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
import com.torodb.mongowp.commands.tools.Empty;
import com.torodb.torod.SchemaOperationExecutor;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;


public class DropDatabaseReplImpl extends ReplCommandImpl<Empty, Empty> {

  private final Logger logger;
  private final CommandFilterUtil filterUtil;

  @Inject
  public DropDatabaseReplImpl(CommandFilterUtil filterUtil, LoggerFactory loggerFactory) {
    this.logger = loggerFactory.apply(this.getClass());
    this.filterUtil = filterUtil;
  }

  @Override
  public Status<Empty> apply(Request req,
      Command<? super Empty, ? super Empty> command, Empty arg,
      SchemaOperationExecutor schemaEx) {

    if (!filterUtil.testDbFilter(req.getDatabase(), command)) {
      return Status.ok();
    }
    
    logger.info("Dropping database {}", req.getDatabase());

    schemaEx.dropDatabase(req.getDatabase());

    return Status.ok();
  }

}
