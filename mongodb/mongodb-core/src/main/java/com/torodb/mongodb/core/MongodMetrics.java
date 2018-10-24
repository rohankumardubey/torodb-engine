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

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.torodb.core.metrics.ToroMetricRegistry;
import com.torodb.mongowp.commands.Command;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

@ThreadSafe
public class MongodMetrics {

  private final ToroMetricRegistry registry;
  private final Map<Command<?, ?>, Timer> commandsTimerMap = new ConcurrentHashMap<>();
  private final Meter commands;
  private final Meter inserts;
  private final Meter deletes;
  private final Meter updateModified;
  private final Meter updateMatched;
  private final Meter updateUpserted;

  @Inject
  public MongodMetrics(ToroMetricRegistry parentRegistry) {
    this.registry = parentRegistry.createSubRegistry("Mongod");

    commands = registry.meter("commands");
    inserts = registry.meter("inserts");
    deletes = registry.meter("deletes");

    updateModified = registry.meter("updateModified");
    updateMatched = registry.meter("updateMatched");
    updateUpserted = registry.meter("updateUpserted");
  }

  public Timer getTimer(Command<?, ?> command) {
    return commandsTimerMap.computeIfAbsent(command, c -> registry.timer(
        c.getCommandName() + "Timer"));
  }

  public Meter getCommands() {
    return commands;
  }

  public Meter getInserts() {
    return inserts;
  }

  public Meter getDeletes() {
    return deletes;
  }

  public Meter getUpdateModified() {
    return updateModified;
  }

  public Meter getUpdateMatched() {
    return updateMatched;
  }

  public Meter getUpdateUpserted() {
    return updateUpserted;
  }
}
