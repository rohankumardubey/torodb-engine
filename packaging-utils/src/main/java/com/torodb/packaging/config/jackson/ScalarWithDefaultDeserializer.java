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

package com.torodb.packaging.config.jackson;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.torodb.packaging.config.model.common.ScalarWithDefault;

import java.io.IOException;
import java.lang.reflect.Constructor;

public abstract class ScalarWithDefaultDeserializer<T>
    extends JsonDeserializer<ScalarWithDefault<T>> {

  private final Class<? extends ScalarWithDefault<T>> scalarWithDefaultImplementationClass;
  private final Class<T> scalarImplementationClass;
  private final Class<?> scalarArgumentClass;
  
  public <SWC extends ScalarWithDefault<T>> ScalarWithDefaultDeserializer(
      Class<SWC> scalarWithDefaultImplementationClass,
      Class<T> scalarImplementationClass) {
    this.scalarWithDefaultImplementationClass = scalarWithDefaultImplementationClass;
    this.scalarImplementationClass = scalarImplementationClass;
    Class<?> scalarArgumentClass = scalarImplementationClass;
    while (scalarArgumentClass.getSuperclass() != Object.class) {
      scalarArgumentClass = scalarArgumentClass.getSuperclass();
    }
    this.scalarArgumentClass = scalarArgumentClass;
  }
  
  @Override
  public ScalarWithDefault<T> deserialize(JsonParser jp, DeserializationContext ctxt) 
      throws IOException, JsonProcessingException {
    try {
      Constructor<? extends ScalarWithDefault<T>> scalarWithDefaultConstructor = 
          scalarWithDefaultImplementationClass.getConstructor(
              scalarArgumentClass, boolean.class);
      
      return scalarWithDefaultConstructor.newInstance(
          jp.getCodec().readValue(jp, scalarImplementationClass), false);
    } catch (JsonParseException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new JsonParseException("Error while parsing scalar with value for scalar type " 
          + scalarImplementationClass.getSimpleName(), jp.getCurrentLocation(), exception);
    }
  }

}
