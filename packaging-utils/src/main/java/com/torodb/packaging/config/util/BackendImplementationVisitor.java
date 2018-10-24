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

package com.torodb.packaging.config.util;

import com.torodb.packaging.config.model.backend.BackendImplementation;
import com.torodb.packaging.config.model.backend.derby.AbstractDerby;
import com.torodb.packaging.config.model.backend.mysql.AbstractMySql;
import com.torodb.packaging.config.model.backend.postgres.AbstractPostgres;

/**
 * A visitor that visits {@link BackendImplementation backend implementations}.
 * @param <R> The result of applying the visitor.
 * @param <A> The argument used on application.
 */
public interface BackendImplementationVisitor<R, A> {

  public R visit(AbstractPostgres value, A arg);

  public R visit(AbstractDerby value, A arg);

  public R visit(AbstractMySql value, A arg);

}
