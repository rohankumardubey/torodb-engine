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

import com.torodb.backend.tests.common.AbstractStructureIntegrationSuite;
import com.torodb.backend.tests.common.BackendTestContextFactory;
import org.junit.jupiter.api.Disabled;

public class DerbyStructureIT extends AbstractStructureIntegrationSuite {

  @Override
  protected BackendTestContextFactory getBackendTestContextFactory() {
    return new DerbyTestContextFactory();
  }

  @Override
  @Disabled
  public void shouldDeleteAll() throws Exception {
  }

  @Override
  @Disabled
  public void shouldDeleteUserData() throws Exception {
  }

  @Override
  @Disabled
  public void shouldMoveCollection() throws Exception {
  }
  
}
