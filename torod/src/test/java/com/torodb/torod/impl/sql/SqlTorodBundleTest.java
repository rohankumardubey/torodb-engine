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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.util.concurrent.Service;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.torodb.backend.derby.DerbyDbBackendBundle;
import com.torodb.backend.derby.driver.DerbyDbBackendConfigBuilder;
import com.torodb.core.backend.BackendBundle;
import com.torodb.core.bundle.BundleConfig;
import com.torodb.core.bundle.BundleConfigImpl;
import com.torodb.core.guice.EssentialModule;
import com.torodb.core.logging.DefaultLoggerFactory;
import com.torodb.core.supervision.Supervisor;
import com.torodb.core.supervision.SupervisorDecision;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;

public class SqlTorodBundleTest {

  private SqlTorodBundle torodBundle;
  private BackendBundle backendBundle;

  @Before
  public void setUp() {
    Supervisor supervisor = new Supervisor() {
      @Override
      public SupervisorDecision onError(Object supervised, Throwable error) {
        throw new AssertionError("error on " + supervised, error);
      }
    };
    Injector essentialInjector = Guice.createInjector(
        new EssentialModule(
            DefaultLoggerFactory.getInstance(),
            () -> true,
            Clock.systemUTC()
        )
    );

    BundleConfig generalConfig = new BundleConfigImpl(essentialInjector, supervisor);
    BackendBundle backendBundle = new DerbyDbBackendBundle(
        new DerbyDbBackendConfigBuilder(generalConfig)
        .setInMemory(true)
        .setEmbedded(true)
        .build()
    );

    backendBundle.startAsync();
    backendBundle.awaitRunning();

    torodBundle = new SqlTorodBundle(new SqlTorodConfig(
        backendBundle,
        essentialInjector,
        supervisor)
    );
  }

  @After
  public void tearDown() {
    if (backendBundle != null) {
      backendBundle.stopAsync();
    }
  }

  @Test
  public void testStartAndStop() {
    torodBundle.start()
        .thenCompose((Object ignore) -> torodBundle.stop())
        .join();
    assertThat(torodBundle.state(), is(Service.State.TERMINATED));
  }

}
