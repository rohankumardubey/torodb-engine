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

package com.torodb.mongodb.repl.commands;

import com.torodb.mongodb.commands.signatures.admin.CreateCollectionCommand;
import com.torodb.mongodb.commands.signatures.admin.CreateIndexesCommand;
import com.torodb.mongodb.commands.signatures.admin.DropCollectionCommand;
import com.torodb.mongodb.commands.signatures.admin.DropDatabaseCommand;
import com.torodb.mongodb.commands.signatures.admin.DropIndexesCommand;
import com.torodb.mongodb.commands.signatures.admin.RenameCollectionCommand;
import com.torodb.mongodb.repl.commands.impl.CreateCollectionReplImpl;
import com.torodb.mongodb.repl.commands.impl.CreateIndexesReplImpl;
import com.torodb.mongodb.repl.commands.impl.DropCollectionReplImpl;
import com.torodb.mongodb.repl.commands.impl.DropDatabaseReplImpl;
import com.torodb.mongodb.repl.commands.impl.DropIndexesReplImpl;
import com.torodb.mongodb.repl.commands.impl.LogAndIgnoreReplImpl;
import com.torodb.mongodb.repl.commands.impl.LogAndStopReplImpl;
import com.torodb.mongodb.repl.commands.impl.RenameCollectionReplImpl;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.commands.Command;
import com.torodb.mongowp.commands.CommandExecutor;
import com.torodb.mongowp.commands.Request;
import com.torodb.mongowp.commands.impl.MapBasedCommandExecutor;
import com.torodb.torod.SchemaOperationExecutor;

import javax.inject.Inject;

public final class ReplCommandExecutor
    implements CommandExecutor<SchemaOperationExecutor> {

  private final MapBasedCommandExecutor<SchemaOperationExecutor> delegate;

  @Inject
  public ReplCommandExecutor(ReplCommandLibrary library,
      LogAndStopReplImpl logAndStopReplImpl,
      LogAndIgnoreReplImpl logAndIgnoreReplImpl,
      CreateCollectionReplImpl createCollectionReplImpl,
      CreateIndexesReplImpl createIndexesReplImpl,
      DropCollectionReplImpl dropCollectionReplImpl,
      DropDatabaseReplImpl dropDatabaseReplImpl,
      DropIndexesReplImpl dropIndexesReplImpl,
      RenameCollectionReplImpl renameCollectionReplImpl) {
    delegate = MapBasedCommandExecutor
        .<SchemaOperationExecutor>fromLibraryBuilder(library)
        .addImplementation(LogAndStopCommand.INSTANCE, logAndStopReplImpl)
        .addImplementation(LogAndIgnoreCommand.INSTANCE, logAndIgnoreReplImpl)
        //                .addImplementation(ApplyOpsCommand.INSTANCE, whatever)
        //                .addImplementation(colmod, whatever)
        //                .addImplementation(coverToCapped, whatever)
        .addImplementation(CreateCollectionCommand.INSTANCE, createCollectionReplImpl)
        .addImplementation(CreateIndexesCommand.INSTANCE, createIndexesReplImpl)
        .addImplementation(DropCollectionCommand.INSTANCE, dropCollectionReplImpl)
        .addImplementation(DropDatabaseCommand.INSTANCE, dropDatabaseReplImpl)
        .addImplementation(DropIndexesCommand.INSTANCE, dropIndexesReplImpl)
        .addImplementation(RenameCollectionCommand.INSTANCE, renameCollectionReplImpl)
        //                .addImplementation(emptycapped, whatever)

        .build();
  }

  @Override
  public <A, R> Status<R> execute(Request request, Command<? super A, ? super R> command, A arg,
      SchemaOperationExecutor context) {
    return delegate.execute(request, command, arg, context);
  }
}
