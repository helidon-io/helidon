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

package io.helidon.webserver.benchmark.jmh;

import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import io.helidon.service.registry.Services;
import io.helidon.transaction.jta.JtaProvider;

import jakarta.transaction.TransactionManager;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class NarayanaTransactionJmhTest {
    @Param({"1", "2"})
    public int resourceCount;

    private TransactionManager transactionManager;
    private XAResource[] resources;

    @Setup(Level.Trial)
    public void setUp() {
        transactionManager = Services.get(JtaProvider.class).transactionManager();
        resources = new XAResource[resourceCount];
        for (int i = 0; i < resources.length; i++) {
            resources[i] = new TransactionResource();
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (transactionManager.getTransaction() != null) {
            transactionManager.rollback();
        }
    }

    @Benchmark
    public void commit() throws Exception {
        transactionManager.begin();
        var transaction = transactionManager.getTransaction();
        for (XAResource resource : resources) {
            if (!transaction.enlistResource(resource)) {
                throw new IllegalStateException("Could not enlist transaction resource");
            }
        }
        transactionManager.commit();
    }

    private static class TransactionResource implements XAResource, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public void start(Xid xid, int flags) {
        }

        @Override
        public void end(Xid xid, int flags) {
        }

        @Override
        public int prepare(Xid xid) {
            return XA_OK;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) {
        }

        @Override
        public void rollback(Xid xid) {
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
}
