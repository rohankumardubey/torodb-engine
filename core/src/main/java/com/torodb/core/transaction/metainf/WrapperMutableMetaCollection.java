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

import com.google.common.collect.Maps;
import com.torodb.core.TableRef;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 *
 */
public class WrapperMutableMetaCollection implements MutableMetaCollection {

  private final ImmutableMetaCollection wrapped;
  private final HashMap<TableRef, WrapperMutableMetaDocPart> newDocParts;
  private final Set<WrapperMutableMetaDocPart> modifiedMetaDocParts;
  private final HashMap<String, Tuple2<MutableMetaIndex, MetaElementState>> indexesByName;
  private final Consumer<WrapperMutableMetaCollection> changeConsumer;
  private final Map<String, Tuple2<MutableMetaIndex, MetaElementState>> aliveIndexesMap;

  @SuppressWarnings("checkstyle:WhitespaceAround")
  public WrapperMutableMetaCollection(ImmutableMetaCollection wrappedCollection) {
    this(wrappedCollection, (ignored) -> {});
  }

  public WrapperMutableMetaCollection(ImmutableMetaCollection wrappedCollection,
      Consumer<WrapperMutableMetaCollection> changeConsumer) {
    this.wrapped = wrappedCollection;
    this.changeConsumer = changeConsumer;

    this.newDocParts = new HashMap<>();

    modifiedMetaDocParts = new HashSet<>();

    wrappedCollection.streamContainedMetaDocParts().forEach((docPart) -> {
      WrapperMutableMetaDocPart mutable = createMetaDocPart(docPart);
      newDocParts.put(mutable.getTableRef(), mutable);
    });

    this.indexesByName = new HashMap<>();

    wrappedCollection.streamContainedMetaIndexes().forEach((index) -> {
      WrapperMutableMetaIndex mutable = createMetaIndex(index);
      indexesByName.put(mutable.getName(), new Tuple2<>(mutable, MetaElementState.NOT_CHANGED));
    });
    aliveIndexesMap = Maps.filterValues(indexesByName, tuple -> tuple.v2().isAlive());
  }

  protected WrapperMutableMetaDocPart createMetaDocPart(ImmutableMetaDocPart immutable) {
    return new WrapperMutableMetaDocPart(immutable, this::onDocPartChange);
  }

  protected WrapperMutableMetaIndex createMetaIndex(ImmutableMetaIndex immutable) {
    return new WrapperMutableMetaIndex(immutable, this::onIndexChange);
  }

  @Override
  public ImmutableMetaCollection getOrigin() {
    return wrapped;
  }

  @Override
  public WrapperMutableMetaDocPart addMetaDocPart(TableRef tableRef, String tableId) throws
      IllegalArgumentException {
    if (getMetaDocPartByTableRef(tableRef) != null) {
      throw new IllegalArgumentException("There is another doc part whose table ref is "
          + tableRef);
    }

    assert getMetaDocPartByIdentifier(tableId) == null : "There is another doc part whose id is "
        + tableRef;

    WrapperMutableMetaDocPart result = createMetaDocPart(
        new ImmutableMetaDocPart(tableRef, tableId));

    newDocParts.put(tableRef, result);
    onDocPartChange(result);

    return result;
  }

  @Override
  public Stream<? extends WrapperMutableMetaDocPart> streamModifiedMetaDocParts() {
    return modifiedMetaDocParts.stream();
  }

  @Override
  public MutableMetaIndex addMetaIndex(String name, boolean unique)
      throws IllegalArgumentException {
    if (getMetaIndexByName(name) != null) {
      throw new IllegalArgumentException("There is another index whose name is " + name);
    }

    WrapperMutableMetaIndex result = createMetaIndex(
        new ImmutableMetaIndex(name, unique));

    indexesByName.put(name, new Tuple2<>(result, MetaElementState.ADDED));
    changeConsumer.accept(this);

    return result;
  }

  @Override
  public boolean removeMetaIndexByName(String indexName) {
    WrapperMutableMetaIndex metaIndex = getMetaIndexByName(indexName);
    if (metaIndex == null) {
      return false;
    }

    indexesByName.put(metaIndex.getName(), new Tuple2<>(metaIndex, MetaElementState.REMOVED));
    changeConsumer.accept(this);
    return true;
  }

  @Override
  public Stream<ChangedElement<MutableMetaIndex>> streamModifiedMetaIndexes() {
    return indexesByName.values().stream()
        .filter(tuple -> tuple.v2().hasChanged())
        .map(PojoChangedElement::new);
  }

  @Override
  public ImmutableMetaCollection immutableCopy() {
    if (modifiedMetaDocParts.isEmpty() && indexesByName.values().stream().noneMatch(tuple -> tuple
        .v2().hasChanged())) {
      return wrapped;
    } else {
      ImmutableMetaCollection.Builder builder = new ImmutableMetaCollection.Builder(wrapped);
      for (MutableMetaDocPart modifiedMetaDocPart : modifiedMetaDocParts) {
        builder.put(modifiedMetaDocPart.immutableCopy());
      }

      indexesByName.values()
          .forEach(tuple -> {
            switch (tuple.v2()) {
              case ADDED:
              case MODIFIED:
              case NOT_CHANGED:
                builder.put(tuple.v1().immutableCopy());
                break;
              case REMOVED:
                builder.remove(tuple.v1());
                break;
              case NOT_EXISTENT:
              default:
                throw new AssertionError("Unexpected case" + tuple.v2());
            }
          });
      return builder.build();
    }
  }

