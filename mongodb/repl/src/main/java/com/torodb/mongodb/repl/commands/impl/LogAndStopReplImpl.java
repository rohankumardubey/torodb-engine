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

import com.torodb.common.util.Empty;
import com.torodb.core.supervision.Supervisor;
import com.torodb.mongodb.repl.commands.LogAndStopCommand;
import com.torodb.mongodb.repl.guice.MongoDbRepl;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.commands.Command;
import com.torodb.mongowp.commands.Request;
import com.torodb.torod.SchemaOperationExecutor;

import javax.inject.Inject;


/**
 * The implementation of {@link LogAndStopCommand}.
 */
public class LogAndStopReplImpl extends ReplCommandImpl<String, Empty> {

  private final Supervisor supervisor;
  private final CommandFilterUtil filterUtil;

  @Inject
  public LogAndStopReplImpl(@MongoDbRepl Supervisor supervisor, CommandFilterUtil filterUtil) {
    this.supervisor = supervisor;
    this.filterUtil = filterUtil;
  }

  @Override
  public Status<Empty> apply(Request req, Command<? super String, ? super Empty> command, 
      String arg, SchemaOperationExecutor schemaEx) {
    if (!filterUtil.testDbFilter(req.getDatabase(), command)) {
      return Status.ok();
    }

    UnsupportedOperationException ex = new UnsupportedOperationException(
        "Command " + arg + " is not supported yet");
    supervisor.onError(this, ex);
    throw ex;
  }

}
