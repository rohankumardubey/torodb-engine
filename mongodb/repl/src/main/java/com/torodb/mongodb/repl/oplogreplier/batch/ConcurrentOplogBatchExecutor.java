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

package com.torodb.mongodb.repl.oplogreplier.batch;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.google.common.base.Supplier;
import com.torodb.common.util.Empty;
import com.torodb.core.concurrent.ConcurrentToolsFactory;
import com.torodb.core.concurrent.StreamExecutor;
import com.torodb.core.exceptions.user.UserException;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.core.metrics.ToroMetricRegistry;
import com.torodb.core.retrier.Retrier;
import com.torodb.core.transaction.RollbackException;
import com.torodb.mongodb.core.MongodServer;
import com.torodb.mongodb.repl.oplogreplier.ApplierContext;
import com.torodb.mongodb.repl.oplogreplier.OplogOperationApplier;
import com.torodb.mongodb.repl.oplogreplier.analyzed.AnalyzedOp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnegative;
import javax.inject.Inject;

public class ConcurrentOplogBatchExecutor extends SimpleAnalyzedOplogBatchExecutor {

  private final StreamExecutor streamExecutor;
  private final ConcurrentOplogBatchExecutorMetrics concurrentMetrics;
  private final SubBatchHeuristic subBatchHeuristic;

  @Inject
  public ConcurrentOplogBatchExecutor(OplogOperationApplier oplogOperationApplier,
      MongodServer server, Retrier retrier, ConcurrentToolsFactory concurrentToolsFactory,
      NamespaceJobExecutor namespaceJobExecutor, LoggerFactory lf,
      ConcurrentOplogBatchExecutorMetrics concurrentMetrics, SubBatchHeuristic subBatchHeuristic) {
    super(concurrentMetrics, oplogOperationApplier, server, retrier, namespaceJobExecutor);
    this.streamExecutor = concurrentToolsFactory.createStreamExecutor(
        lf.apply(this.getClass()), "concurrent-oplog-batch-executor", true);
    this.concurrentMetrics = concurrentMetrics;
    this.subBatchHeuristic = subBatchHeuristic;
  }

  @Override
  protected void doStart() {
    streamExecutor.startAsync();
    streamExecutor.awaitRunning();

    super.doStart();
  }

  @Override
  protected void doStop() {
    streamExecutor.stopAsync();
    streamExecutor.awaitTerminated();

    super.doStop();
  }

  @Override
  public void execute(CudAnalyzedOplogBatch cudBatch, ApplierContext context) throws UserException {
    assert isRunning() : "The service is on state " + state() + " instead of RUNNING";
    List<NamespaceJob> namespaceJobList = cudBatch.streamNamespaceJobs()
        .flatMap(this::split)
        .collect(Collectors.toList());
    concurrentMetrics.getSubBatchSizeMeter().mark(namespaceJobList.size());
    concurrentMetrics.getSubBatchSizeHistogram().update(namespaceJobList.size());

    Stream<Callable<Empty>> callables = namespaceJobList.stream()
        .map(namespaceJob -> toCallable(namespaceJob, context));
    try {
      streamExecutor.execute(callables)
          .join();
    } catch (CompletionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof UserException) {
        throw (UserException) cause;
      } else if (cause instanceof RollbackException) {
        throw (RollbackException) cause;
      }
      throw ex;
    }
  }

  private Callable<Empty> toCallable(NamespaceJob namespaceJob, ApplierContext context) {
    return () -> {
      execute(namespaceJob, context);
      return Empty.getInstance();
    };
  }

  private Stream<NamespaceJob> split(NamespaceJob namespaceJob) {
    Collection<AnalyzedOp> jobs = namespaceJob.getJobs();

    int subBatchSize = subBatchHeuristic.getSubBatchSize(concurrentMetrics);

    assert subBatchSize > 0 : "Sub batch size must be positive";

    Supplier<List<AnalyzedOp>> currentListFactory = () -> new ArrayList<>(subBatchSize);

    List<NamespaceJob> result = new ArrayList<>(1 + jobs.size() / subBatchSize);
    List<AnalyzedOp> currentList = null;

    for (AnalyzedOp job : jobs) {
      if (currentList == null) {
        currentList = currentListFactory.get();
      }

      currentList.add(job);
      if (currentList.size() >= subBatchSize) {
        result.add(new NamespaceJob(namespaceJob.getDatabase(), namespaceJob.getCollection(),
            currentList));
        currentList = currentListFactory.get();
      }
      assert currentList.size() <= subBatchSize : "Created a subatch whose size is "
          + currentList.size() + " but heuristic says max subatch size is " + subBatchSize;
    }
    if (currentList != null) {
      result.add(new NamespaceJob(namespaceJob.getDatabase(), namespaceJob.getCollection(),
          currentList));
    }

    return result.stream();
  }

  public static class ConcurrentOplogBatchExecutorMetrics
      extends AnalyzedOplogBatchExecutorMetrics {

    private final Meter subBatchSizeMeter;
    private final Histogram subBatchSizeHistogram;

    @Inject
    public ConcurrentOplogBatchExecutorMetrics(ToroMetricRegistry parentRegistry) {
      super(parentRegistry);
      this.subBatchSizeMeter = getRegistry().meter("subBatchSizeMeter");
      this.subBatchSizeHistogram = getRegistry().histogram("subBatchSizeHistogram");
    }

    public Meter getSubBatchSizeMeter() {
      return subBatchSizeMeter;
    }

    public Histogram getSubBatchSizeHistogram() {
      return subBatchSizeHistogram;
    }
  }

  public static interface SubBatchHeuristic {

    /**
     * Given some metrics, this heuristic returns number of {@link AnalyzedOp ops} that each sub
     * batch should have.
     *
     * @return a positive integer
     */
    @Nonnegative
    public int getSubBatchSize(ConcurrentOplogBatchExecutorMetrics metrics);
  }

}
