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

package com.torodb.mongodb.repl;

import com.google.common.base.Supplier;
import com.google.common.net.HostAndPort;
import com.google.inject.assistedinject.Assisted;
import com.torodb.core.annotations.TorodbRunnableService;
import com.torodb.core.exceptions.user.UserException;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.core.services.RunnableTorodbService;
import com.torodb.core.supervision.Supervisor;
import com.torodb.core.supervision.SupervisorDecision;
import com.torodb.core.transaction.RollbackException;
import com.torodb.mongodb.commands.signatures.diagnostic.ListDatabasesCommand;
import com.torodb.mongodb.commands.signatures.diagnostic.ListDatabasesCommand.ListDatabasesReply;
import com.torodb.mongodb.core.MongodSchemaExecutor;
import com.torodb.mongodb.core.MongodServer;
import com.torodb.mongodb.filters.IndexFilter;
import com.torodb.mongodb.filters.NamespaceFilter;
import com.torodb.mongodb.language.Namespace;
import com.torodb.mongodb.repl.OplogManager.OplogManagerPersistException;
import com.torodb.mongodb.repl.OplogManager.ReadOplogTransaction;
import com.torodb.mongodb.repl.OplogManager.WriteOplogTransaction;
import com.torodb.mongodb.repl.exceptions.NoSyncSourceFoundException;
import com.torodb.mongodb.repl.guice.MongoDbRepl;
import com.torodb.mongodb.repl.oplogreplier.ApplierContext;
import com.torodb.mongodb.repl.oplogreplier.OplogApplier;
import com.torodb.mongodb.repl.oplogreplier.OplogApplier.UnexpectedOplogApplierException;
import com.torodb.mongodb.repl.oplogreplier.RollbackReplicationException;
import com.torodb.mongodb.repl.oplogreplier.StopReplicationException;
import com.torodb.mongodb.repl.oplogreplier.fetcher.LimitedOplogFetcher;
import com.torodb.mongodb.repl.oplogreplier.fetcher.OplogFetcher;
import com.torodb.mongodb.utils.DbCloner;
import com.torodb.mongodb.utils.DbCloner.CloneOptions;
import com.torodb.mongodb.utils.DbCloner.CloningException;
import com.torodb.mongowp.OpTime;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.client.core.MongoClient;
import com.torodb.mongowp.client.core.MongoClientFactory;
import com.torodb.mongowp.client.core.MongoConnection;
import com.torodb.mongowp.client.core.MongoConnection.RemoteCommandResponse;
import com.torodb.mongowp.client.core.UnreachableMongoServerException;
import com.torodb.mongowp.commands.oplog.OplogOperation;
import com.torodb.mongowp.commands.pojos.MongoCursor;
import com.torodb.mongowp.commands.tools.Empty;
import com.torodb.mongowp.exceptions.MongoException;
import com.torodb.mongowp.exceptions.OplogOperationUnsupported;
import com.torodb.mongowp.exceptions.OplogStartMissingException;
import com.torodb.torod.SchemaOperationExecutor;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.inject.Inject;


public class RecoveryService extends RunnableTorodbService {

  private static final int MAX_ATTEMPTS = 10;
  private final Logger logger;
  private final Callback callback;
  private final OplogManager oplogManager;
  private final SyncSourceProvider syncSourceProvider;
  private final OplogReaderProvider oplogReaderProvider;
  private final DbCloner cloner;
  private final MongoClientFactory remoteClientFactory;
  private final MongodServer server;
  private final OplogApplier oplogApplier;
  private final NamespaceFilter namespaceFilter;
  private final IndexFilter indexFilter;

