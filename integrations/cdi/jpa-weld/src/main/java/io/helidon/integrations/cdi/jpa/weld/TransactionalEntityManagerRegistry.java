/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.jpa.weld;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.SynchronizationType;
import javax.transaction.TransactionScoped;

@TransactionScoped
class TransactionalEntityManagerRegistry implements EntityManagerRegistry, Serializable {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, EntityManager> registry;

    TransactionalEntityManagerRegistry() {
        super();
        this.registry = new HashMap<>();
    }

    private Object readResolve() throws ObjectStreamException {
        return new TransactionalEntityManagerRegistry();
    }

    @Override
    public EntityManager get(final String persistenceUnitName,
                             final Supplier<? extends EntityManagerFactory> entityManagerFactorySupplier,
                             final SynchronizationType synchronizationType,
                             final Map<?, ?> properties) {
        return this.registry.computeIfAbsent(persistenceUnitName,
                                             n -> entityManagerFactorySupplier.get().createEntityManager(synchronizationType,
                                                                                                         properties));
    }

    @Override
    public EntityManager remove(final String persistenceUnitName) {
        final EntityManager entityManager = this.registry.remove(persistenceUnitName);
        if (entityManager != null) {
            entityManager.close();
        }
        return entityManager;
    }

}
