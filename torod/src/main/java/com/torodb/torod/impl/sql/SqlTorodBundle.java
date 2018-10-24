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

package com.torodb.torod.impl.sql;

import com.google.common.util.concurrent.Service;
import com.google.inject.Injector;
import com.torodb.core.backend.BackendBundle;
import com.torodb.torod.AbstractTorodBundle;
import com.torodb.torod.TorodLoggerFactory;
import com.torodb.torod.TorodServer;
import com.torodb.torod.concurrency.ConcurrentTorodModule;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Collections;

/**
 * A {@link TorodBundle torod bundle} that uses a SQL backend.
 */
public class SqlTorodBundle extends AbstractTorodBundle {
  private static final Logger LOGGER = TorodLoggerFactory.get(SqlTorodBundle.class);
  private final TorodServer torodServer;
  private final BackendBundle backendBundle;

  public SqlTorodBundle(SqlTorodConfig config) {
    super(config);
    Injector injector = config.getEssentialInjector().createChildInjector(
        new SqlTorodModule(config),
        new ConcurrentTorodModule()
    );
    this.torodServer = injector.getInstance(TorodServer.class);
    this.backendBundle = config.getBackendBundle();
  }

  @Override
  protected TorodServer getTorodServer() {
    return torodServer;
  }
  
  @Override
  public Collection<Service> getDependencies() {
    return Collections.singleton(backendBundle);
  }
}