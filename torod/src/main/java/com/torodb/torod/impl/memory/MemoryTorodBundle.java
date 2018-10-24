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

package com.torodb.torod.impl.memory;

import com.google.common.util.concurrent.Service;
import com.google.inject.Injector;
import com.torodb.core.bundle.BundleConfig;
import com.torodb.torod.AbstractTorodBundle;
import com.torodb.torod.TorodServer;
import com.torodb.torod.concurrency.ConcurrentTorodModule;

import java.util.Collection;
import java.util.Collections;

/**
 * A {@link com.torodb.torod.TorodBundle torod bundle} that uses a memory backend, intended to be
 * used on testing.
 */
public class MemoryTorodBundle extends AbstractTorodBundle {

  private final TorodServer torodServer;

  public MemoryTorodBundle(BundleConfig config) {
    super(config);
    Injector injector = config
            .getEssentialInjector()
            .createChildInjector(new MemoryTorodModule(), new ConcurrentTorodModule());
    this.torodServer = injector.getInstance(TorodServer.class);
  }

  @Override
  public TorodServer getTorodServer() {
    return torodServer;
  }

  @Override
  public Collection<Service> getDependencies() {
    return Collections.emptyList();
  }
}
