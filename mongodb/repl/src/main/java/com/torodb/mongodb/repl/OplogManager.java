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

import static com.torodb.mongowp.bson.utils.DefaultBsonValues.EMPTY_DOC;
import static com.torodb.mongowp.bson.utils.DefaultBsonValues.newLong;

import com.google.common.base.Preconditions;
import com.torodb.common.util.Empty;
import com.torodb.core.annotations.TorodbIdleService;
import com.torodb.core.exceptions.user.UserException;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.core.retrier.Retrier;
import com.torodb.core.retrier.Retrier.Hint;
import com.torodb.core.retrier.RetrierAbortException;
import com.torodb.core.retrier.RetrierGiveUpException;
import com.torodb.core.services.IdleTorodbService;
import com.torodb.mongodb.annotations.Locked;
import com.torodb.mongodb.commands.signatures.general.DeleteCommand;
import com.torodb.mongodb.commands.signatures.general.DeleteCommand.DeleteArgument;
import com.torodb.mongodb.commands.signatures.general.DeleteCommand.DeleteStatement;
import com.torodb.mongodb.commands.signatures.general.FindCommand;
import com.torodb.mongodb.commands.signatures.general.FindCommand.FindArgument;
import com.torodb.mongodb.commands.signatures.general.FindCommand.FindResult;
import com.torodb.mongodb.commands.signatures.general.InsertCommand;
import com.torodb.mongodb.commands.signatures.general.InsertCommand.InsertArgument;
import com.torodb.mongodb.commands.signatures.general.InsertCommand.InsertResult;
import com.torodb.mongodb.core.MongodServer;
import com.torodb.mongodb.core.MongodTransaction;
import com.torodb.mongodb.core.WriteMongodTransaction;
import com.torodb.mongowp.ErrorCode;
import com.torodb.mongowp.OpTime;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.bson.BsonDateTime;
import com.torodb.mongowp.bson.BsonDocument;
import com.torodb.mongowp.bson.utils.DefaultBsonValues;
import com.torodb.mongowp.bson.utils.TimestampToDateTime;
import com.torodb.mongowp.commands.Request;
import com.torodb.mongowp.commands.oplog.OplogOperation;
import com.torodb.mongowp.exceptions.MongoException;
import com.torodb.mongowp.utils.BsonDocumentBuilder;
import com.torodb.mongowp.utils.BsonReaderTool;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.util.Iterator;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

@ThreadSafe
public class OplogManager extends IdleTorodbService {

  private static final String KEY = "lastAppliedOplogEntry";
  private static final BsonDocument DOC_QUERY = EMPTY_DOC;
  private static final String OPLOG_DB = "torodb";
  private static final String OPLOG_COL = "oplog.replication";

  private final Logger logger;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private long lastAppliedHash;
  private OpTime lastAppliedOpTime;
  private final MongodServer server;
  private final Retrier retrier;
  private final ReplMetrics metrics;

  @Inject
  public OplogManager(@TorodbIdleService ThreadFactory threadFactory, LoggerFactory lf,
      MongodServer mongodServer, Retrier retrier, ReplMetrics metrics) {
    super(threadFactory);
    this.logger = lf.apply(this.getClass());
    this.server = mongodServer;
    this.retrier = retrier;
    this.metrics = metrics;
  }

  public ReadOplogTransaction createReadTransaction() {
    Preconditions.checkState(isRunning(), "The service is not running");
    return new ReadOplogTransaction(lock.readLock());
  }

  public WriteOplogTransaction createWriteTransaction() {
    Preconditions.checkState(isRunning(), "The service is not running");
    return new WriteOplogTransaction(lock.writeLock());
  }

  private void notifyLastAppliedOpTimeChange() {
    metrics.getLastOpTimeApplied().setValue(lastAppliedOpTime.toString());
  }

  @Override
  protected void startUp() throws Exception {
    logger.debug("Starting OplogManager");
    Lock mutex = lock.writeLock();
    mutex.lock();
    try {
      loadState();
    } finally {
      mutex.unlock();
    }
    logger.debug("Started OplogManager");
  }

  @Override
  protected void shutDown() throws Exception {
    logger.debug("Stopping OplogManager");
  }

  @Locked(exclusive = true)
  private void storeState(long hash, OpTime opTime) throws OplogManagerPersistException {
    Preconditions.checkState(isRunning(), "The service is not running");

    try {
      retrier.retry(() -> {
        try (WriteMongodTransaction transaction = server.openWriteTransaction()) {
          Status<Long> deleteResult = transaction.execute(
              new Request(OPLOG_DB, null, true, null),
              DeleteCommand.INSTANCE,
              new DeleteArgument.Builder(OPLOG_COL)
                  .addStatement(new DeleteStatement(DOC_QUERY, false))
                  .build()
          );
          if (!deleteResult.isOk()) {
            throw new RetrierAbortException(new MongoException(deleteResult));
          }
          //TODO: This should be stored as timestamp once TORODB-189 is resolved
          long optimeAsLong = opTime.toOldBson().getMillisFromUnix();

          Status<InsertResult> insertResult = transaction.execute(
              new Request(OPLOG_DB, null, true, null),
              InsertCommand.INSTANCE,
              new InsertArgument.Builder(OPLOG_COL)
                  .addDocument(
                      new BsonDocumentBuilder()
                          .appendUnsafe(KEY, new BsonDocumentBuilder()
                              .appendUnsafe("hash", newLong(hash))
                              .appendUnsafe("optime_i", DefaultBsonValues.newLong(optimeAsLong))
                              .appendUnsafe("optime_t", newLong(opTime.getTerm()))
                              .build()
                          ).build()
                  ).build()
          );
          if (insertResult.isOk() && insertResult.getResult().getN() != 1) {
            throw new RetrierAbortException(new MongoException(ErrorCode.OPERATION_FAILED,
                "More than one element inserted"));
          }
          if (!insertResult.isOk()) {
            throw new RetrierAbortException(new MongoException(insertResult));
          }
          transaction.commit();
          return Empty.getInstance();
        } catch (UserException ex) {
          throw new RetrierAbortException(ex);
        }
      }, Hint.INFREQUENT_ROLLBACK);
    } catch (RetrierGiveUpException ex) {
      throw new OplogManagerPersistException(ex);
    }
  }

