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

import com.torodb.backend.converters.TableRefConverter;
import com.torodb.backend.mysql.tables.MySqlMetaIndexFieldTable;
import com.torodb.backend.tables.records.MetaIndexFieldRecord;
import com.torodb.core.TableRef;
import com.torodb.core.TableRefFactory;
import com.torodb.core.transaction.metainf.FieldIndexOrdering;

import java.io.StringReader;

import javax.json.Json;
import javax.json.JsonReader;

public class MySqlMetaIndexFieldRecord extends MetaIndexFieldRecord<String> {

  private static final long serialVersionUID = 8338839384022880385L;

  /**
   * Create a detached MetaFieldRecord
   */
  public MySqlMetaIndexFieldRecord() {
    super(MySqlMetaIndexFieldTable.INDEX_FIELD);
  }

  /**
   * Create a detached, initialised MetaFieldRecord
   */
  public MySqlMetaIndexFieldRecord(String database, String collection, String index,
      Integer position, String tableRef, String name, FieldIndexOrdering fieldIndexOrdering) {
    super(MySqlMetaIndexFieldTable.INDEX_FIELD);

    values(database, collection, index, position, tableRef, name, fieldIndexOrdering);
  }

  @Override
  public MetaIndexFieldRecord<String> values(String database, String collection, String index,
      Integer position, String tableRef, String name, FieldIndexOrdering fieldIndexOrdering) {
    setDatabase(database);
    setCollection(collection);
    setIndex(index);
    setPosition(position);
    setTableRef(tableRef);
    setName(name);
    setOrdering(fieldIndexOrdering);
    return this;
  }

  @Override
  protected String toTableRefType(TableRef tableRef) {
    return TableRefConverter.toJsonArray(tableRef).toString();
  }

  @Override
  public TableRef getTableRefValue(TableRefFactory tableRefFactory) {
    final JsonReader reader = Json.createReader(new StringReader(getTableRef()));
    return TableRefConverter.fromJsonArray(tableRefFactory, reader.readArray());
  }
}
