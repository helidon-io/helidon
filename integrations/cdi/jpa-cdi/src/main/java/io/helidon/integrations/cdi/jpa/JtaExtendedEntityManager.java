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
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * A {@link DelegatingEntityManager} created to support extended
 * persistence contexts.
 */
final class JtaExtendedEntityManager extends DelegatingEntityManager {

    private final EntityManager delegate;

    private final boolean isSynchronized;

    private final BooleanSupplier activeTransaction;

    private final Function<? super Object, ?> transactionalResourceGetter;

    private final BiConsumer<? super Object, ? super Object> transactionalResourceSetter;

    JtaExtendedEntityManager(BooleanSupplier activeTransaction,
                             Function<? super Object, ?> transactionalResourceGetter,
                             BiConsumer<? super Object, ? super Object> transactionalResourceSetter,
                             EntityManager delegate,
                             boolean isSynchronized) {
        super();
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.activeTransaction = Objects.requireNonNull(activeTransaction, "activeTransaction");
        this.transactionalResourceGetter = Objects.requireNonNull(transactionalResourceGetter, "transactionalResourceGetter");
        this.transactionalResourceSetter = Objects.requireNonNull(transactionalResourceSetter, "transactionalResourceSetter");
        this.isSynchronized = isSynchronized;
    }

    @Override
    protected EntityManager acquireDelegate() {
        try {
            if (this.activeTransaction.getAsBoolean()) {
                Object emf = this.delegate.getEntityManagerFactory();
                Object extantEm = this.transactionalResourceGetter.apply(emf);
                if (extantEm == null) {
                    this.transactionalResourceSetter.accept(emf, this);
                    if (this.isSynchronized) {
                        this.delegate.joinTransaction();
                    }
                } else if (extantEm != this) {
                    throw new PersistenceException();
                }
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
