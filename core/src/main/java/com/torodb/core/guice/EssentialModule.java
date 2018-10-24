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

package com.torodb.core.guice;

import com.google.inject.AbstractModule;
import com.torodb.core.bundle.BundleConfig;
import com.torodb.core.concurrent.ConcurrentModule;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.core.metrics.MetricsConfig;
import com.torodb.core.metrics.MetricsModule;

import java.time.Clock;

/**
 * A module that installs all core and utility modules required to generate the
 * {@link BundleConfig#getEssentialInjector() essential injector}.
 */
public class EssentialModule extends AbstractModule {

  private final LoggerFactory lifecycleLoggerFactory;
  private final MetricsConfig metricsConfig;
  private final Clock clock;

  public EssentialModule(LoggerFactory lifecycleLoggerFactory, MetricsConfig metricsConfig,
      Clock clock) {
    this.lifecycleLoggerFactory = lifecycleLoggerFactory;
    this.metricsConfig = metricsConfig;
    this.clock = clock;
  }

  @Override
  protected void configure() {
    binder().requireExplicitBindings();

    bind(Clock.class)
        .toInstance(clock);

    install(new CoreModule(lifecycleLoggerFactory));
    install(new ExecutorServicesModule());
    install(new ConcurrentModule(lifecycleLoggerFactory));
    install(new MetricsModule(metricsConfig));
  }

}