  @Override
  public String getName() {
    return wrapped.getName();
  }

  @Override
  public String getIdentifier() {
    return wrapped.getIdentifier();
  }

  @Override
  public Stream<? extends WrapperMutableMetaDocPart> streamContainedMetaDocParts() {
    return newDocParts.values().stream();
  }

  @Override
  public WrapperMutableMetaDocPart getMetaDocPartByIdentifier(String docPartId) {
    Optional<WrapperMutableMetaDocPart> newDocPart = newDocParts.values().stream()
        .filter((docPart) -> docPart.getIdentifier().equals(docPartId))
        .findAny();

    return newDocPart.orElse(null);
  }

  @Override
  public WrapperMutableMetaDocPart getMetaDocPartByTableRef(TableRef tableRef) {
    return newDocParts.get(tableRef);
  }

  @Override
  public Stream<? extends WrapperMutableMetaIndex> streamContainedMetaIndexes() {
    return aliveIndexesMap.values().stream().map(tuple -> (WrapperMutableMetaIndex) tuple.v1());
  }

  @Override
  public WrapperMutableMetaIndex getMetaIndexByName(String indexName) {
    Tuple2<MutableMetaIndex, MetaElementState> tuple = aliveIndexesMap.get(indexName);
    if (tuple == null) {
      return null;
    }
    return (WrapperMutableMetaIndex) tuple.v1();
  }

  @Override
  public List<Tuple2<MetaIndex, List<String>>> getMissingIndexesForNewField(
      MutableMetaDocPart docPart,
      MetaField newField) {
    return wrapped.getMissingIndexesForNewField(streamContainedMetaIndexes(), docPart, newField);
  }

  @Override
  public Optional<ImmutableMetaDocPart> getAnyDocPartWithMissedDocPartIndex(
      ImmutableMetaCollection oldStructure, MutableMetaIndex newIndex) {
    return newIndex.streamTableRefs()
        .map(tableRef -> oldStructure.getMetaDocPartByTableRef(tableRef))
        .filter(docPart -> docPart != null && newIndex.isCompatible(docPart) && Seq.seq(newIndex
            .iteratorMetaDocPartIndexesIdentifiers(docPart))
            .filter(identifiers -> docPart.streamIndexes()
                .noneMatch(docPartIndex -> newIndex.isMatch(docPart, identifiers, docPartIndex)))
            .anyMatch(identifiers -> {
              MutableMetaDocPart newDocPart = getMetaDocPartByTableRef(docPart.getTableRef());
              return Seq.seq(newDocPart.streamModifiedMetaDocPartIndexes())
                  .filter(docPartIndex -> docPartIndex.getChange() != MetaElementState.REMOVED)
                  .noneMatch(docPartIndex -> newIndex.isMatch(newDocPart, identifiers,
                      docPartIndex.getElement()));
            }))
        .findAny();
  }

  @Override
  public Optional<? extends MetaIdentifiedDocPartIndex> getAnyOrphanDocPartIndex(
      ImmutableMetaCollection oldStructure, MutableMetaIndex newRemovedIndex) {
    return newRemovedIndex.streamTableRefs()
        .map(tableRef -> (MetaDocPart) oldStructure.getMetaDocPartByTableRef(tableRef))
        .filter(docPart -> docPart != null && newRemovedIndex.isCompatible(docPart))
        .flatMap(oldDocPart -> oldDocPart.streamIndexes()
            .filter(oldDocPartIndex -> newRemovedIndex.isCompatible(oldDocPart, oldDocPartIndex)
                && Seq.seq(getMetaDocPartByTableRef(oldDocPart.getTableRef())
                    .streamModifiedMetaDocPartIndexes())
                    .noneMatch(newDocPartIndex ->
                        newDocPartIndex.getChange() == MetaElementState.REMOVED
                        && newDocPartIndex.getElement().getIdentifier().equals(oldDocPartIndex
                            .getIdentifier())) && oldStructure.streamContainedMetaIndexes()
                .noneMatch(oldIndex -> oldIndex.isCompatible(oldDocPart, oldDocPartIndex) && Seq
                    .seq(streamModifiedMetaIndexes())
                    .noneMatch(newIndex -> newIndex.getChange() == MetaElementState.REMOVED
                        && newIndex.getElement().getName().equals(oldIndex.getName()))
                )
            )
        )
        .findAny();
  }

  @Override
  public String toString() {
    return defautToString();
  }

  protected void onDocPartChange(WrapperMutableMetaDocPart changedDocPart) {
    modifiedMetaDocParts.add(changedDocPart);
    changeConsumer.accept(this);
  }

  private boolean isTransitionAllowed(MetaIndex metaIndex, MetaElementState newState) {
    MetaElementState oldState;
    Tuple2<MutableMetaIndex, MetaElementState> tuple = indexesByName.get(metaIndex.getName());

    if (tuple == null) {
      oldState = MetaElementState.NOT_EXISTENT;
    } else {
      oldState = tuple.v2();
    }

    oldState.assertLegalTransition(newState);
    return true;
  }

  protected void onIndexChange(WrapperMutableMetaIndex changedIndex) {
    assert isTransitionAllowed(changedIndex, MetaElementState.MODIFIED);

    indexesByName.put(
        changedIndex.getName(),
        new Tuple2<>(changedIndex, MetaElementState.MODIFIED)
    );
    changeConsumer.accept(this);
  }

}
