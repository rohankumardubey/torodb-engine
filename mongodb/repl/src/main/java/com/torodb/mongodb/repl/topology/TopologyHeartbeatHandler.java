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

package com.torodb.mongodb.repl.topology;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;
import com.torodb.common.util.CompletionExceptions;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.core.services.IdleTorodbService;
import com.torodb.mongodb.commands.pojos.ReplicaSetConfig;
import com.torodb.mongodb.commands.signatures.internal.ReplSetHeartbeatCommand.ReplSetHeartbeatArgument;
import com.torodb.mongodb.commands.signatures.internal.ReplSetHeartbeatReply;
import com.torodb.mongodb.repl.guice.ReplSetName;
import com.torodb.mongowp.ErrorCode;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.client.core.MongoConnection.ErroneousRemoteCommandResponse;
import com.torodb.mongowp.client.core.MongoConnection.FromExceptionRemoteCommandRequest;
import com.torodb.mongowp.client.core.MongoConnection.RemoteCommandResponse;
import com.torodb.mongowp.client.core.UnreachableMongoServerException;
import com.torodb.mongowp.commands.MongoRuntimeException;
import com.torodb.mongowp.commands.tools.Empty;
import com.torodb.mongowp.exceptions.InconsistentReplicaSetNamesException;
import com.torodb.mongowp.exceptions.MongoException;
import org.apache.logging.log4j.Logger;
import org.jooq.lambda.UncheckedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TopologyHeartbeatHandler extends IdleTorodbService {

  private final Logger logger;

  private final ImmutableList<HostAndPort> seeds;
  private final Clock clock;
  private final String replSetName;
  private final HeartbeatNetworkHandler networkHandler;
  private final TopologyExecutor executor;
  private final TopologyErrorHandler errorHandler;
  private final VersionChangeListener versionChangeListener;
  @GuardedBy("executor")
  private boolean stopped;

  @Inject
  public TopologyHeartbeatHandler(Clock clock, @ReplSetName String replSetName,
      LoggerFactory loggerFactory, HeartbeatNetworkHandler heartbeatSender,
      TopologyExecutor executor, TopologyErrorHandler errorHandler, ThreadFactory threadFactory,
      @RemoteSeed ImmutableList<HostAndPort> seeds) {
    super(threadFactory);
    this.logger = loggerFactory.apply(this.getClass());
    this.clock = clock;
    this.replSetName = replSetName;
    this.networkHandler = heartbeatSender;
    this.executor = executor;
    this.errorHandler = errorHandler;
    this.versionChangeListener = this::scheduleHeartbeats;
    this.seeds = seeds;
  }

  @Override
  protected final String serviceName() {
    return "Heartbeat handler";
  }

  @Override
  protected void startUp() throws Exception {
    logger.debug("Starting up {}", serviceName());

    Iterator<HostAndPort> seedIterator = Iterables.cycle(seeds).iterator();
    boolean finished = false;
    while (!finished) {
      finished = start(seedIterator.next())
          .handle(this::checkHeartbeatStarted)
          .join();
      if (!finished) {
        logger.debug("Retrying to start heartbeats in 1 second");
        Thread.sleep(1000);
      }
    }

    logger.debug("{} has been started up", serviceName());
  }

  @Override
  protected void shutDown() throws Exception {
    logger.debug("Shutting down {}", serviceName());
    executor.onAnyVersion()
        .consumeAsync(coord -> stopped = true)
        .join();
    logger.debug("{} has been shutted down", serviceName());
  }

  @GuardedBy("any")
  private boolean checkHeartbeatStarted(Status<?> status, Throwable t) {
    if (t == null) {
      if (status.isOk()) {
        logger.trace("Heartbeat started correctly");
        return true;
      } else {
        logger.debug("Heartbeat start failed: {}", status);
        switch (status.getErrorCode()) {
          case NO_REPLICATION_ENABLED:
            logger.warn("The sync source {} is not running with "
                + "replication enabled", seeds);
            break;
          case INCONSISTENT_REPLICA_SET_NAMES:
          default:
            logger.warn(status.getErrorMsg());
            break;
        }
        return false;
      }
    } else {
      Throwable usefulThrowable = CompletionExceptions
          .getFirstNonCompletionException(t);

      if (usefulThrowable instanceof UncheckedException) {
        usefulThrowable = usefulThrowable.getCause() != null 
            ? usefulThrowable.getCause() : usefulThrowable;
      }

      logger.warn("Heartbeat start failed (sync source: " + seeds + "): " + usefulThrowable
          .getLocalizedMessage(),
          usefulThrowable);
      return false;
    }
  }

  CompletableFuture<Status<ReplicaSetConfig>> start(HostAndPort seed) {
    executor.addVersionChangeListener(versionChangeListener);
    return executor.onCurrentVersion().andThenApplyAsync(
        networkHandler.askForConfig(
            new RemoteCommandRequest<>(seed, "admin", Empty.getInstance())
        ),
        (coord, remoteConfig) -> {
          Status<ReplicaSetConfig> result = remoteConfig.asStatus();
          if (!result.isOk()) {
            return result;
          }
          ReplicaSetConfig replConfig = result.getResult();
          try {
            checkRemoteReplSetConfig(replConfig);
            updateConfig(coord, replConfig);
            return result;
          } catch (InconsistentReplicaSetNamesException ex) {
            return Status.from(ex);
          }
        }
    );
  }

  private void checkRemoteReplSetConfig(ReplicaSetConfig remoteConfig)
      throws InconsistentReplicaSetNamesException {
    //TODO(gortiz): DRY. Implement a better way to do that once the config
    //is validated
    String remoteReplSetName = remoteConfig.getReplSetName();
    if (!replSetName.equals(remoteReplSetName)) {
      throw new InconsistentReplicaSetNamesException(
          "The remote replica set configuration is named as '"
          + remoteReplSetName + "', which differs with the local "
          + "replica set name '" + replSetName + "'");
    }
  }

  @GuardedBy("executor")
  private void scheduleHeartbeats(TopologyCoordinator coord, ReplicaSetConfig oldConf) {
    logger.debug("Scheduling new heartbeats to nodes on config {}",
        coord.getRsConfig().getConfigVersion());
    coord.getRsConfig().getMembers().stream()
        .forEach(member -> scheduleHeartbeatToTarget(
            member.getHostAndPort(),
            Duration.ZERO
        ));
  }

  @GuardedBy("any")
  private CompletableFuture<?> scheduleHeartbeatToTarget(final HostAndPort target, Duration delay) {
    logger.trace("Scheduling heartbeat to {} in {}", target, delay);

    return executor.onCurrentVersion()
        .scheduleOnce((coord) -> doHeartbeat(coord, target), delay);
  }

  @GuardedBy("executor")
  private void doHeartbeat(final TopologyCoordinator coord, final HostAndPort target) {
    if (stopped) {
      logger.trace("Ignoring heartbeat to {} because the handler has "
          + "been stopped", target);
      return;
    }

    Instant start = clock.instant();
    RemoteCommandRequest<ReplSetHeartbeatArgument> request = coord
        .prepareHeartbeatRequest(start, replSetName, target);

    CompletableFuture<RemoteCommandResponse<ReplSetHeartbeatReply>> hbHandle =
        networkHandler.sendHeartbeat(request)
            .exceptionally(t -> onNetworkError(t, target, start));

    executor.onCurrentVersion()
        .andThenAcceptAsync(
            hbHandle,
            (coord2, response) -> handleHeartbeatResponse(
                coord2, target, request.getCmdObj(), response));
  }

  /**
   * Called when a heartbeat request fails on the network handler.
   *
   * It is important to not call this method more than once per request, otherwise more than one
   * request can be scheduled to the target.
   *
   * @param t
   * @param target
   */
  @GuardedBy("any")
  private RemoteCommandResponse<ReplSetHeartbeatReply> onNetworkError(
      Throwable t, HostAndPort target, Instant start) {
    Throwable cause = CompletionExceptions.getFirstNonCompletionException(t);
    while (cause.getCause() != cause && cause instanceof UncheckedException) {
      cause = cause.getCause();
    }
    if (cause instanceof CancellationException) {
      logger.trace("Heartbeat handling to {} has been cancelled "
          + "before execution: {}", target, cause.getMessage());
      throw (CancellationException) cause;
    } else {
      logger.debug("Error while on the heartbeat request sent to "
          + target, t);
      if (errorHandler.reciveHeartbeatError(cause)) {
        RemoteCommandResponse<ReplSetHeartbeatReply> response =
            handleHeartbeatError(cause, start);
        logger.trace("Handled with a response with error {}",
            response.getErrorCode());
        return response;
      } else {
        String msg = "Aborting execution as requested by the topology "
            + "supervisor";
        logger.trace(msg);
        stopAsync();
        throw new CancellationException(msg);
      }
    }
  }

  @Nonnull
  private RemoteCommandResponse<ReplSetHeartbeatReply> handleHeartbeatError(
      Throwable t, Instant start) {
    Duration d = Duration.between(clock.instant(), start);
    ErrorCode errorCode;
    if (t instanceof MongoException) {
      return new FromExceptionRemoteCommandRequest((MongoException) t, d);
    } else if (t instanceof UnreachableMongoServerException) {
      errorCode = ErrorCode.HOST_UNREACHABLE;
    } else {
      if (!(t instanceof MongoRuntimeException)
          && !(t instanceof UnreachableMongoServerException)) {
        logger.warn("Unexpected exception {} catched by the topology "
            + "heartbeat handler", t.getClass().getSimpleName());
      }
      errorCode = ErrorCode.UNKNOWN_ERROR;
    }
    return new ErroneousRemoteCommandResponse<>(
        errorCode,
        t.getLocalizedMessage(),
        d
    );
  }

  @GuardedBy("executor")
  private void handleHeartbeatResponse(TopologyCoordinator coord,
      HostAndPort target, ReplSetHeartbeatArgument request,
      RemoteCommandResponse<ReplSetHeartbeatReply> response) {
    boolean isUnauthorized = (response.getErrorCode() == ErrorCode.UNAUTHORIZED) || (response
        .getErrorCode() == ErrorCode.AUTHENTICATION_FAILED);
    Instant now = clock.instant();
    Duration networkTime = Duration.ZERO;

    if (response.isOk()) {
      networkTime = response.getNetworkTime();
    } else {
      logger.warn("Error in heartbeat request to {}; {}", target, response.asStatus());
      if (response.getBson() != null) {
        logger.debug("heartbeat response: ", response.getBson());
      }

      if (isUnauthorized) {
        networkTime = response.getNetworkTime();
      }
    }

    HeartbeatResponseAction action = coord.processHeartbeatResponse(now,
        networkTime, target, response);

    ReplSetHeartbeatReply hbReply = response.getCommandReply().orElse(null);
    assert hbReply != null || !response.isOk() :
        "Recived a null hbReply when the request didn't fail";

    scheduleHeartbeatToTarget(target, action.getNextHeartbeatDelay());

    handleHeartbeatResponseAction(coord, action, hbReply, response.getErrorCode());

  }

  @GuardedBy("executor")
  private void handleHeartbeatResponseAction(TopologyCoordinator coord,
      HeartbeatResponseAction action,
      @Nullable ReplSetHeartbeatReply reply, ErrorCode responseStatus)
      throws UnsupportedHeartbeatResponseActionException {
    switch (action.getAction()) {
      case NO_ACTION:
        break;
      case RECONFIG:
        assert reply != null;
        assert reply.getConfig().isPresent();
        updateConfig(coord, reply.getConfig().get());
        break;
      case START_ELECTION:
      case STEP_DOWN_SELF:
      case STEP_DOWN_REMOTE_PRIMARY:
        throw new UnsupportedHeartbeatResponseActionException(action, reply);
      default:
        logger.error("Illegal heartbeat response action code {}", action.getAction());
        throw new AssertionError();
    }
  }

  @GuardedBy("executor")
  private void updateConfig(TopologyCoordinator coord, ReplicaSetConfig config) {
    validateConfig(coord, config);
    coord.updateConfig(config, clock.instant());
  }

  @GuardedBy("executor")
  private void validateConfig(TopologyCoordinator coord, ReplicaSetConfig config) {
    logger.debug("Accepting the new replica set config (version is {}) without validating it first "
        + "(not supported yet)",
        config.getConfigVersion());
  }

  private static class UnsupportedHeartbeatResponseActionException extends RuntimeException {

    private static final long serialVersionUID = 8879568483145061898L;
    private final HeartbeatResponseAction action;
    @Nullable
    private final transient ReplSetHeartbeatReply reply;

    public UnsupportedHeartbeatResponseActionException(HeartbeatResponseAction action,
        ReplSetHeartbeatReply reply) {
      super("Heartbeat action " + action.getAction() + " is not supported");
      this.action = action;
      this.reply = reply;
    }

    public HeartbeatResponseAction getAction() {
      return action;
    }

    @Nullable
    public ReplSetHeartbeatReply getReply() {
      return reply;
    }
  }
}
