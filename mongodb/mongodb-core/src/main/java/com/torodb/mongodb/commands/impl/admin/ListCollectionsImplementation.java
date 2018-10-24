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

package com.torodb.mongodb.commands.impl.admin;

import com.torodb.core.logging.LoggerFactory;
import com.torodb.mongodb.commands.impl.ReadTorodbCommandImpl;
import com.torodb.mongodb.commands.pojos.CollectionOptions;
import com.torodb.mongodb.commands.pojos.CollectionOptions.AutoIndexMode;
import com.torodb.mongodb.commands.pojos.CursorResult;
import com.torodb.mongodb.commands.signatures.admin.ListCollectionsCommand.ListCollectionsArgument;
import com.torodb.mongodb.commands.signatures.admin.ListCollectionsCommand.ListCollectionsResult;
import com.torodb.mongodb.commands.signatures.admin.ListCollectionsCommand.ListCollectionsResult.Entry;
import com.torodb.mongodb.core.MongodTransaction;
import com.torodb.mongodb.utils.NamespaceUtil;
import com.torodb.mongowp.ErrorCode;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.bson.utils.DefaultBsonValues;
import com.torodb.mongowp.commands.Command;
import com.torodb.mongowp.commands.Request;
import org.apache.logging.log4j.Logger;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

@ThreadSafe
public class ListCollectionsImplementation implements
    ReadTorodbCommandImpl<ListCollectionsArgument, ListCollectionsResult> {

  private final Logger logger;

  @Inject
  public ListCollectionsImplementation(LoggerFactory loggerFactory) {
    this.logger = loggerFactory.apply(this.getClass());
  }

  private static final CollectionOptions DEFAULT_COLLECTION_OPTIONS =
      new CollectionOptions.Builder()
          .setAutoIndexMode(AutoIndexMode.DEFAULT)
          .setCapped(false)
          .setStorageEngine(DefaultBsonValues.newDocument("engine", DefaultBsonValues.newString(
              "torodb")))
          .setTemp(false)
          .build();

  @Override
  public Status<ListCollectionsResult> apply(Request req,
      Command<? super ListCollectionsArgument, ? super ListCollectionsResult> command,
      ListCollectionsArgument arg, MongodTransaction context) {

    if (arg.getFilter() != null && !arg.getFilter().isEmpty()) {
      logger.debug("Recived a {} with the unsupported filter {}", command.getCommandName(), arg
          .getFilter());
      return Status.from(ErrorCode.COMMAND_FAILED, command.getCommandName()
          + " with filters are not supported right now");
    }

    return Status.ok(
        new ListCollectionsResult(
            CursorResult.createSingleBatchCursor(req.getDatabase(),
                NamespaceUtil.LIST_COLLECTIONS_GET_MORE_COLLECTION,
                context.getDocTransaction().getCollectionsInfo(req.getDatabase()).map(colInfo ->
                    new Entry(colInfo.getName(), colInfo.getType(), DEFAULT_COLLECTION_OPTIONS)
                )
            )
        )
    );

  }

}
