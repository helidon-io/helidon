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
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.inject.Instance;
import javax.persistence.EntityManager;

/**
 * A {@link DelegatingEntityManager} created in certain very specific
 * JPA-mandated transaction-related scenarios.
 */
class CdiTransactionScopedEntityManager extends DelegatingEntityManager {


    /*
     * Instance fields.
     */


    private final Instance<Object> instance;

    private final Set<? extends Annotation> suppliedQualifiers;

    private EntityManager delegate;

    private boolean closeDelegate;

    private boolean closed;


    /*
     * Constructors.
     */


    /**
     * This constructor exists solely to fulfil the requirement that
     * certain kinds of contextual objects in CDI must have
     * zero-argument constructors.  Do not call this constructor by
     * hand.
     *
     * @deprecated Use the {@link
     * #CdiTransactionScopedEntityManager(Instance, Set)} constructor
     * instead.
     */
    @Deprecated
    CdiTransactionScopedEntityManager() {
        super();
        this.closeDelegate = true;
        this.instance = null;
        this.suppliedQualifiers = Collections.emptySet();
    }

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
        super();
        this.instance = Objects.requireNonNull(instance);
        this.suppliedQualifiers = Objects.requireNonNull(suppliedQualifiers);
    }


    /*
     * Instance methods.
     */


    @Override
    protected EntityManager acquireDelegate() {
        if (this.delegate == null) {
            this.delegate = EntityManagers.createContainerManagedEntityManager(this.instance, this.suppliedQualifiers);
            this.closeDelegate = true;
        }
        assert this.delegate != null;
        return this.delegate;
    }

    /**
     * Sets this {@link CdiTransactionScopedEntityManager}'s internal
     * delegate {@link EntityManager} only if it has not yet been set.
     *
     * <p>This method will prevent this {@link
     * CdiTransactionScopedEntityManager} from {@linkplain
     * EntityManager#close() closing} the supplied {@code delegate}.
     * The caller must ensure that this delegate will be closed when
     * appropriate.</p>
     *
     * @param delegate the delegate {@link EntityManager}; must not be
     * {@code null}; must not be this {@link
     * CdiTransactionScopedEntityManager}
     *
     * @exception NullPointerException if {@code delegate} is {@code null}
     *
     * @exception IllegalArgumentException if {@code delegate} is
     * equal to this {@code CdiTransactionScopedEntityManager}
     *
     * @exception IllegalStateException if this {@link
     * CdiTransactionScopedEntityManager}'s internal delegate has
     * already been set one way or another
     *
     * @see #acquireDelegate()
     */
    void setDelegate(final EntityManager delegate) {
        Objects.requireNonNull(delegate);
        if (delegate == this) {
            throw new IllegalArgumentException("delegate == this");
        }
        if (this.delegate != null) {
            throw new IllegalStateException();
        }
        this.delegate = delegate;
        this.closeDelegate = false;
    }

    @Override
    public boolean isOpen() {
        return !this.closed && super.isOpen();
    }

    @Override
    public void close() {
        this.closed = true;
        if (this.closeDelegate) {
            super.close();
            assert this.delegate != null ? !this.delegate.isOpen() : true;
        }
    }

}
