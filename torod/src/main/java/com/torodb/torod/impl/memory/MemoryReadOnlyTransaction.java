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

import com.torodb.torod.impl.memory.MemoryData.MdTransaction;

/**
 *
 */
class MemoryReadOnlyTransaction extends MemoryTransaction {

  private final MemoryData.MdReadTransaction trans;

  public MemoryReadOnlyTransaction(MemoryTorodServer server) {
    trans = server.getData().openReadTransaction();
  }

  @Override
  public void rollback() {
  }

  @Override
  protected MdTransaction getTransaction() {
    return trans;
  }

}
