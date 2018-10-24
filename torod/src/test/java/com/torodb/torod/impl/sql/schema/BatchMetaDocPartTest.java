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

package com.torodb.torod.impl.sql.schema;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Iterables;
import com.torodb.core.TableRefFactory;
import com.torodb.core.impl.TableRefFactoryImpl;
import com.torodb.core.transaction.metainf.FieldType;
import com.torodb.core.transaction.metainf.ImmutableMetaDocPart;
import com.torodb.core.transaction.metainf.WrapperMutableMetaDocPart;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Consumer;

/**
 *
 */
public class BatchMetaDocPartTest {

  private final TableRefFactory tableRefFactory = new TableRefFactoryImpl();
  private BatchMetaDocPart docPart;
  private WrapperMutableMetaDocPart delegate;
  private Consumer<BatchMetaDocPart> testChangeConsumer;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() throws Exception {
    testChangeConsumer = mock(Consumer.class);
    Consumer<WrapperMutableMetaDocPart> delegateChangeConsumer = mock(Consumer.class);

    delegate = new WrapperMutableMetaDocPart(
        new ImmutableMetaDocPart(tableRefFactory.createRoot(), "docPartId"),
        delegateChangeConsumer
    );
    docPart = new BatchMetaDocPart(delegate, testChangeConsumer, true);
  }

  @Test
  public void testCreatedOnCurrentBatch() {
    docPart.setCreatedOnCurrentBatch(true);
    assertTrue(docPart.isCreatedOnCurrentBatch());

    docPart.setCreatedOnCurrentBatch(false);
    assertFalse(docPart.isCreatedOnCurrentBatch());
  }

  @Test
  public void testAddMetaField() {
    String fieldName = "aFieldName";
    String fieldId = "aFieldID";
    FieldType fieldType = FieldType.INTEGER;

    assertNull(delegate.getMetaFieldByIdentifier(fieldId));
    assertNull(delegate.getMetaFieldByNameAndType(fieldName, fieldType));

    assertNull(docPart.getMetaFieldByIdentifier(fieldId));
    assertNull(docPart.getMetaFieldByNameAndType(fieldName, fieldType));

    docPart.addMetaField(fieldName, fieldId, fieldType);

    assertNotNull(delegate.getMetaFieldByIdentifier(fieldId));
    assertNotNull(delegate.getMetaFieldByNameAndType(fieldName, fieldType));

    assertNotNull(docPart.getMetaFieldByIdentifier(fieldId));
    assertNotNull(docPart.getMetaFieldByNameAndType(fieldName, fieldType));

    assertFalse(!docPart.streamAddedMetaFields().findAny().isPresent());
    assertFalse(!delegate.streamAddedMetaFields().findAny().isPresent());
    assertFalse(Iterables.isEmpty(docPart.getOnBatchModifiedMetaFields()));

    verify(testChangeConsumer).accept(docPart);
    verifyNoMoreInteractions(testChangeConsumer);
  }
}
