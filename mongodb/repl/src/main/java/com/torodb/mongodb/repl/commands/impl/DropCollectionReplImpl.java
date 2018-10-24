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

package com.torodb.mongodb.repl.commands.impl;

import com.torodb.core.logging.LoggerFactory;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.commands.Command;
import com.torodb.mongowp.commands.Request;
import com.torodb.mongowp.commands.impl.CollectionCommandArgument;
import com.torodb.mongowp.commands.tools.Empty;
import com.torodb.torod.SchemaOperationExecutor;
import com.torodb.torod.exception.UnexistentDatabaseException;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;

public class DropCollectionReplImpl extends ReplCommandImpl<CollectionCommandArgument, Empty> {

  private final Logger logger;

  private final CommandFilterUtil filterUtil;

  @Inject
  public DropCollectionReplImpl(CommandFilterUtil filterUtil, LoggerFactory lf) {
    this.filterUtil = filterUtil;
    this.logger = lf.apply(this.getClass());
  }

  @Override
  public Status<Empty> apply(
      Request req,
      Command<? super CollectionCommandArgument, ? super Empty> command,
      CollectionCommandArgument arg,
      SchemaOperationExecutor schemaEx) {

    if (!filterUtil.testNamespaceFilter(req.getDatabase(), arg.getCollection(), command)) {
      return Status.ok();
    }

    try {
      logger.info("Dropping collection {}.{}", req.getDatabase(), arg.getCollection());

      schemaEx.dropCollection(req.getDatabase(), arg.getCollection());
    } catch (UnexistentDatabaseException ex) {
      logger.info("Trying to drop collection {}.{} but it has not been found. "
            + "This is normal when reapplying oplog during a recovery. Ignoring operation",
            req.getDatabase(), arg.getCollection());
    }

    return Status.ok();

  }

}
