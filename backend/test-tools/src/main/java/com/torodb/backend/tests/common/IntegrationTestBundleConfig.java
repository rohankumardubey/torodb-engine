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

package com.torodb.backend.tests.common;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.torodb.core.bundle.BundleConfig;
import com.torodb.core.guice.EssentialModule;
import com.torodb.core.logging.DefaultLoggerFactory;
import com.torodb.core.supervision.Supervisor;
import com.torodb.core.supervision.SupervisorDecision;

import java.time.Clock;

public class IntegrationTestBundleConfig implements BundleConfig {

  private final Supervisor supervisor = new TestSupervisor();

  private final Injector essentialInjector = Guice.createInjector(
      Stage.PRODUCTION, new EssentialModule(
          DefaultLoggerFactory.getInstance(),
          () -> true,
          Clock.systemUTC()
      ));

  @Override
  public Injector getEssentialInjector() {
    return essentialInjector;
  }

  @Override
  public Supervisor getSupervisor() {
    return supervisor;
  }

  private static class TestSupervisor implements Supervisor {

    @Override
    public SupervisorDecision onError(Object supervised, Throwable error) {
      throw new AssertionError("Error on " + supervised, error);
    }

  }

}
