/*
 * ToroDB
 * Copyright © 2014 8Kdata Technology (www.8kdata.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.torodb.mongodb.repl.oplogreplier;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.dispatch.ExecutionContexts;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.torodb.common.util.Empty;
import com.torodb.core.Shutdowner;
import com.torodb.core.concurrent.ConcurrentToolsFactory;
import com.torodb.core.concurrent.akka.BatchFlow;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.mongodb.repl.OplogManager;
import com.torodb.mongodb.repl.OplogManager.OplogManagerPersistException;
import com.torodb.mongodb.repl.OplogManager.WriteOplogTransaction;
import com.torodb.mongodb.repl.oplogreplier.batch.AnalyzedOplogBatch;
import com.torodb.mongodb.repl.oplogreplier.batch.AnalyzedOplogBatchExecutor;
import com.torodb.mongodb.repl.oplogreplier.batch.BatchAnalyzer;
import com.torodb.mongodb.repl.oplogreplier.batch.BatchAnalyzer.BatchAnalyzerFactory;
import com.torodb.mongodb.repl.oplogreplier.batch.OplogBatch;
import com.torodb.mongodb.repl.oplogreplier.batch.OplogBatchChecker;
import com.torodb.mongodb.repl.oplogreplier.batch.OplogBatchFilter;
import com.torodb.mongodb.repl.oplogreplier.fetcher.OplogFetcher;
import com.torodb.mongodb.repl.oplogreplier.offheapbuffer.OffHeapBufferConfig;
import com.torodb.mongodb.repl.oplogreplier.offheapbuffer.OffHeapBufferUtils;
import com.torodb.mongowp.commands.oplog.OplogOperation;

import org.apache.logging.log4j.Logger;

import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import javax.inject.Inject;

public class DefaultOplogApplier implements OplogApplier {

  private final Logger logger;
  private final BatchLimits batchLimits;
  private final AnalyzedOplogBatchExecutor batchExecutor;
  private final OplogManager oplogManager;
  private final BatchAnalyzerFactory batchAnalyzerFactory;
  private final ActorSystem actorSystem;
  private final ExecutorService executorService;
  private final OplogApplierMetrics metrics;
  private final OplogBatchFilter batchFilter;
  private final OplogBatchChecker batchChecker;
  private final OffHeapBufferConfig offHeapConfig;

  @Inject
  public DefaultOplogApplier(
      BatchLimits batchLimits,
      OplogManager oplogManager,
      AnalyzedOplogBatchExecutor batchExecutor,
      BatchAnalyzerFactory batchAnalyzerFactory,
      ConcurrentToolsFactory concurrentToolsFactory,
      Shutdowner shutdowner,
      LoggerFactory lf,
      OplogApplierMetrics metrics,
      OplogBatchFilter batchFilter,
      OplogBatchChecker batchChecker,
      OffHeapBufferConfig offHeapConfig) {
    this.logger = lf.apply(this.getClass());
    this.batchExecutor = batchExecutor;
    this.batchLimits = batchLimits;
    this.oplogManager = oplogManager;
    this.batchAnalyzerFactory = batchAnalyzerFactory;
    this.executorService =
        concurrentToolsFactory.createExecutorServiceWithMaxThreads("oplog-applier", 3);
    this.actorSystem =
        ActorSystem.create(
            "oplog-applier", null, null, ExecutionContexts.fromExecutor(executorService));
    this.metrics = metrics;
    this.batchFilter = batchFilter;
    this.batchChecker = batchChecker;
    this.offHeapConfig = offHeapConfig;
    shutdowner.addCloseShutdownListener(this);
  }

  @Override
  public ApplyingJob apply(OplogFetcher fetcher, ApplierContext applierContext) {

    Materializer materializer = ActorMaterializer.create(actorSystem);

    RunnableGraph<Pair<UniqueKillSwitch, CompletionStage<Done>>> graph =
        createOplogSource(fetcher)
            .async()
            .via(createOffheapBuffer(this.offHeapConfig))
            .async()
            .map(batchFilter)
            .map(batchChecker)
            .via(createBatcherFlow(applierContext))
            .viaMat(KillSwitches.single(), Keep.right())
            .async()
            .map(
                analyzedElem -> {
                  for (AnalyzedOplogBatch analyzedOplogBatch : analyzedElem.analyzedBatch) {
                    batchExecutor.apply(analyzedOplogBatch, applierContext);
                  }
                  return analyzedElem;
                })
            .map(this::metricExecution)
            .toMat(
                Sink.foreach(this::storeLastAppliedOp),
                (killSwitch, completionStage) -> new Pair<>(killSwitch, completionStage));

    Pair<UniqueKillSwitch, CompletionStage<Done>> pair = graph.run(materializer);
    UniqueKillSwitch killSwitch = pair.first();

    CompletableFuture<Empty> whenComplete =
        pair.second()
            .toCompletableFuture()
            .thenApply(done -> Empty.getInstance())
            .whenComplete(
                (done, t) -> {
                  fetcher.close();
                  if (done != null) {
                    logger.trace("Oplog replication stream finished normally");
                  } else {
                    Throwable cause;
                    if (t instanceof CompletionException) {
                      cause = t.getCause();
                    } else {
                      cause = t;
                    }
                    //the completable future has been cancelled
                    if (cause instanceof CancellationException) {
                      logger.debug("Oplog replication stream has been cancelled");
                      killSwitch.shutdown();
                    } else { //in this case the exception should came from the stream
                      cause = Throwables.getRootCause(cause);
                      logger.warn(
                          "Oplog replication stream finished exceptionally: "
                              + cause.getLocalizedMessage(),
                          cause);
                      //the stream should be finished exceptionally, but just in case we
                      //notify the kill switch to stop the stream.
                      killSwitch.shutdown();
                    }
                  }
                });

    return new DefaultApplyingJob(killSwitch, whenComplete);
  }

  private Flow<OplogBatch, OplogBatch, NotUsed> createOffheapBuffer(
      OffHeapBufferConfig offHeapConfig) {
    return OffHeapBufferUtils.createOffheapBuffer(offHeapConfig);
  }

  @Override
  public void close() throws Exception {
    logger.trace("Waiting until actor system terminates");
    Await.result(actorSystem.terminate(), Duration.Inf());
    logger.trace("Actor system terminated");
    executorService.shutdown();
  }

  private Source<OplogBatch, NotUsed> createOplogSource(OplogFetcher fetcher) {
    return Source.unfold(
        fetcher,
        f -> {
          OplogBatch batch = f.fetch();
          if (batch.isLastOne()) {
            return Optional.empty();
          }
          return Optional.of(new Pair<>(f, batch));
        });
  }

  /**
   * Creates a flow that batches and analyze a input of {@link AnalyzedOplogBatch remote jobs}.
   *
   * <p>This flow tries to accummulate several remote jobs into a bigger one and does not emit
   * until:
   *
   * <ul>
   *   <li>A maximum number of operations are batched
   *   <li>Or a maximum time has happen since the last emit
   *   <li>Or the recived job is not {@link OplogBatch#isReadyForMore()}
   * </ul>
   */
  private Flow<OplogBatch, AnalyzedStreamElement, NotUsed> createBatcherFlow(
      ApplierContext context) {
    Predicate<OplogBatch> finishBatchPredicate =
        (OplogBatch rawBatch) -> !rawBatch.isReadyForMore();
    ToIntFunction<OplogBatch> costFunction = (rawBatch) -> rawBatch.count();

    Supplier<RawStreamElement> zeroFun = () -> RawStreamElement.INITIAL_ELEMENT;
    BiFunction<RawStreamElement, OplogBatch, RawStreamElement> acumFun =
        (streamElem, newBatch) -> streamElem.concat(newBatch);

    BatchAnalyzer batchAnalyzer = batchAnalyzerFactory.createBatchAnalyzer(context);
    return Flow.of(OplogBatch.class)
        .via(
            new BatchFlow<>(
                batchLimits.maxSize,
                batchLimits.maxPeriod,
                finishBatchPredicate,
                costFunction,
                zeroFun,
                acumFun))
        .filter(rawElem -> rawElem.rawBatch != null && !rawElem.rawBatch.isEmpty())
        .map(
            rawElem -> {
              List<OplogOperation> rawOps = rawElem.rawBatch.getOps();
              List<AnalyzedOplogBatch> analyzed = batchAnalyzer.apply(rawOps);
              return new AnalyzedStreamElement(rawElem, analyzed);
            });
  }

  private AnalyzedStreamElement storeLastAppliedOp(AnalyzedStreamElement streamElement)
      throws OplogManagerPersistException {
    assert !streamElement.rawBatch.isEmpty();
    OplogOperation lastOp = streamElement.rawBatch.getLastOperation();
    try (WriteOplogTransaction writeTrans = oplogManager.createWriteTransaction()) {
      writeTrans.forceNewValue(lastOp.getHash(), lastOp.getOpTime());
    }
    return streamElement;
  }

  private AnalyzedStreamElement metricExecution(AnalyzedStreamElement streamElement) {
    long timestamp = System.currentTimeMillis();
    long batchExecutionMillis = timestamp - streamElement.startFetchTimestamp;

    int rawBatchSize = streamElement.rawBatch.count();
    metrics.getBatchSize().update(rawBatchSize);
    metrics.getApplied().mark(rawBatchSize);

    metricOpsExecutionDelay(rawBatchSize, batchExecutionMillis);

    return streamElement;
  }

  private void metricOpsExecutionDelay(int rawBatchSize, long batchExecutionMillis) {
    if (rawBatchSize < 1) {
      return;
    }
    if (batchExecutionMillis <= 0) {
      logger.debug("Unexpected time execution: {}" + batchExecutionMillis);
    }
    metrics.getMaxDelay().update(batchExecutionMillis);
    metrics.getApplicationCost().update((1000L * batchExecutionMillis) / rawBatchSize);
  }

  private static class DefaultApplyingJob extends AbstractApplyingJob {

    private final KillSwitch killSwitch;

    public DefaultApplyingJob(KillSwitch killSwitch, CompletableFuture<Empty> onFinish) {
      super(onFinish);
      this.killSwitch = killSwitch;
    }

    @Override
    public void cancel() {
      killSwitch.shutdown();
    }
  }

  public static class BatchLimits {

    private final int maxSize;
    private final FiniteDuration maxPeriod;

    public BatchLimits(int maxSize, java.time.Duration maxPeriod) {
      this.maxSize = maxSize;
      this.maxPeriod = new FiniteDuration(maxPeriod.toMillis(), TimeUnit.MILLISECONDS);
    }

    public int getMaxSize() {
      return maxSize;
    }

    public FiniteDuration getMaxPeriod() {
      return maxPeriod;
    }
  }

  private static class RawStreamElement {

    private static final RawStreamElement INITIAL_ELEMENT = new RawStreamElement(null, 0);
    private final OplogBatch rawBatch;
    private final long startFetchTimestamp;

    public RawStreamElement(OplogBatch rawBatch, long startFetchTimestamp) {
      this.rawBatch = rawBatch;
      this.startFetchTimestamp = startFetchTimestamp;
    }

    private RawStreamElement concat(OplogBatch newBatch) {
      OplogBatch newRawBatch;
      long newStartFetchTimestamp;
      if (this == INITIAL_ELEMENT) {
        newRawBatch = newBatch;
        newStartFetchTimestamp = System.currentTimeMillis();
      } else {
        newRawBatch = rawBatch.concat(newBatch);
        newStartFetchTimestamp = startFetchTimestamp;
      }
      return new RawStreamElement(newRawBatch, newStartFetchTimestamp);
    }
  }

  private static class AnalyzedStreamElement {

    private final OplogBatch rawBatch;
    private final long startFetchTimestamp;
    private final List<AnalyzedOplogBatch> analyzedBatch;

    AnalyzedStreamElement(
        RawStreamElement rawStreamElement, List<AnalyzedOplogBatch> analyzedBatches) {
      this.rawBatch = rawStreamElement.rawBatch;
      this.startFetchTimestamp = rawStreamElement.startFetchTimestamp;
      this.analyzedBatch = analyzedBatches;
    }
  }
}
