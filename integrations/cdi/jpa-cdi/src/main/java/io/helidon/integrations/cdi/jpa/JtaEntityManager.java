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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.SynchronizationType;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

class JtaEntityManager extends DelegatingEntityManager {

    private final EntityManagerFactory emf;

    private final SynchronizationType syncType;

    private final Map<?, ?> properties;

    private final TransactionSynchronizationRegistry tsr;

    private final Object puid;

    JtaEntityManager(EntityManagerFactory emf,
                     SynchronizationType syncType,
                     Map<?, ?> properties,
                     TransactionSynchronizationRegistry tsr,
                     Object puid) {
        super();
        this.emf = Objects.requireNonNull(emf, "emf");
        // JPA permits null SynchronizationType and properties.
        this.syncType = syncType;
        if (syncType == null) {
            if (properties == null) {
                this.properties = null;
            } else {
                this.properties = Map.copyOf(properties);
            }
        } else if (properties == null || properties.isEmpty()) {
            this.properties = Map.of("jakarta.persistence.SynchronizationType", syncType);
        } else {
            Map<Object, Object> m = new LinkedHashMap<>(properties);
            m.put("jakarta.persistence.SynchronizationType", syncType);
            this.properties = Collections.unmodifiableMap(m);
        }
        this.tsr = Objects.requireNonNull(tsr, "tsr");
        this.puid = Objects.requireNonNull(puid, "puid");
    }

    @Override
    protected EntityManager acquireDelegate() {
        int ts = this.tsr.getTransactionStatus();
        switch (ts) {
        case Status.STATUS_ACTIVE:
            return this.computeIfAbsentForActiveTransaction();
        case Status.STATUS_COMMITTED:
        case Status.STATUS_COMMITTING:
        case Status.STATUS_MARKED_ROLLBACK:
        case Status.STATUS_NO_TRANSACTION:
        case Status.STATUS_PREPARED:
        case Status.STATUS_PREPARING:
        case Status.STATUS_ROLLEDBACK:
        case Status.STATUS_ROLLING_BACK:
        case Status.STATUS_UNKNOWN:
            return this.computeIfAbsentForNoTransaction();
        default:
            throw new PersistenceException("Unknown transaction status: " + ts);
        }
    }

    private EntityManager computeIfAbsentForActiveTransaction() {
        EntityManager em = (EntityManager) this.tsr.getResource(this.puid);
        if (em == null) {
            EntityManager em0 = this.emf.createEntityManager(this.syncType, this.properties);
            try {
                tsr.registerInterposedSynchronization(new Synchronization() {
                        @Override
                        public void beforeCompletion() {

                        }
                        @Override
                        public void afterCompletion(int completionStatus) {
                            // TO DO: oh Lord the threading concerns.  This method can get invoked asynchronously (by a
                            // timeout for example).  The TSR is in an undefined state at this point.  The application
                            // thread could still be using the EntityManager (em0 above).
                            //
                            // Solving this problem is not at all easy. The simplest thing to do is to simply close em0
                            // here, which will quite possibly be on another thread, and therefore illegal.
                            //
                            // Another approach would be to ensure that em0 is actually a wrapped EntityManager, all of
                            // whose "reachable" operations check to see if an asynchronous close has been requested,
                            // and, if so, close the EntityManager before carrying out their (now futile) work.
                            em0.close();
                        }
                    });
            } catch (RuntimeException | Error e) {
                try {
                    em0.close();
                } catch (RuntimeException | Error e2) {
                    e.addSuppressed(e2);
                }
                throw e;
            }
            em = new DelegatingEntityManager(em0) {
                    @Override
                    protected EntityManager acquireDelegate() {
                        // (Won't be called because we supplied the delegate at construction time.)
                        return em0;
                    }
                    @Override
                    public void close() {
                        throw new IllegalStateException("close() cannot be called on a container-managed EntityManager");
                    }
                };
            try {
                this.tsr.putResource(this.puid, em);
            } catch (RuntimeException | Error e) {
                try {
                    em.close();
                } catch (PersistenceException e2) {
                    e.addSuppressed(e2);
                }
                throw e;
            }
        } else if (this.syncType == SynchronizationType.SYNCHRONIZED
                   && synchronizationType(em) == SynchronizationType.UNSYNCHRONIZED) {
            // Check for mixed synchronization types per section 7.6.4.1
            // (https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#a11820):
            //
            // "If there is a persistence context of type SynchronizationType.UNSYNCHRONIZED associated with the JTA
            // transaction and the target component specifies a persistence context of type
            // SynchronizationType.SYNCHRONIZED, the IllegalStateException is thrown by the container."
            throw new IllegalStateException("SynchronizationType.UNSYNCHRONIZED EntityManager already associated");
        }
        return em;
    }

    private EntityManager computeIfAbsentForNoTransaction() {
        // TO DO: it would be nice to have a thread-local place to store these
        return this.emf.createEntityManager(this.syncType, this.properties);
    }

    static SynchronizationType synchronizationType(EntityManager em) {
        Map<?, ?> properties = em.getProperties();
        return properties == null ? null : (SynchronizationType) properties.get("jakarta.persistence.SynchronizationType");
    }

}