  @Inject
  public RecoveryService(
      @TorodbRunnableService ThreadFactory threadFactory,
      @Assisted Callback callback,
      OplogManager oplogManager,
      SyncSourceProvider syncSourceProvider,
      OplogReaderProvider oplogReaderProvider,
      @MongoDbRepl DbCloner cloner,
      MongoClientFactory remoteClientFactory,
      MongodServer server,
      OplogApplier oplogApplier,
      NamespaceFilter namespaceFilter,
      IndexFilter indexFilter,
      LoggerFactory lf) {
    super(callback, threadFactory);
    this.logger = lf.apply(this.getClass());
    this.callback = callback;
    this.oplogManager = oplogManager;
    this.syncSourceProvider = syncSourceProvider;
    this.oplogReaderProvider = oplogReaderProvider;
    this.cloner = cloner;
    this.remoteClientFactory = remoteClientFactory;
    this.server = server;
    this.oplogApplier = oplogApplier;
    this.namespaceFilter = namespaceFilter;
    this.indexFilter = indexFilter;
  }

  @Override
  protected void startUp() {
    logger.info("Starting RECOVERY service");
  }

  @Override
  protected Logger getLogger() {
    return logger;
  }

  @Override
  protected void runProtected() throws Exception {
    callback.waitUntilStartPermision();
    try {
      int attempt = 0;
      boolean finished = false;

      while (!finished && attempt < MAX_ATTEMPTS && isRunning()) {
        attempt++;
        if (attempt > 1) {
          long millisToSleep = getMillisToSleep(attempt);
          logger.debug("Waiting {} millis after the {}th attempt", millisToSleep, attempt - 1);
          Thread.sleep(millisToSleep);
        }
        try {
          finished = initialSync();
        } catch (TryAgainException ex) {
          logger.warn("Error while trying to recover (attempt: "
              + attempt + ")", ex);
        } catch (FatalErrorException ex) {
          logger.error("Fatal error while trying to recover", ex);
        }
      }

      if (!finished) {
        callback.recoveryFailed(this);
      } else {
        callback.recoveryFinished(this);
      }
    } catch (Throwable ex) {
      callback.recoveryFailed(this, ex);
    }
  }

