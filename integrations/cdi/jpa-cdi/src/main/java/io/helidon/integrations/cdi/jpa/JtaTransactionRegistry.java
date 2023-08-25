/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.jpa;

import java.util.Objects;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;

@Singleton
final class JtaTransactionRegistry implements TransactionRegistry {

    private final TransactionSynchronizationRegistry tsr;

    @Inject
    JtaTransactionRegistry(TransactionSynchronizationRegistry tsr) {
        super();
        this.tsr = Objects.requireNonNull(tsr, "tsr");
    }

    @Override
    public boolean active() {
        return this.tsr.getTransactionStatus() == Status.STATUS_ACTIVE;
    }

    @Override
    public void addCompletionListener(Consumer<? super CompletionStatus> cl) {
        this.tsr.registerInterposedSynchronization(new AfterCompletionSynchronization(cs -> {
                    switch (cs) {
                    case Status.STATUS_COMMITTED:
                        cl.accept(CompletionStatus.COMMITTED);
                        break;
                    case Status.STATUS_ROLLEDBACK:
                        cl.accept(CompletionStatus.ROLLED_BACK);
                        break;
                    default:
                        throw new AssertionError();
                    }}));
    }

    @Override
    public Object get(Object k) {
        return this.tsr.getResource(k);
    }

    @Override
    public void put(Object k, Object v) {
        this.tsr.putResource(k, v);
    }

}
