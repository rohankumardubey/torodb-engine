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

import com.torodb.core.TableRef;
import com.torodb.core.transaction.metainf.MetaSnapshot;

public interface ReservedIdGenerator {

  /**
   * Updates the id information with the last information found on the database.
   *
   * @param snapshot the knowledge the system has about the metainformation
   */
  void load(MetaSnapshot snapshot);

  int nextRid(String dbName, String collectionName, TableRef tableRef);

  void setNextRid(String dbName, String collectionName, TableRef tableRef, int nextRid);

  DocPartRidGenerator getDocPartRidGenerator(String dbName, String collectionName);

  public interface DocPartRidGenerator {

    int nextRid(TableRef tableRef);

    void setNextRid(TableRef tableRef, int nextRid);

  }

}
