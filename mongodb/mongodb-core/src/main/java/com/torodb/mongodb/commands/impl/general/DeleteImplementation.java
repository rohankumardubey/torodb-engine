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

import com.torodb.core.language.AttributeReference;
import com.torodb.core.language.AttributeReference.Builder;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.kvdocument.values.KvValue;
import com.torodb.mongodb.commands.impl.WriteTransactionCommandImpl;
import com.torodb.mongodb.commands.signatures.general.DeleteCommand.DeleteArgument;
import com.torodb.mongodb.commands.signatures.general.DeleteCommand.DeleteStatement;
import com.torodb.mongodb.core.WriteMongodTransaction;
import com.torodb.mongowp.ErrorCode;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.bson.BsonDocument;
import com.torodb.mongowp.commands.Command;
import com.torodb.mongowp.commands.Request;
import com.torodb.mongowp.exceptions.CommandFailed;
import com.torodb.torod.WriteDocTransaction;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class DeleteImplementation implements WriteTransactionCommandImpl<DeleteArgument, Long> {

  private final Logger logger;

  public DeleteImplementation(LoggerFactory loggerFactory) {
    this.logger = loggerFactory.apply(this.getClass());
  }
  
  @Override
  public Status<Long> apply(Request req, Command<? super DeleteArgument, ? super Long> command,
      DeleteArgument arg,
      WriteMongodTransaction context) {
    Long deleted = 0L;

    for (DeleteStatement deleteStatement : arg.getStatements()) {
      BsonDocument query = deleteStatement.getQuery();

      switch (query.size()) {
        case 0: {
          deleted += context.getDocTransaction()
              .deleteAll(req.getDatabase(), arg.getCollection());
          break;
        }
        case 1: {
          try {
            logDeleteCommand(arg);
            deleted += deleteByAttribute(context.getDocTransaction(), req.getDatabase(), arg
                .getCollection(), query);
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
    }
    context.getMetrics().getDeletes().mark(deleted);
    return Status.ok(deleted);

  }

  private long deleteByAttribute(WriteDocTransaction transaction, String db, String col,
      BsonDocument query) throws CommandFailed {
    Builder refBuilder = new AttributeReference.Builder();
    KvValue<?> kvValue = AttrRefHelper.calculateValueAndAttRef(query, refBuilder);
    return transaction.deleteByAttRef(db, col, refBuilder.build(), kvValue);
  }

  private void logDeleteCommand(DeleteArgument arg) {
    if (logger.isTraceEnabled()) {
      String collection = arg.getCollection();
      String filter = StreamSupport.stream(arg.getStatements().spliterator(), false)
          .map(statement -> statement.getQuery().toString())
          .collect(Collectors.joining(","));

      logger.trace("Delete from {} filter {}", collection, filter);
    }
  }

}
