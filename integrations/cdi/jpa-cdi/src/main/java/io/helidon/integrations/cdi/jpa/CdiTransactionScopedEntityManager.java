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
package io.helidon.integrations.cdi.jpa;

import java.lang.annotation.Annotation;
import java.util.Set;

import javax.enterprise.inject.Instance;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

/**
 * A {@link DelegatingEntityManager} created in certain very specific
 * JPA-mandated transaction-related scenarios.
 */
final class CdiTransactionScopedEntityManager extends DelegatingEntityManager {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link CdiTransactionScopedEntityManager}.
     *
     * @param instance an {@link Instance} representing the CDI
     * container; must not be {@code null}
     *
     * @param suppliedQualifiers a {@link Set} of qualifier {@link
     * Annotation} instances; must not be {@code null}
     *
     * @exception NullPointerException if either parameter value is
     * {@code null}
     */
    CdiTransactionScopedEntityManager(final Instance<Object> instance,
                                      final Set<? extends Annotation> suppliedQualifiers) {
        super(EntityManagers.createContainerManagedEntityManager(instance, suppliedQualifiers));
    }


    /*
     * Instance methods.
     */


    /**
     * Throws a {@link PersistenceException} when invoked, because it
     * will never be invoked in the normal course of events.
     *
     * @return a non-{@code null} {@link EntityManager}, but this will
     * never happen
     *
     * @exception PersistenceException when invoked
     */
    @Override
    protected EntityManager acquireDelegate() {
        throw new PersistenceException();
    }

}
