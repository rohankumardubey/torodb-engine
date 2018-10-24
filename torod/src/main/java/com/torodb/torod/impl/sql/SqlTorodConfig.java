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

import com.google.inject.Injector;
import com.torodb.core.backend.BackendBundle;
import com.torodb.core.bundle.BundleConfig;
import com.torodb.core.bundle.BundleConfigImpl;
import com.torodb.core.supervision.Supervisor;


public class SqlTorodConfig extends BundleConfigImpl {
  private final BackendBundle backendBundle;

  public SqlTorodConfig(BackendBundle backendBundle, Injector essentialInjector,
      Supervisor supervisor) {
    super(essentialInjector, supervisor);
    this.backendBundle = backendBundle;
  }

  public SqlTorodConfig(BackendBundle backendBundle, BundleConfig other) {
    super(other);
    this.backendBundle = backendBundle;
  }

  public BackendBundle getBackendBundle() {
    return backendBundle;
  }
  
}
