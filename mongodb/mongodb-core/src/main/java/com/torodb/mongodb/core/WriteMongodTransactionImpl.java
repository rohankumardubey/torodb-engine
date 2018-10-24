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

package com.torodb.mongodb.core;

import com.torodb.core.exceptions.user.UserException;
import com.torodb.core.transaction.RollbackException;
import com.torodb.mongodb.commands.CommandClassifier;
import com.torodb.mongodb.language.ObjectIdFactory;
import com.torodb.mongowp.Status;
import com.torodb.mongowp.commands.Command;
import com.torodb.mongowp.commands.CommandExecutor;
import com.torodb.mongowp.commands.Request;
import com.torodb.torod.WriteDocTransaction;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

/**
 *
 */
class WriteMongodTransactionImpl extends MongodTransactionImpl implements WriteMongodTransaction {

  private final WriteDocTransaction torodTransaction;
  private final CommandExecutor<? super WriteMongodTransactionImpl> commandsExecutor;
  private final ObjectIdFactory objectIdFactory;
  private final MongodMetrics metrics;

  public WriteMongodTransactionImpl(Function<Class<?>, Logger> loggerFactory,
      WriteDocTransaction torodTransaction,
      CommandClassifier commandClassifier, 
      ObjectIdFactory objectIdFactory,
      MongodMetrics metrics) {
    super(loggerFactory);
    this.torodTransaction = torodTransaction;
    this.commandsExecutor = commandClassifier.getWriteCommandsExecutor();
    this.objectIdFactory = objectIdFactory;
    this.metrics = metrics;
  }

  @Override
  public WriteDocTransaction getDocTransaction() {
    return torodTransaction;
  }

  @Override
  public ObjectIdFactory getObjectIdFactory() {
    return objectIdFactory;
  }

  @Override
  public MongodMetrics getMetrics() {
    return metrics;
  }

  @Override
  protected <A, R> Status<R> executeProtected(Request req,
      Command<? super A, ? super R> command, A arg) {
    return commandsExecutor.execute(req, command, arg, this);
  }

  @Override
  public void commit() throws RollbackException, UserException {
    torodTransaction.commit();
  }

}
