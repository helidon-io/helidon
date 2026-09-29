/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.transaction.narayana;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import io.helidon.service.registry.Services;
import io.helidon.transaction.jta.JtaProvider;

import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestTransactions {
    private final TransactionManager transactionManager = Services.get(JtaProvider.class).transactionManager();

    @AfterEach
    void cleanUpTransaction() throws Exception {
        if (transactionManager.getTransaction() != null) {
            transactionManager.rollback();
        }
    }

    @Test
    void testTwoPhaseCommit() throws Exception {
        var firstResource = new RecordingResource();
        var secondResource = new RecordingResource();
        var synchronization = new RecordingSynchronization();

        transactionManager.begin();
        var transaction = transactionManager.getTransaction();
        assertThat("first resource enlistment", transaction.enlistResource(firstResource), is(true));
        assertThat("second resource enlistment", transaction.enlistResource(secondResource), is(true));
        transaction.registerSynchronization(synchronization);

        transactionManager.commit();

        assertThat("first resource callbacks", firstResource.events, contains("start", "end", "prepare", "commit:false"));
        assertThat("second resource callbacks", secondResource.events, contains("start", "end", "prepare", "commit:false"));
        assertThat(synchronization.events, contains("beforeCompletion", "afterCompletion"));
        assertThat(synchronization.completionStatus, is(Status.STATUS_COMMITTED));
        assertThat(transaction.getStatus(), is(Status.STATUS_COMMITTED));
        assertThat(transactionManager.getStatus(), is(Status.STATUS_NO_TRANSACTION));
    }

    @Test
    void testRollbackOnly() throws Exception {
        var resource = new RecordingResource();
        var synchronization = new RecordingSynchronization();

        transactionManager.begin();
        var transaction = transactionManager.getTransaction();
        assertThat("resource enlistment", transaction.enlistResource(resource), is(true));
        transaction.registerSynchronization(synchronization);
        transactionManager.setRollbackOnly();

        assertThrows(RollbackException.class, transactionManager::commit);

        assertThat(resource.events, contains("start", "end", "rollback"));
        assertThat(synchronization.events, contains("afterCompletion"));
        assertThat(synchronization.completionStatus, is(Status.STATUS_ROLLEDBACK));
        assertThat(transaction.getStatus(), is(Status.STATUS_ROLLEDBACK));
        assertThat(transactionManager.getStatus(), is(Status.STATUS_NO_TRANSACTION));
    }

    private static class RecordingResource implements XAResource {
        private final List<String> events = new ArrayList<>();

        @Override
        public void start(Xid xid, int flags) {
            events.add("start");
        }

        @Override
        public void end(Xid xid, int flags) {
            events.add("end");
        }

        @Override
        public int prepare(Xid xid) {
            events.add("prepare");
            return XA_OK;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) {
            events.add("commit:" + onePhase);
        }

        @Override
        public void rollback(Xid xid) {
            events.add("rollback");
        }

        @Override
        public boolean isSameRM(XAResource resource) {
            return this == resource;
        }

        @Override
        public int getTransactionTimeout() {
            return 0;
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            return false;
        }

        @Override
        public Xid[] recover(int flags) {
            return new Xid[0];
        }

        @Override
        public void forget(Xid xid) {
        }
    }

    private static class RecordingSynchronization implements Synchronization {
        private final List<String> events = new ArrayList<>();
        private int completionStatus = Status.STATUS_UNKNOWN;

        @Override
        public void beforeCompletion() {
            events.add("beforeCompletion");
        }

        @Override
        public void afterCompletion(int status) {
            events.add("afterCompletion");
            completionStatus = status;
        }
    }
}
