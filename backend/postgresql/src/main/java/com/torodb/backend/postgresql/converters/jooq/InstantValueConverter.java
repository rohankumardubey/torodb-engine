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

package com.torodb.backend.postgresql.converters.jooq;

import com.torodb.backend.converters.jooq.DataTypeForKv;
import com.torodb.backend.converters.jooq.KvValueConverter;
import com.torodb.backend.converters.sql.SqlBinding;
import com.torodb.backend.converters.sql.TimestampSqlBinding;
import com.torodb.kvdocument.types.InstantType;
import com.torodb.kvdocument.types.KvType;
import com.torodb.kvdocument.values.KvInstant;
import com.torodb.kvdocument.values.heap.InstantKvInstant;
import org.jooq.DataType;
import org.jooq.impl.DefaultDataType;

import java.sql.Timestamp;

/**
 *
 */
public class InstantValueConverter implements KvValueConverter<Timestamp, Timestamp, KvInstant> {

  private static final long serialVersionUID = 1L;

  private static final DataType<Timestamp> TIMESTAMPTZ = new DefaultDataType<Timestamp>(null,
      Timestamp.class, "timestamptz");

  public static final DataTypeForKv<KvInstant> TYPE = DataTypeForKv.from(TIMESTAMPTZ,
      new InstantValueConverter());

  @Override
  public KvType getErasuredType() {
    return InstantType.INSTANCE;
  }

  @Override
  public KvInstant from(Timestamp databaseObject) {
    return new InstantKvInstant(databaseObject.toInstant());
  }

  @Override
  public Timestamp to(KvInstant userObject) {
    return Timestamp.from(userObject.getValue());
  }

  @Override
  public Class<Timestamp> fromType() {
    return Timestamp.class;
  }

  @Override
  public Class<KvInstant> toType() {
    return KvInstant.class;
  }

  @Override
  public SqlBinding<Timestamp> getSqlBinding() {
    return TimestampSqlBinding.INSTANCE;
  }

}
