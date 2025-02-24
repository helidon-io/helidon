/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.common;

import io.helidon.tests.integration.jpa.common.model.PokemonDataSet;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Base test implementation.
 */
public abstract class AbstractTestImpl {

    @Inject
    @SuppressWarnings("unused")
    protected PokemonDataSet dataSet;

    @PersistenceContext(unitName = "test")
    protected EntityManager em;

    protected <T> T result(TypedQuery<T> typedQuery) {
        try {
            return typedQuery.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    protected void clear() {
        em.flush();
        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();
    }
}