  private boolean initialSync() throws TryAgainException, FatalErrorException {
    /*
     * 1. store that data is inconsistent
     * 2. decide a sync source
     * 3. lastRemoteOptime1 = get the last optime of the sync source
     * 4. clone all databases except local
     * 5. lastRemoteOptime2 = get the last optime of the sync source
     * 6. apply remote oplog from lastRemoteOptime1 to lastRemoteOptime2
     * 7. lastRemoteOptime3 = get the last optime of the sync source
     * 8. apply remote oplog from lastRemoteOptime2 to lastRemoteOptime3
     * 9. rebuild indexes
     * 10. store lastRemoteOptime3 as the last applied operation optime
     * 11. store that data is consistent
     * 12. change replication state to SECONDARY
     */

    //TODO: Support fastsync (used to restore a node by copying the data from other up-to-date node)
    logger.info("Starting initial sync");

    callback.setConsistentState(false);

    HostAndPort syncSource;
    try {
      syncSource = syncSourceProvider.newSyncSource();
      logger.info("Using node " + syncSource + " to replicate from");
    } catch (NoSyncSourceFoundException ex) {
      throw new TryAgainException("No sync source");
    }

    MongoClient remoteClient;
    try {
      remoteClient = remoteClientFactory.createClient(syncSource);
    } catch (UnreachableMongoServerException ex) {
      throw new TryAgainException(ex);
    }
    try {
      logger.debug("Remote client obtained");

      MongoConnection remoteConnection = remoteClient.openConnection();

      try (OplogReader reader = oplogReaderProvider.newReader(remoteConnection)) {

        OplogOperation lastClonedOp = reader.getLastOp();
        final OpTime lastRemoteOptime1 = lastClonedOp.getOpTime();

        try (WriteOplogTransaction oplogTransaction = oplogManager.createWriteTransaction()) {
          logger.info("Remote database cloning started");
          oplogTransaction.truncate();
          logger.info("Local databases dropping started");
          Status<?> status = dropDatabases();
          if (!status.isOk()) {
            throw new TryAgainException("Error while trying to drop collections: "
                + status);
          }
          logger.info("Local databases dropping finished");
          if (!isRunning()) {
            logger.warn("Recovery stopped before it can finish");
            return false;
          }
          logger.info("Remote database cloning started");
          cloneDatabases(remoteClient);
          logger.info("Remote database cloning finished");

          oplogTransaction.forceNewValue(lastClonedOp.getHash(), lastClonedOp.getOpTime());
        }

        if (!isRunning()) {
          logger.warn("Recovery stopped before it can finish");
          return false;
        }

        OpTime lastRemoteOptime2 = reader.getLastOp().getOpTime();
        logger.info("First oplog application started");
        applyOplog(reader, lastRemoteOptime1, lastRemoteOptime2);
        logger.info("First oplog application finished");

        if (!isRunning()) {
          logger.warn("Recovery stopped before it can finish");
          return false;
        }

        OplogOperation lastOperation = reader.getLastOp();
        OpTime lastRemoteOptime3 = lastOperation.getOpTime();
        logger.info("Second oplog application started");
        applyOplog(reader, lastRemoteOptime2, lastRemoteOptime3);
        logger.info("Second oplog application finished");

        if (!isRunning()) {
          logger.warn("Recovery stopped before it can finish");
          return false;
        }

        logger.info("Index rebuild started");
        rebuildIndexes();
        logger.info("Index rebuild finished");
        if (!isRunning()) {
          logger.warn("Recovery stopped before it can finish");
          return false;
        }
      } catch (OplogStartMissingException ex) {
        throw new TryAgainException(ex);
      } catch (OplogOperationUnsupported ex) {
        throw new TryAgainException(ex);
      } catch (MongoException | RollbackException ex) {
        throw new TryAgainException(ex);
      } catch (OplogManagerPersistException ex) {
        throw new FatalErrorException();
      } catch (UserException ex) {
        throw new FatalErrorException(ex);
      }

      callback.setConsistentState(true);

      logger.info("Initial sync finished");
    } finally {
      remoteClient.close();
    }
    return true;
  }

  @Override
  protected void shutDown() {
    logger.info("Recived a request to stop the recovering service");
  }

  private Status<?> dropDatabases() throws TryAgainException {
    try (MongodSchemaExecutor schemaExecutor = server.openSchemaExecutor()) {
      SchemaOperationExecutor docSchemaEx = schemaExecutor.getDocSchemaExecutor();
      docSchemaEx.streamDbNames()
          .forEach(docSchemaEx::dropDatabase);
    } catch (TimeoutException ex) {
      throw new TryAgainException(ex);
    }
    return Status.ok();
  }

  private void cloneDatabases(@Nonnull MongoClient remoteClient) throws CloningException,
      MongoException, UserException {

    streamRemoteDatabases(remoteClient)
        .map(ListDatabasesReply.DatabaseEntry::getName)
        .filter(this::isReplicable)
        .forEach(databaseName -> cloneDatabase(databaseName, remoteClient));
  }

  private Stream<ListDatabasesReply.DatabaseEntry> streamRemoteDatabases(MongoClient remoteClient)
      throws MongoException {
    try (MongoConnection remoteConnection = remoteClient.openConnection()) {
      RemoteCommandResponse<ListDatabasesReply> remoteResponse = remoteConnection.execute(
          ListDatabasesCommand.INSTANCE,
          "admin",
          true,
          Empty.getInstance()
      );
      if (!remoteResponse.isOk()) {
        throw remoteResponse.asMongoException();
      }

      return remoteResponse.getCommandReply().get()
          .getDatabases()
          .stream();
    }
  }

