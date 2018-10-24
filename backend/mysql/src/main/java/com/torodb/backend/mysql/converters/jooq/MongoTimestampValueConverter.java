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

package com.torodb.backend.mysql.converters.jooq;

import com.torodb.backend.converters.jooq.DataTypeForKv;
import com.torodb.backend.converters.jooq.KvValueConverter;
import com.torodb.backend.converters.sql.SqlBinding;
import com.torodb.backend.mysql.converters.sql.StringSqlBinding;
import com.torodb.kvdocument.types.KvType;
import com.torodb.kvdocument.types.MongoTimestampType;
import com.torodb.kvdocument.values.KvMongoTimestamp;
import com.torodb.kvdocument.values.heap.DefaultKvMongoTimestamp;

import java.sql.Types;

import javax.json.Json;
import javax.json.JsonObject;

/**
 *
 */
public class MongoTimestampValueConverter implements
    KvValueConverter<JsonObject, String, KvMongoTimestamp> {

  private static final long serialVersionUID = 1L;

  public static final DataTypeForKv<KvMongoTimestamp> TYPE =
      DataTypeForKv.from(JsonConverter.JSON, new MongoTimestampValueConverter(), 
          Types.LONGVARCHAR);

  @Override
  public KvType getErasuredType() {
    return MongoTimestampType.INSTANCE;
  }

  @Override
  public KvMongoTimestamp from(JsonObject databaseObject) {
    return new DefaultKvMongoTimestamp(databaseObject.getInt("secs"), 
        databaseObject.getInt("counter"));
  }

  @Override
  public JsonObject to(KvMongoTimestamp userObject) {
    return Json.createObjectBuilder()
        .add("secs", userObject.getSecondsSinceEpoch())
        .add("counter", userObject.getOrdinal())
        .build();
  }

  @Override
  public Class<JsonObject> fromType() {
    return JsonObject.class;
  }

  @Override
  public Class<KvMongoTimestamp> toType() {
    return KvMongoTimestamp.class;
  }

  @Override
  public SqlBinding<String> getSqlBinding() {
    return StringSqlBinding.INSTANCE;
  }
}
