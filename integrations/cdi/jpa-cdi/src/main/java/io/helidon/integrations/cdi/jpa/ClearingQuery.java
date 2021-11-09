/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Objects;

import javax.persistence.EntityManager;
import javax.persistence.Query;

final class ClearingQuery extends DelegatingQuery {

    private final EntityManager entityManager;

    ClearingQuery(final EntityManager entityManager, final Query delegate) {
        super(delegate);
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getResultList() {
        try {
            return super.getResultList();
        } finally {
            this.entityManager.clear();
        }
    }

    @Override
    public Object getSingleResult() {
        try {
            return super.getSingleResult();
        } finally {
            this.entityManager.clear();
        }
    }

}
