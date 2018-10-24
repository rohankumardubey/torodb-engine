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

package com.torodb.backend.mysql.tables.records;

import com.torodb.backend.mysql.tables.MySqlKvTable;
import com.torodb.backend.tables.records.KvRecord;

public class MySqlKvRecord extends KvRecord {

  private static final long serialVersionUID = -7220623531622958067L;

  public MySqlKvRecord() {
    super(MySqlKvTable.KV);
  }

  public MySqlKvRecord(String name, String identifier) {
    super(MySqlKvTable.KV);

    values(name, identifier);
  }

  @Override
  public MySqlKvRecord values(String key, String value) {
    setKey(key);
    setValue(value);
    return this;
  }
}
