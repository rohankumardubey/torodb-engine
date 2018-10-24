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

package com.torodb.core.d2r;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.torodb.core.TableRef;
import com.torodb.core.transaction.metainf.MetaSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MemoryRidGenerator implements ReservedIdGenerator {

  private Table<String, String, DocPartRidGenerator> generators = HashBasedTable.create();

  @Override
  public synchronized void load(MetaSnapshot snapshot) {
    generators = HashBasedTable.create();
  }

  @Override
  public int nextRid(String dbName, String collectionName, TableRef tableRef) {
    return getDocPartRidGenerator(dbName, collectionName).nextRid(tableRef);
  }

  @Override
  public void setNextRid(String dbName, String collectionName, TableRef tableRef, int nextRid) {
    getDocPartRidGenerator(dbName, collectionName).setNextRid(tableRef, nextRid);
  }

  @Override
  public synchronized DocPartRidGenerator getDocPartRidGenerator(
      String dbName, String collectionName) {
    DocPartRidGenerator map = generators.get(dbName, collectionName);
    if (map == null) {
      map = new CollectionRidGeneratorMemory();
      generators.put(dbName, collectionName, map);
    }
    return map;
  }

  public static class CollectionRidGeneratorMemory implements DocPartRidGenerator {

    private Map<TableRef, AtomicInteger> map = new HashMap<TableRef, AtomicInteger>();

    @Override
    public int nextRid(TableRef tableRef) {
      AtomicInteger rid = map.computeIfAbsent(tableRef, tr -> new AtomicInteger(0));
      return rid.getAndIncrement();
    }

    @Override
    public void setNextRid(TableRef tableRef, int nextRid) {
      AtomicInteger rid = map.computeIfAbsent(tableRef, tr -> new AtomicInteger(0));
      rid.set(nextRid);
    }

  }

}