  @Locked(exclusive = true)
  private void loadState() throws OplogManagerPersistException {
    try {
      retrier.retry(() -> {
        try (MongodTransaction transaction = server.openReadTransaction()) {
          Status<FindResult> status = transaction.execute(
              new Request(OPLOG_DB, null, true, null),
              FindCommand.INSTANCE,
              new FindArgument.Builder()
                  .setCollection(OPLOG_COL)
                  .setSlaveOk(true)
                  .build()
          );
          if (!status.isOk()) {
            throw new RetrierAbortException(new MongoException(status));
          }

          Iterator<BsonDocument> batch = status.getResult().getCursor().getFirstBatch();
          if (!batch.hasNext()) {
            lastAppliedHash = 0;
            lastAppliedOpTime = OpTime.EPOCH;
          } else {
            BsonDocument doc = batch.next();

            BsonDocument subDoc = BsonReaderTool.getDocument(doc, KEY);
            lastAppliedHash = BsonReaderTool.getLong(subDoc, "hash");

            long optimeAsLong = BsonReaderTool.getLong(subDoc, "optime_i");
            BsonDateTime optimeAsDateTime = DefaultBsonValues.newDateTime(optimeAsLong);

            lastAppliedOpTime = new OpTime(
                TimestampToDateTime.toTimestamp(optimeAsDateTime, DefaultBsonValues::newTimestamp),
                BsonReaderTool.getLong(subDoc, "optime_t")
            );
          }
          notifyLastAppliedOpTimeChange();
          return Empty.getInstance();
        }
      }, Hint.INFREQUENT_ROLLBACK);
    } catch (RetrierGiveUpException ex) {
      throw new OplogManagerPersistException(ex);
    }
  }

  public static class OplogManagerPersistException extends Exception {

    private static final long serialVersionUID = -2352073393613989057L;

    public OplogManagerPersistException(String message) {
      super(message);
    }

    public OplogManagerPersistException(String message, Throwable cause) {
      super(message, cause);
    }

    public OplogManagerPersistException(Throwable cause) {
      super(cause);
    }

  }

  @NotThreadSafe
  public class ReadOplogTransaction implements Closeable {

    private final Lock readLock;
    private boolean closed;

    private ReadOplogTransaction(Lock readLock) {
      this.readLock = readLock;
      readLock.lock();
      closed = false;
    }

    public long getLastAppliedHash() {
      if (closed) {
        throw new IllegalStateException("Transaction closed");
      }
      return lastAppliedHash;
    }

    @Nonnull
    public OpTime getLastAppliedOptime() {
      if (closed) {
        throw new IllegalStateException("Transaction closed");
      }
      if (lastAppliedOpTime == null) {
        throw new AssertionError("lastAppliedOpTime should not be null");
      }
      return lastAppliedOpTime;
    }

    @Override
    public void close() {
      if (!closed) {
        closed = true;
        readLock.unlock();
      }
    }
  }

  @NotThreadSafe
  public class WriteOplogTransaction implements Closeable {

    private final Lock writeLock;
    private boolean closed = false;

    public WriteOplogTransaction(Lock writeLock) {
      this.writeLock = writeLock;
      writeLock.lock();
      closed = false;
    }

    public long getLastAppliedHash() {
      if (closed) {
        throw new IllegalStateException("Transaction closed");
      }
      return lastAppliedHash;
    }

    public OpTime getLastAppliedOptime() {
      if (closed) {
        throw new IllegalStateException("Transaction closed");
      }
      return lastAppliedOpTime;
    }

    public void addOperation(@Nonnull OplogOperation op) throws OplogManagerPersistException {
      if (closed) {
        throw new IllegalStateException("Transaction closed");
      }

      storeState(op.getHash(), op.getOpTime());

      lastAppliedHash = op.getHash();
      lastAppliedOpTime = op.getOpTime();
      notifyLastAppliedOpTimeChange();
    }

    public void forceNewValue(long newHash, OpTime newOptime) throws OplogManagerPersistException {
      if (closed) {
        throw new IllegalStateException("Transaction closed");
      }
      storeState(newHash, newOptime);

      OplogManager.this.lastAppliedHash = newHash;
      OplogManager.this.lastAppliedOpTime = newOptime;
      notifyLastAppliedOpTimeChange();
    }

    @Override
    public void close() {
      if (!closed) {
        closed = true;
        writeLock.unlock();
      }
    }

    /**
     * Deletes all information on the current oplog and reset all its variables (like
     * lastAppliedHash or lastAppliedOptime).
     */
    void truncate() throws OplogManagerPersistException {
      if (closed) {
        throw new IllegalStateException("Transaction closed");
      }
      storeState(0, OpTime.EPOCH);

      lastAppliedHash = 0;
      lastAppliedOpTime = OpTime.EPOCH;
      notifyLastAppliedOpTimeChange();
    }

  }
}