  private void cloneDatabase(String databaseName, MongoClient remoteClient) {
    MyWritePermissionSupplier writePermissionSupplier =
        new MyWritePermissionSupplier(databaseName);

    CloneOptions options = new CloneOptions(
        true,
        true,
        true,
        false,
        databaseName,
        Collections.<String>emptySet(),
        writePermissionSupplier,
        (colName) -> namespaceFilter.filter(new Namespace(databaseName, colName)),
        indexFilter
    );

    try {
      cloner.cloneDatabase(databaseName, remoteClient, server, options);
    } catch (MongoException ex) {
      throw new CloningException(ex);
    }
  }

  /**
   * Applies all the oplog operations stored on the remote server whose optime is higher than
   * <em>from</em> but lower or equal than <em>to</em>.
   */
  private void applyOplog(
      OplogReader remoteOplog,
      OpTime from,
      OpTime to) throws TryAgainException, MongoException, FatalErrorException {

    MongoCursor<OplogOperation> oplogCursor = remoteOplog.between(from, true, to, true);

    if (!oplogCursor.hasNext()) {
      throw new OplogStartMissingException(remoteOplog.getSyncSource());
    }
    OplogOperation firstOp = oplogCursor.next();
    if (!firstOp.getOpTime().equals(from)) {
      throw new TryAgainException("Remote oplog does not cointain our last operation");
    }

    OplogFetcher fetcher = new LimitedOplogFetcher(oplogCursor);

    ApplierContext context = new ApplierContext.Builder()
        .setReapplying(true)
        .setUpdatesAsUpserts(true)
        .build();

    try {
      oplogApplier.apply(fetcher, context)
          .waitUntilFinished();
    } catch (StopReplicationException | RollbackReplicationException | CancellationException
        | UnexpectedOplogApplierException ex) {
      throw new FatalErrorException(ex);
    }

    OpTime lastAppliedOptime;
    try (ReadOplogTransaction oplogTrans = oplogManager.createReadTransaction()) {
      lastAppliedOptime = oplogTrans.getLastAppliedOptime();
    }
    if (!lastAppliedOptime.equals(to)) {
      logger.debug("Unexpected optime for last operation to apply. "
          + "Expected " + to + ", but " + lastAppliedOptime
          + " found");
    }
  }

  private void rebuildIndexes() {
    //TODO: Check if this is necessary
    logger.debug("Rebuild index is not implemented yet, so indexes have not been rebuild");
  }

  private boolean isReplicable(String databaseName) {
    return !databaseName.equals("local");
  }

  private long getMillisToSleep(int attempt) {
    return attempt * 1000L;
  }

  private class MyWritePermissionSupplier implements Supplier<Boolean> {

    private final String database;

    public MyWritePermissionSupplier(String database) {
      this.database = database;
    }

    @Override
    public Boolean get() {
      return callback.canAcceptWrites(database);
    }
  }

  private static class TryAgainException extends Exception {

    private static final long serialVersionUID = 1L;

    public TryAgainException() {
    }

    public TryAgainException(String message) {
      super(message);
    }

    public TryAgainException(String message, Throwable cause) {
      super(message, cause);
    }

    public TryAgainException(Throwable cause) {
      super(cause);
    }
  }

  private static class FatalErrorException extends Exception {

    private static final long serialVersionUID = 1L;

    public FatalErrorException() {
    }

    public FatalErrorException(Throwable cause) {
      super(cause);
    }
  }

  static interface Callback extends Supervisor {

    void waitUntilStartPermision();

    void recoveryFinished(RecoveryService service);

    void recoveryFailed(RecoveryService service);

    void recoveryFailed(RecoveryService service, Throwable ex);

    public void setConsistentState(boolean consistent);

    public boolean canAcceptWrites(String database);

    @Override
    public default SupervisorDecision onError(Object supervised, Throwable error) {
      recoveryFailed((RecoveryService) supervised, error);
      return SupervisorDecision.IGNORE;
    }
  }

  public static interface RecoveryServiceFactory {

    RecoveryService createRecoveryService(Callback callback);
  }

}
