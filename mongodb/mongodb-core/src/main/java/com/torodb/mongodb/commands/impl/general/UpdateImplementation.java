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

package com.torodb.mongodb.commands.impl.general;

import com.google.common.collect.ImmutableList;
import com.torodb.core.cursors.Cursor;
import com.torodb.core.document.ToroDocument;
import com.torodb.core.exceptions.UserWrappedException;
import com.torodb.core.exceptions.user.UpdateException;
import com.torodb.core.exceptions.user.UserException;
import com.torodb.core.language.AttributeReference;
import com.torodb.core.language.AttributeReference.Builder;
import com.torodb.kvdocument.conversion.mongowp.MongoWpConverter;
import com.torodb.kvdocument.values.KvDocument;
import com.torodb.kvdocument.values.KvDocument.DocEntry;
import com.torodb.kvdocument.values.KvValue;
import com.torodb.mongodb.commands.impl.WriteTransactionCommandImpl;
import com.torodb.mongodb.commands.signatures.general.UpdateCommand.UpdateArgument;
import com.torodb.mongodb.commands.signatures.general.UpdateCommand.UpdateResult;
import com.torodb.mongodb.commands.signatures.general.UpdateCommand.UpdateStatement;
import com.torodb.mongodb.commands.signatures.general.UpdateCommand.UpsertResult;
import com.torodb.mongodb.core.MongodMetrics;
import com.torodb.mongodb.core.WriteMongodTransaction;
import com.torodb.mongodb.language.UpdateActionTranslator;
import com.torodb.mongodb.language.update.SetDocumentUpdateAction;
import com.torodb.mongodb.language.update.UpdateAction;
import com.torodb.mongodb.language.update.UpdatedToroDocumentBuilder;
import com.torodb.mongodb.utils.DefaultIdUtils;
import com.torodb.mongowp.ErrorCode;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.bson.BsonDocument;
import com.torodb.mongowp.commands.Command;
import com.torodb.mongowp.commands.Request;
import com.torodb.mongowp.exceptions.CommandFailed;
import com.torodb.torod.WriteDocTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.ThreadSafe;

/**
 *
 */
@ThreadSafe
public class UpdateImplementation
    implements WriteTransactionCommandImpl<UpdateArgument, UpdateResult> {

  @Override
  public Status<UpdateResult> apply(Request req,
      Command<? super UpdateArgument, ? super UpdateResult> command, UpdateArgument arg,
      WriteMongodTransaction context) {
    UpdateStatus updateStatus = new UpdateStatus();

    try {
      for (UpdateStatement updateStatement : arg.getStatements()) {
        BsonDocument query = updateStatement.getQuery();
        UpdateAction updateAction = UpdateActionTranslator.translate(updateStatement.getUpdate());
        Cursor<ToroDocument> candidatesCursor;
        switch (query.size()) {
          case 0: {
            candidatesCursor = context.getDocTransaction()
                .findAll(req.getDatabase(), arg.getCollection())
                .asDocCursor();
            break;
          }
          case 1: {
            try {
              candidatesCursor = findByAttribute(context.getDocTransaction(), req.getDatabase(),
                  arg.getCollection(), query);
            } catch (CommandFailed ex) {
              return Status.from(ex);
            }
            break;
          }
          default: {
            return Status.from(ErrorCode.COMMAND_FAILED,
                "The given query is not supported right now");
          }
        }

        if (candidatesCursor.hasNext()) {
          try {
            Stream<List<ToroDocument>> candidatesbatchStream;
            if (updateStatement.isMulti()) {
              candidatesbatchStream = StreamSupport.stream(
                  Spliterators.spliteratorUnknownSize(candidatesCursor.batch(100),
                      Spliterator.ORDERED), false);
            } else {
              candidatesbatchStream = Stream.of(ImmutableList.of(candidatesCursor.next()));
            }
            Stream<KvDocument> updatedCandidates = candidatesbatchStream
                .map(candidates -> {
                  updateStatus.increaseCandidates(candidates.size());
                  context.getDocTransaction().delete(req.getDatabase(), arg.getCollection(),
                      candidates);
                  return candidates;
                })
                .flatMap(l -> l.stream())
                .map(candidate -> {
                  try {
                    updateStatus.increaseUpdated();
                    return update(updateAction, candidate);
                  } catch (UserException userException) {
                    throw new UserWrappedException(userException);
                  }
                });
            context.getDocTransaction().insert(req.getDatabase(), arg.getCollection(),
                updatedCandidates);
          } catch (UserWrappedException userWrappedException) {
            throw userWrappedException.getCause();
          }
        } else if (updateStatement.isUpsert()) {
          KvDocument toInsertCandidate;
          if (updateAction instanceof SetDocumentUpdateAction) {
            toInsertCandidate = ((SetDocumentUpdateAction) updateAction).getNewValue();
          } else {
            toInsertCandidate =
                update(updateAction,
                    new ToroDocument(-1, (KvDocument) MongoWpConverter.translate(query)));
          }
          if (!toInsertCandidate.containsKey(DefaultIdUtils.ID_KEY)) {
            KvDocument.Builder builder = new KvDocument.Builder();
            for (DocEntry<?> entry : toInsertCandidate) {
              builder.putValue(entry.getKey(), entry.getValue());
            }
            builder.putValue(DefaultIdUtils.ID_KEY, MongoWpConverter.translate(
                context.getObjectIdFactory().consumeObjectId())
            );
            toInsertCandidate = builder.build();
          }
          updateStatus.increaseCandidates(1);
          updateStatus.increaseCreated(toInsertCandidate.get(DefaultIdUtils.ID_KEY));
          Stream<KvDocument> toInsertCandidates = Stream.of(toInsertCandidate);
          context.getDocTransaction().insert(req.getDatabase(), arg.getCollection(),
              toInsertCandidates);
        }
      }
    } catch (UserException ex) {
      //TODO: Improve error reporting
      return Status.from(ErrorCode.COMMAND_FAILED, ex.getLocalizedMessage());
    }
    MongodMetrics metrics = context.getMetrics();
    metrics.getUpdateModified().mark(updateStatus.updated);
    metrics.getUpdateMatched().mark(updateStatus.candidates);
    metrics.getUpdateUpserted().mark(updateStatus.upsertResults.size());
    return Status.ok(new UpdateResult(updateStatus.updated, updateStatus.candidates,
        ImmutableList.copyOf(updateStatus.upsertResults)));
  }

  private static class UpdateStatus {

    int candidates = 0;
    int updated = 0;
    int created = 0;
    List<UpsertResult> upsertResults = new ArrayList<>();

    void increaseCandidates(int size) {
      candidates += size;
    }

    void increaseUpdated() {
      updated++;
    }

    void increaseCreated(KvValue<?> id) {
      upsertResults.add(new UpsertResult(created,
          MongoWpConverter.translate(id)));
      created++;
    }
  }

  protected KvDocument update(UpdateAction updateAction, ToroDocument candidate) throws
      UpdateException {
    UpdatedToroDocumentBuilder builder =
        UpdatedToroDocumentBuilder.from(candidate);
    updateAction.apply(builder);
    return builder.build();
  }

  private Cursor<ToroDocument> findByAttribute(WriteDocTransaction transaction, String db,
      String col, BsonDocument query) throws CommandFailed, UserException {
    Builder refBuilder = new AttributeReference.Builder();
    KvValue<?> kvValue = AttrRefHelper.calculateValueAndAttRef(query, refBuilder);

    return transaction.findByAttRef(db, col, refBuilder.build(), kvValue).asDocCursor();
  }

}
