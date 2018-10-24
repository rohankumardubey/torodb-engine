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

package com.torodb.mongodb.repl.oplogreplier.batch;

import com.torodb.core.cursors.Cursor;
import com.torodb.core.cursors.IteratorCursor;
import com.torodb.core.document.ToroDocument;
import com.torodb.core.exceptions.user.UniqueIndexViolationException;
import com.torodb.core.exceptions.user.UserException;
import com.torodb.core.language.AttributeReference;
import com.torodb.core.transaction.RollbackException;
import com.torodb.kvdocument.values.KvDocument;
import com.torodb.kvdocument.values.KvValue;
import com.torodb.mongodb.core.MongodServer;
import com.torodb.mongodb.core.WriteMongodTransaction;
import com.torodb.mongodb.repl.oplogreplier.analyzed.AnalyzedOp;
import com.torodb.mongodb.repl.oplogreplier.analyzed.AnalyzedOpType;
import com.torodb.mongodb.utils.DefaultIdUtils;
import com.torodb.mongowp.Status;
import org.jooq.lambda.tuple.Tuple2;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.ThreadSafe;

/**
 *
 */
@ThreadSafe
public class NamespaceJobExecutor {

  private static final AttributeReference _ID_ATT_REF = new AttributeReference.Builder()
      .addObjectKey(DefaultIdUtils.ID_KEY)
      .build();
  private static final int MAX_ATTEMPTS = 2;

  public void apply(NamespaceJob job, MongodServer server, boolean optimisticDeleteAndCreate)
      throws RollbackException, UserException, NamespaceJobExecutionException,
      UniqueIndexViolationException {

    int attempts = 1;
    while (attempts <= MAX_ATTEMPTS) {
      attempts++;
      try (WriteMongodTransaction trans = server.openWriteTransaction()) {
        apply(job, trans, optimisticDeleteAndCreate);
        trans.commit();
        break;
      } catch (RollbackException ignore) {
        //just retry in case the transaction rolled because it was needed to change the schema
      } catch (TimeoutException ex) {
        throw new RollbackException(ex);
      }
    }
  }

  private final void apply(NamespaceJob job, WriteMongodTransaction trans,
      boolean optimisticDeleteAndCreate)
      throws RollbackException, UserException, NamespaceJobExecutionException,
      UniqueIndexViolationException {

    Map<AnalyzedOp, Integer> fetchDids = fetchDids(job, trans, optimisticDeleteAndCreate);

    List<Status<?>> errors = findErrors(job, fetchDids);
    if (!errors.isEmpty()) {
      throw new NamespaceJobExecutionException(job, errors);
    }
    if (errors.isEmpty()) {
      Map<AnalyzedOp, ToroDocument> fetchDocs = fetchDocs(job, trans, fetchDids);
      deleteDocs(job, trans, fetchDids);
      insertDocs(job, trans, fetchDocs);
    }
  }

  /**
   * Returns a map whose entries are the did of each analyzed op that requires to fetch them.
   *
   * @see AnalyzedOp#requiresToFetchToroId()
   */
  private static Map<AnalyzedOp, Integer> fetchDids(NamespaceJob job,
      WriteMongodTransaction transaction, boolean optimisticDeleteAndCreate) {

    Stream<AnalyzedOp> filteredJobs = job.getJobs().stream()
        .filter(AnalyzedOp::requiresToFetchToroId);

    if (optimisticDeleteAndCreate) {
      filteredJobs = filteredJobs.filter(op -> op.getType() != AnalyzedOpType.DELETE_CREATE);
    }

    Map<KvValue<?>, AnalyzedOp> mapToFetch = filteredJobs
        .collect(Collectors.toMap(
            op -> op.getMongoDocId(),
            Function.identity()
        ));

    return transaction.getDocTransaction()
        .findByAttRefInProjection(
            job.getDatabase(),
            job.getCollection(),
            _ID_ATT_REF,
            mapToFetch.keySet())
        .getRemaining()
        .stream()
        .collect(Collectors.toMap(
            tuple -> mapToFetch.get(tuple.v2),
            Tuple2::v1)
        );
  }

  /**
   * Returns a list of all mismatching errors on the given job.
   *
   * @param job
   * @param fetchDids
   * @return
   */
  private List<Status<?>> findErrors(NamespaceJob job, Map<AnalyzedOp, Integer> fetchDids) {
    return job.getJobs().stream()
        .filter(AnalyzedOp::requiresMatch) //only care about ops that requires a match
        .filter(op -> !fetchDids.containsKey(op)) //only care about ops that did not match
        .map(AnalyzedOp::getMismatchErrorMessage)
        .collect(Collectors.toList());
  }

  private Map<AnalyzedOp, ToroDocument> fetchDocs(NamespaceJob job,
      WriteMongodTransaction transaction, Map<AnalyzedOp, Integer> fetchDids) {
    Map<Integer, AnalyzedOp> didToOps = job.getJobs().stream()
        .filter(AnalyzedOp::requiresFetch) //only care about ops that requires a fetch
        .collect(Collectors.toMap(
            op -> fetchDids.get(op),
            Function.identity())
        );

    Cursor<Integer> didCursor = new IteratorCursor<>(didToOps.keySet().iterator());
    return transaction.getDocTransaction()
        .fetch(job.getDatabase(), job.getCollection(), didCursor)
        .asDocCursor()
        .getRemaining()
        .stream()
        .collect(Collectors.toMap(
            toroDoc -> didToOps.get(toroDoc.getId()),
            Function.identity())
        );
  }

  private void deleteDocs(NamespaceJob job, WriteMongodTransaction transaction,
      Map<AnalyzedOp, Integer> fetchDids) {
    if (fetchDids.isEmpty()) {
      return;
    }

    Stream<Integer> didsToDelete = job.getJobs().stream()
        .filter(AnalyzedOp::deletes)
        .map(op -> fetchDids.get(op))
        .filter(did -> did != null);

    transaction.getDocTransaction().delete(job.getDatabase(), job.getCollection(),
        new IteratorCursor<>(didsToDelete.iterator()));
  }

  private void insertDocs(NamespaceJob job, WriteMongodTransaction transaction,
      Map<AnalyzedOp, ToroDocument> fetchDocs) throws UserException {
    Function<AnalyzedOp, KvDocument> getFetchDocFun = op -> {
      ToroDocument fetchToroDoc = fetchDocs.get(op);
      if (fetchToroDoc == null) {
        return null;
      } else {
        return fetchToroDoc.getRoot();
      }
    };
    Stream<KvDocument> docsToInsert = job.getJobs().stream()
        .map(op -> op.calculateDocToInsert(getFetchDocFun))
        .filter(doc -> doc != null);

    transaction.getDocTransaction().insert(job.getDatabase(), job.getCollection(), docsToInsert);
  }
}
