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
package com.torodb.backend.derby;

import com.google.inject.PrivateModule;
import com.torodb.backend.BackendConfig;
import com.torodb.backend.BackendConfigImplBuilder;
import com.torodb.backend.derby.driver.DerbyDbBackendConfig;
import com.torodb.backend.derby.guice.DerbyBackendModule;
import com.torodb.backend.tests.common.BackendTestContext;
import com.torodb.backend.tests.common.BackendTestContextFactory;
import com.torodb.backend.tests.common.IntegrationTestBundleConfig;

public class DerbyTestContextFactory extends BackendTestContextFactory {

  @Override
  public BackendTestContext<?> get() {
    DerbyDbBackendConfig config = 
        new DerbyDbBackendConfig(true, true, new BackendConfigImplBuilder(new IntegrationTestBundleConfig())
          .build());
    return new DerbyTestContext(new DerbyBackendTestBundle(config) {
        @Override
        public PrivateModule getBackendModule(BackendConfig config) {
          return new DerbyBackendTestModule((DerbyDbBackendConfig) config);
        }
    });
  }

  private class DerbyBackendTestModule extends DerbyBackendModule {
    public DerbyBackendTestModule(DerbyDbBackendConfig config) {
      super(config);
    }

    @Override
    protected void configure() {
      super.configure();
      
      exposeTestInstances(clazz -> expose(clazz));
    }
  }

}
