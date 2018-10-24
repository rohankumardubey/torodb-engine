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

import com.torodb.core.cursors.Cursor;
import com.torodb.core.language.AttributeReference;
import com.torodb.core.language.AttributeReference.Builder;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.kvdocument.conversion.mongowp.ToBsonDocumentTranslator;
import com.torodb.kvdocument.values.KvDocument;
import com.torodb.kvdocument.values.KvValue;
import com.torodb.mongodb.commands.impl.ReadTorodbCommandImpl;
import com.torodb.mongodb.commands.pojos.CursorResult;
import com.torodb.mongodb.commands.signatures.general.FindCommand.FindArgument;
import com.torodb.mongodb.commands.signatures.general.FindCommand.FindResult;
import com.torodb.mongodb.core.MongodTransaction;
import com.torodb.mongowp.ErrorCode;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.bson.BsonDocument;
import com.torodb.mongowp.commands.Command;
import com.torodb.mongowp.commands.Request;
import com.torodb.mongowp.exceptions.CommandFailed;
import com.torodb.torod.DocTransaction;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.OptionalLong;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FindImplementation implements ReadTorodbCommandImpl<FindArgument, FindResult> {

  private final Logger logger;

  @Inject
  public FindImplementation(LoggerFactory loggerFactory) {
    this.logger = loggerFactory.apply(this.getClass());
  }

  @Override
  public Status<FindResult> apply(Request req,
      Command<? super FindArgument, ? super FindResult> command, FindArgument arg,
      MongodTransaction context) {
    logFindCommand(arg);

    BsonDocument filter = arg.getFilter();

    Cursor<BsonDocument> cursor;

    switch (filter.size()) {
      case 0: {
        cursor = context.getDocTransaction().findAll(req.getDatabase(), arg.getCollection())
            .asDocCursor()
            .transform(t -> t.getRoot())
            .transform(ToBsonDocumentTranslator.getInstance());
        break;
      }
      case 1: {
        try {
          cursor = getByAttributeCursor(context.getDocTransaction(), req.getDatabase(), arg
              .getCollection(), filter)
              .transform(ToBsonDocumentTranslator.getInstance());
        } catch (CommandFailed ex) {
          return Status.from(ex);
        }
        break;
      }
      default: {
        return Status.from(ErrorCode.COMMAND_FAILED, "The given query is not supported right now");
      }
    }

    if (Long.valueOf(arg.getBatchSize()) > (long) Integer.MAX_VALUE) {
      return Status.from(ErrorCode.COMMAND_FAILED, "Only batchSize equals or lower than "
          + Integer.MAX_VALUE + " is supported");
    }

    OptionalLong batchSize = arg.getEffectiveBatchSize();
    List<BsonDocument> batch = cursor.getNextBatch(batchSize.isPresent() ? (int) batchSize
        .getAsLong() : 101);
    cursor.close();

    return Status.ok(new FindResult(CursorResult.createSingleBatchCursor(req.getDatabase(), arg
        .getCollection(), batch.iterator())));

  }

  private Cursor<KvDocument> getByAttributeCursor(DocTransaction transaction, String db,
      String col, BsonDocument filter) throws CommandFailed {

    Builder refBuilder = new AttributeReference.Builder();
    KvValue<?> kvValue = AttrRefHelper.calculateValueAndAttRef(filter, refBuilder);

    return transaction.findByAttRef(db, col, refBuilder.build(), kvValue)
        .asDocCursor()
        .transform(t -> t.getRoot());
  }

  private void logFindCommand(FindArgument arg) {
    logger.trace("Find into {} filter {}", arg.getCollection(), arg.getFilter());
  }

}
