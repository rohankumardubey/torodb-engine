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
import com.torodb.kvdocument.types.JavascriptType;
import com.torodb.kvdocument.types.KvType;
import com.torodb.kvdocument.values.KvMongoJavascript;

import java.sql.Types;

/** */
public class MongoJavascriptValueConverter
    implements KvValueConverter<String, String, KvMongoJavascript> {

  private static final long serialVersionUID = 1L;

  public static final DataTypeForKv<KvMongoJavascript> TYPE =
      DataTypeForKv.from(StringValueConverter.TEXT, new MongoJavascriptValueConverter(), 
          Types.LONGVARCHAR);

  @Override
  public KvType getErasuredType() {
    return JavascriptType.INSTANCE;
  }

  @Override
  public KvMongoJavascript from(String databaseObject) {
    return KvMongoJavascript.of(databaseObject);
  }

  @Override
  public String to(KvMongoJavascript userObject) {
    return userObject.getValue();
  }

  @Override
  public Class<String> fromType() {
    return String.class;
  }

  @Override
  public Class<KvMongoJavascript> toType() {
    return KvMongoJavascript.class;
  }

  @Override
  public SqlBinding<String> getSqlBinding() {
    return StringSqlBinding.INSTANCE;
  }
}
