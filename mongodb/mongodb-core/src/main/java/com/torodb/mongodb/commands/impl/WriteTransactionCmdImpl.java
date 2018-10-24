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

package com.torodb.mongodb.commands.impl;

import com.google.common.collect.ImmutableMap;
import com.torodb.core.annotations.DoNotChange;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.mongodb.commands.CmdImplMapSupplier;
import com.torodb.mongodb.commands.impl.general.DeleteImplementation;
import com.torodb.mongodb.commands.impl.general.InsertImplementation;
import com.torodb.mongodb.commands.impl.general.UpdateImplementation;
import com.torodb.mongodb.commands.signatures.general.DeleteCommand;
import com.torodb.mongodb.commands.signatures.general.InsertCommand;
import com.torodb.mongodb.commands.signatures.general.UpdateCommand;
import com.torodb.mongodb.core.WriteMongodTransaction;
import com.torodb.mongowp.commands.Command;
import com.torodb.mongowp.commands.CommandImplementation;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@SuppressWarnings("checkstyle:LineLength")
public class WriteTransactionCmdImpl implements CmdImplMapSupplier<WriteMongodTransaction> {

  private final ImmutableMap<Command<?, ?>, CommandImplementation<?, ?, ? super WriteMongodTransaction>> map;

  @Inject
  WriteTransactionCmdImpl(LoggerFactory loggerFactory) {
    this.map = ImmutableMap.<Command<?, ?>, CommandImplementation<?, ?, ? super WriteMongodTransaction>>builder()
        .put(InsertCommand.INSTANCE, new InsertImplementation())
        .put(DeleteCommand.INSTANCE, new DeleteImplementation(loggerFactory))
        .put(UpdateCommand.INSTANCE, new UpdateImplementation())
        .build();
  }

  @DoNotChange
  Set<Command<?, ?>> getSupportedCommands() {
    return map.keySet();
  }

  @Override
  public ImmutableMap<Command<?, ?>, CommandImplementation<?, ?, ? super WriteMongodTransaction>> get() {
    return map;
  }

}
