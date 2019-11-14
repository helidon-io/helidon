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
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Vetoed;
import javax.persistence.EntityManager;

/**
 * A {@link DelegatingEntityManager} created in certain very specific
 * JPA-mandated transaction-related scenarios.
 */
@Vetoed
class CdiTransactionScopedEntityManager extends DelegatingEntityManager {


    /*
     * Instance fields.
     */


    private final Instance<Object> instance;

    private final Set<? extends Annotation> suppliedQualifiers;

    private final TransactionSupport transactionSupport;

    private final AtomicInteger priorTransactionStatus;

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
        this.priorTransactionStatus = new AtomicInteger(6); // 6 == javax.transaction.Status.STATUS_NO_TRANSACTION
        this.closeDelegate = true;
        this.instance = null;
        this.transactionSupport = null;
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
        this.priorTransactionStatus = new AtomicInteger(6); // 6 == javax.transaction.Status.STATUS_NO_TRANSACTION
        this.instance = Objects.requireNonNull(instance);
        this.transactionSupport = instance.select(TransactionSupport.class).get();
        this.suppliedQualifiers = Objects.requireNonNull(suppliedQualifiers);
    }


    /*
     * Instance methods.
     */


    Number getPriorTransactionStatus() {
        return this.priorTransactionStatus;
    }

    /**
     * Disposes of this {@link CdiTransactionScopedEntityManager} by
     * capturing the {@link TransactionSupport#getStatus() current
     * transaction status} and then calling the {@link #close()}
     * method.
     *
     * <p>If the {@link javax.transaction.TransactionScoped} scope is
     * behaving the way it should, then this method will be invoked
     * only when the underlying transaction has finished committing or
     * rolling back, and in no other situations.  It follows that the
     * current transaction status must be either {@link
     * TransactionSupport#STATUS_COMMITTED} or {@link
     * TransactionSupport#STATUS_ROLLEDBACK}.</p>
     *
     * <p>This method must not be overridden.  It is not {@code final}
     * only due to CDI restrictions.</p>
     *
     * <h2>Thread Safety</h2>
     *
     * <p>This method may be (and often is) called by a thread that is
     * not the CDI container thread, since transactions may roll back
     * on such a thread.</p>
     *
     * @param ignoredInstance the {@link Instance} supplied by the CDI
     * bean configuration machinery when the transaction scope is
     * going away; ignored by this method; may be {@code null}
     *
     * @see #close()
     *
     * @see #getPriorTransactionStatus()
     */
    void dispose(final Instance<Object> ignoredInstance) {
        try {
            this.priorTransactionStatus.getAndSet(this.transactionSupport.getStatus());
            final int currentTransactionStatus = this.priorTransactionStatus.get();
            assert
                currentTransactionStatus == TransactionSupport.STATUS_COMMITTED
                || currentTransactionStatus == TransactionSupport.STATUS_ROLLEDBACK
                : "Unexpected transaction status during dispose(): " + currentTransactionStatus;

        } finally {
            this.close();
        }
    }

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
