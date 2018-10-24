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

package com.torodb.core.transaction.metainf;

import java.util.Iterator;
import java.util.stream.Stream;

/**
 *
 */
public interface MutableMetaDocPartIndex extends MetaDocPartIndex {

  @Override
  public ImmutableMetaDocPartIndexColumn getMetaDocPartIndexColumnByIdentifier(String columnName);

  @Override
  public Iterator<? extends ImmutableMetaDocPartIndexColumn> iteratorColumns();

  /**
   * Put a new column to this index at specified position.
   *
   * @param position
   * @param identifier
   * @param ordering
   * @return the new column
   * @throws IllegalArgumentException if this index already contains a column with the same
   *                                  {@link MetaDocPartIndexColumn#getPosition() position} or
   *                                  {@link MetaDocPartIndexColumn#getName() identifier}.
   */
  public abstract ImmutableMetaDocPartIndexColumn putMetaDocPartIndexColumn(int position,
      String identifier, FieldIndexOrdering ordering) throws IllegalArgumentException;

  /**
   * Adds a new column to this index at next free position.
   *
   * @param identifier
   * @param ordering
   * @return the new column
   * @throws IllegalArgumentException if this index already contains a column with the same
   *                                  {@link MetaDocPartIndexColumn#getName() identifier}.
   */
  public abstract ImmutableMetaDocPartIndexColumn addMetaDocPartIndexColumn(String identifier,
      FieldIndexOrdering ordering) throws IllegalArgumentException;

  @SuppressWarnings("checkstyle:LineLength")
  public Stream<? extends ImmutableMetaDocPartIndexColumn> streamAddedMetaDocPartIndexColumns();
}
