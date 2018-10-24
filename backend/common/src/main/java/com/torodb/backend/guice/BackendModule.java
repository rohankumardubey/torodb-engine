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

package com.torodb.backend.guice;

import com.google.inject.PrivateModule;
import com.google.inject.Singleton;
import com.torodb.backend.DslContextFactory;
import com.torodb.backend.DslContextFactoryImpl;
import com.torodb.backend.SqlHelper;
import com.torodb.backend.SqlInterface;
import com.torodb.backend.SqlInterfaceDelegate;
import com.torodb.backend.ddl.DdlOps;
import com.torodb.backend.rid.ReservedIdGeneratorImpl;
import com.torodb.backend.rid.ReservedIdInfoFactory;
import com.torodb.backend.rid.ReservedIdInfoFactoryImpl;
import com.torodb.backend.service.BackendServiceModule;
import com.torodb.backend.service.KvMetainfoHandler;
import com.torodb.core.backend.BackendService;
import com.torodb.core.d2r.ReservedIdGenerator;

public class BackendModule extends PrivateModule {

  @Override
  protected void configure() {
    requireBinding(DdlOps.class);

    bind(SqlInterfaceDelegate.class)
        .in(Singleton.class);
    bind(SqlInterface.class)
        .to(SqlInterfaceDelegate.class);
    expose(SqlInterface.class);

    bind(ReservedIdInfoFactoryImpl.class)
        .in(Singleton.class);
    bind(ReservedIdInfoFactory.class)
        .to(ReservedIdInfoFactoryImpl.class);

    bind(ReservedIdGeneratorImpl.class)
        .in(Singleton.class);
    bind(ReservedIdGenerator.class)
        .to(ReservedIdGeneratorImpl.class);
    expose(ReservedIdGenerator.class);

    bind(DslContextFactoryImpl.class)
        .in(Singleton.class);
    bind(DslContextFactory.class)
        .to(DslContextFactoryImpl.class);
    expose(DslContextFactory.class);

    bind(SqlHelper.class)
        .in(Singleton.class);
    expose(SqlHelper.class);

    bind(KvMetainfoHandler.class);

    install(new BackendServiceModule());
    expose(BackendService.class);
  }

}
