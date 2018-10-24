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

package com.torodb.mongodb.commands.impl.diagnostic;

import com.torodb.mongodb.commands.impl.ServerCommandImpl;
import com.torodb.mongodb.commands.signatures.diagnostic.GetLogCommand.AsteriskGetLogReply;
import com.torodb.mongodb.commands.signatures.diagnostic.GetLogCommand.GetLogArgument;
import com.torodb.mongodb.commands.signatures.diagnostic.GetLogCommand.GetLogReply;
import com.torodb.mongodb.commands.signatures.diagnostic.GetLogCommand.LogGetLogReply;
import com.torodb.mongodb.core.MongodServer;
import com.torodb.mongowp.ErrorCode;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.commands.Command;
import com.torodb.mongowp.commands.Request;

import java.util.Collections;

/**
 *
 */
public class GetLogImplementation extends ServerCommandImpl<GetLogArgument, GetLogReply> {

  @Override
  public Status<GetLogReply> apply(Request req,
      Command<? super GetLogArgument, ? super GetLogReply> command, GetLogArgument arg,
      MongodServer context) {
    if (arg.isIsAsterisk()) {
      return Status.ok(new AsteriskGetLogReply(Collections.emptyList()));
    } else {
      switch (arg.getLogName()) {
        case "startupWarnings": { //MOCKED
          return Status.ok(new LogGetLogReply(0, Collections.emptyList()));
        }
        default: {
          return Status.from(ErrorCode.COMMAND_FAILED, "no RamLog named " + arg.getLogName());
        }
      }
    }
  }

}
