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

package com.torodb.backend.derby.guice;

import com.google.inject.PrivateModule;
import com.torodb.backend.BackendConfig;
import com.torodb.backend.DataTypeProvider;
import com.torodb.backend.DbBackendService;
import com.torodb.backend.ErrorHandler;
import com.torodb.backend.MetaDataReadInterface;
import com.torodb.backend.MetaDataWriteInterface;
import com.torodb.backend.ReadInterface;
import com.torodb.backend.StructureInterface;
import com.torodb.backend.WriteInterface;
import com.torodb.backend.ddl.DdlOpsModule;
import com.torodb.backend.ddl.DefaultReadStructure;
import com.torodb.backend.derby.DerbyDataTypeProvider;
import com.torodb.backend.derby.DerbyDbBackend;
import com.torodb.backend.derby.DerbyErrorHandler;
import com.torodb.backend.derby.DerbyIdentifierConstraints;
import com.torodb.backend.derby.DerbyMetaDataReadInterface;
import com.torodb.backend.derby.DerbyMetaDataWriteInterface;
import com.torodb.backend.derby.DerbyReadInterface;
import com.torodb.backend.derby.DerbyStructureInterface;
import com.torodb.backend.derby.DerbyWriteInterface;
import com.torodb.backend.derby.driver.DerbyDbBackendConfig;
import com.torodb.backend.derby.driver.DerbyDriverProvider;
import com.torodb.backend.derby.driver.OfficialDerbyDriver;
import com.torodb.backend.derby.schema.DerbySchemaUpdater;
import com.torodb.backend.guice.BackendModule;
import com.torodb.backend.meta.SchemaUpdater;
import com.torodb.core.backend.BackendService;
import com.torodb.core.backend.IdentifierConstraints;
import com.torodb.core.d2r.DefaultIdentifierFactory;
import com.torodb.core.d2r.IdentifierFactory;
import com.torodb.core.d2r.ReservedIdGenerator;
import com.torodb.core.d2r.UniqueIdentifierGenerator;
import com.torodb.core.guice.EssentialToDefaultModule;

import javax.inject.Singleton;

public class DerbyBackendModule extends PrivateModule {

  private final DerbyDbBackendConfig config;

  public DerbyBackendModule(DerbyDbBackendConfig config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    expose(BackendService.class);
    expose(ReservedIdGenerator.class);

    install(new DdlOpsModule());
    install(new BackendModule());

    install(new EssentialToDefaultModule());

    bind(BackendConfig.class)
        .toInstance(config);
    bind(DerbyDbBackendConfig.class)
        .toInstance(config);

    bind(OfficialDerbyDriver.class)
        .in(Singleton.class);
    bind(DerbyDriverProvider.class)
        .to(OfficialDerbyDriver.class);

    bind(DerbyDbBackend.class)
        .in(Singleton.class);
    bind(DbBackendService.class)
        .to(DerbyDbBackend.class);
    expose(DbBackendService.class);

    bind(DefaultReadStructure.class);
    expose(DefaultReadStructure.class);

    bind(DerbySchemaUpdater.class)
        .in(Singleton.class);
    bind(SchemaUpdater.class)
        .to(DerbySchemaUpdater.class);
    expose(SchemaUpdater.class);

    bind(DerbyMetaDataReadInterface.class)
        .in(Singleton.class);
    bind(MetaDataReadInterface.class)
        .to(DerbyMetaDataReadInterface.class);
    expose(MetaDataReadInterface.class);

    bind(DerbyMetaDataWriteInterface.class)
        .in(Singleton.class);
    bind(MetaDataWriteInterface.class)
        .to(DerbyMetaDataWriteInterface.class);
    expose(MetaDataWriteInterface.class);

    bind(DerbyDataTypeProvider.class)
        .in(Singleton.class);
    bind(DataTypeProvider.class)
        .to(DerbyDataTypeProvider.class);
    expose(DataTypeProvider.class);

    bind(DerbyStructureInterface.class)
        .in(Singleton.class);
    bind(StructureInterface.class)
        .to(DerbyStructureInterface.class);
    expose(StructureInterface.class);

    bind(DerbyReadInterface.class)
        .in(Singleton.class);
    bind(ReadInterface.class)
        .to(DerbyReadInterface.class);
    expose(ReadInterface.class);

    bind(DerbyWriteInterface.class)
        .in(Singleton.class);
    bind(WriteInterface.class)
        .to(DerbyWriteInterface.class);
    expose(WriteInterface.class);

    bind(DerbyErrorHandler.class)
        .in(Singleton.class);
    bind(ErrorHandler.class)
        .to(DerbyErrorHandler.class);
    expose(ErrorHandler.class);

    bind(DerbyIdentifierConstraints.class)
        .in(Singleton.class);
    bind(IdentifierConstraints.class)
        .to(DerbyIdentifierConstraints.class);
    expose(IdentifierConstraints.class);

    bind(UniqueIdentifierGenerator.class)
        .in(Singleton.class);
    bind(DefaultIdentifierFactory.class)
        .in(Singleton.class);

    bind(IdentifierFactory.class)
        .to(DefaultIdentifierFactory.class);
    expose(IdentifierFactory.class);
  }

}
