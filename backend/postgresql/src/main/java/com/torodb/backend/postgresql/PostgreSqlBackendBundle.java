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

package com.torodb.backend.postgresql;

import com.google.inject.Injector;
import com.torodb.backend.AbstractBackendBundle;
import com.torodb.backend.BackendConfig;
import com.torodb.backend.DbBackendService;
import com.torodb.backend.postgresql.guice.PostgreSqlBackendModule;
import com.torodb.core.backend.BackendBundle;
import com.torodb.core.backend.BackendExtInt;
import com.torodb.core.backend.BackendService;
import com.torodb.core.d2r.IdentifierFactory;
import com.torodb.core.d2r.ReservedIdGenerator;

/**
 * A {@link BackendBundle} that uses PostgresSql.
 */
public class PostgreSqlBackendBundle extends AbstractBackendBundle {

  private final DbBackendService lowLevelService;
  private final BackendService backendService;
  private final ReservedIdGenerator reservedIdGenerator;
  private final IdentifierFactory identifierFactory;

  @SuppressWarnings("checkstyle:JavadocMethod")
  public PostgreSqlBackendBundle(BackendConfig config) {
    super(config);
    Injector injector = createInjector(config);
    this.lowLevelService = injector.getInstance(DbBackendService.class);
    this.backendService = injector.getInstance(BackendService.class);
    this.reservedIdGenerator = injector.getInstance(ReservedIdGenerator.class);
    this.identifierFactory = injector.getInstance(IdentifierFactory.class);
  }

  protected Injector createInjector(BackendConfig config) {
    return config.getEssentialInjector().createChildInjector(
        new PostgreSqlBackendModule(config));
  }

  @Override
  protected DbBackendService getLowLevelService() {
    return lowLevelService;
  }

  @Override
  protected BackendService getBackendService() {
    return backendService;
  }

  @Override
  public BackendExtInt getExternalInterface() {
    return new BackendExtInt() {
      @Override
      public BackendService getBackendService() {
        return backendService;
      }

      @Override
      public ReservedIdGenerator getReservedIdGenerator() {
        return reservedIdGenerator;
      }

      @Override
      public IdentifierFactory getIdentifierFactory() {
        return identifierFactory;
      }
    };
  }

}
