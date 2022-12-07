/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.SynchronizationType;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;

import static jakarta.persistence.SynchronizationType.SYNCHRONIZED;

/**
 * A {@link DelegatingEntityManager} created to support extended
 * persistence contexts.
 */
final class ExtendedEntityManager2 extends DelegatingEntityManager {

    private final EntityManager delegate;

    private final SynchronizationType syncType;

    private final TransactionSynchronizationRegistry tsr;

    ExtendedEntityManager2(TransactionSynchronizationRegistry tsr,
                           EntityManager delegate,
                           SynchronizationType syncType) {
        super();
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tsr = Objects.requireNonNull(tsr, "tsr");
        this.syncType = syncType == null ? SynchronizationType.SYNCHRONIZED : syncType;
    }

    @Override
    protected EntityManager acquireDelegate() {
        try {
            int ts = this.tsr.getTransactionStatus();
            switch (ts) {
            case Status.STATUS_ACTIVE:
                Object emf = this.delegate.getEntityManagerFactory();
                Object extantEm = this.tsr.getResource(emf);
                if (extantEm == null) {
                    if (this.syncType == SYNCHRONIZED) {
                        this.delegate.joinTransaction();
                    }
                    this.tsr.putResource(emf, this);
                } else if (extantEm != this) {
                    throw new PersistenceException();
                }
                break;
            case Status.STATUS_COMMITTED:
            case Status.STATUS_COMMITTING:
            case Status.STATUS_MARKED_ROLLBACK:
            case Status.STATUS_NO_TRANSACTION:
            case Status.STATUS_PREPARED:
            case Status.STATUS_PREPARING:
            case Status.STATUS_ROLLEDBACK:
            case Status.STATUS_ROLLING_BACK:
            case Status.STATUS_UNKNOWN:
                break;
            default:
                throw new PersistenceException("Unknown transaction status: " + ts);
            }
        } catch (PersistenceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PersistenceException(e.getMessage(), e);
        }
        return this.delegate;
    }

    @Override
    public void close() {
        if (this.isOpen()) {
            throw new IllegalStateException("close() cannot be called on a container-managed EntityManager");
        }
        super.close();
    }

    void dispose() {
        this.delegate.close();
    }

}
