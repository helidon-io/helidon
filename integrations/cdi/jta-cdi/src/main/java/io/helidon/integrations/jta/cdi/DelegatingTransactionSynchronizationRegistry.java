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
package io.helidon.integrations.jta.cdi;

import java.util.Map;

import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;

/**
 * An {@code abstract} {@link TransactionSynchronizationRegistry}
 * implementation that delegates all method invocations to another
 * {@link TransactionSynchronizationRegistry}.
 *
 * <h2>Design Notes</h2>
 *
 * <p>This class is {@code public} for convenience.  It is extended by
 * other non-{@code public} internal classes.</p>
 *
 * @see TransactionSynchronizationRegistry
 *
 * @deprecated An equivalent class now exists in Narayana itself.
 */
@Deprecated(forRemoval = true)
public abstract class DelegatingTransactionSynchronizationRegistry implements TransactionSynchronizationRegistry {

    private final TransactionSynchronizationRegistry delegate;

    /**
     * Creates a new {@link
     * DelegatingTransactionSynchronizationRegistry}.
     *
     * @param delegate the {@link TransactionSynchronizationRegistry}
     * to which all method invocations will be delegated; may be
     * {@code null} in which case every method in this class will
     * throw an {@link IllegalStateException} when invoked
     *
     */
    protected DelegatingTransactionSynchronizationRegistry(final TransactionSynchronizationRegistry delegate) {
        super();
        this.delegate = delegate;
    }

    /**
     * Return an opaque object to represent the transaction bound to
     * the current thread at the time this method is called.
     *
     * <p>This method may return {@code null}.</p>
     *
     * <p>This object overrides {@link Object#hashCode()} and {@link
     * Object#equals(Object)} to allow its use as the key in a {@link
     * Map} for use by the caller. If there is no transaction
     * currently active, this method will return {@code null}.</p>
     *
     * <p>The {@link Object} returned will return the same hashCode
     * and compare equal to all other objects returned by calling this
     * method from any component executing in the same transaction
     * context in the same application server.</p>
     *
     * <p>The {@link Object#toString()} method returns a {@link
     * String} that might be usable by a human reader to usefully
     * understand the transaction context. The {@link
     * Object#toString()} result is otherwise not
     * defined. Specifically, there is no forward or backward
     * compatibility guarantee of the results of the returned {@link
     * Object}'s {@link Object#toString()} override.</p>
     *
     * <p>The object is not necessarily serializable, and has no
     * defined behavior outside the virtual machine whence it was
     * obtained.</p>
     *
     * @return an opaque object representing the transaction bound to
     * the current thread at the time this method is called, or {@code
     * null}
     *
     * @exception IllegalStateException if a {@code null} {@code
     * delegate} was supplied at {@linkplain
     * #DelegatingTransactionSynchronizationRegistry(TransactionSynchronizationRegistry)
     * construction time}
     */
    @Override
    public Object getTransactionKey() {
        if (this.delegate == null) {
            throw new IllegalStateException("delegate == null");
        }
        return this.delegate.getTransactionKey();
    }

    /**
     * Adds or replaces an object in the {@link Map} of resources
     * being managed for the transaction bound to the current thread
     * at the time this method is called.
     *
     * <p>The supplied key should be of an caller-defined class so as
     * not to conflict with other users. The class of the key must
     * guarantee that the {@link Object#hashCode() hashCode()} and
     * {@link Object#equals(Object) equals(Object)} methods are
     * suitable for use as keys in a {@link Map}. The key and value
     * are not examined or used by the implementation. The general
     * contract of this method is that of {@link Map#put(Object,
     * Object)} for a {@link Map} that supports non-{@code null} keys
     * and null values. For example, if there is already an value
     * associated with the key, it is replaced by the {@code value}
     * parameter.</p>
     *
     * @param key the key for the {@link Map} entry; must not be
     * {@code null}
     *
     * @param value the value for the {@link Map} entry
     *
     * @exception IllegalStateException if no transaction is active or
     * if a {@code null} {@code delegate} was supplied at {@linkplain
     * #DelegatingTransactionSynchronizationRegistry(TransactionSynchronizationRegistry)
     * construction time}
     *
     * @exception NullPointerException if the parameter {@code key} is
     * {@code null}
     */
    @Override
    public void putResource(final Object key, final Object value) {
        if (this.delegate == null) {
            throw new IllegalStateException("delegate == null");
        }
        this.delegate.putResource(key, value);
    }

    /**
     * Gets an object from the {@link Map} of resources being managed
     * for the transaction bound to the current thread at the time
     * this method is called.
     *
     * <p>The key should have been supplied earlier by a call to
     * {@link #putResource(Object, Object)} in the same
     * transaction. If the key cannot be found in the current resource
     * {@link Map}, {@code null} is returned. The general contract of
     * this method is that of {@link Map#get(Object)} for a {@link
     * Map} that supports non-{@code null} keys and null values. For
     * example, the returned value is null if there is no entry for
     * the parameter {@code key} or if the value associated with the
     * key is actually {@code null}.</p>
     *
     * @param key the key for the {@link Map} entry
     *
     * @return the value associated with the supplied {@code key}; may
     * be {@code null}
     *
     * @exception IllegalStateException if no transaction is active or
     * if a {@code null} {@code delegate} was supplied at {@linkplain
     * #DelegatingTransactionSynchronizationRegistry(TransactionSynchronizationRegistry)
     * construction time}
     *
     * @exception NullPointerException if the parameter {@code key} is
     * {@code null}
     */
    @Override
    public Object getResource(final Object key) {
        if (this.delegate == null) {
            throw new IllegalStateException("delegate == null");
        }
        return this.delegate.getResource(key);
    }

    /**
     * Registers a {@link Synchronization} instance with special
     * ordering semantics.
     *
     * <p>The supplied {@link Synchronization}'s {@link
     * Synchronization#beforeCompletion()} method will be called after
     * all {@code SessionSynchronization#beforeCompletion()} callbacks
     * and callbacks registered directly with the {@link Transaction},
     * but before the 2-phase commit process starts. Similarly, the
     * {@link Synchronization#afterCompletion(int)} callback will be
     * called after 2-phase commit completes but before any {@code
     * SessionSynchronization} and {@link Transaction} {@code
     * afterCompletion(int)} callbacks.</p>
     *
     * <p>The {@link Synchronization#beforeCompletion()} callback will
     * be invoked in the transaction context of the transaction bound
     * to the current thread at the time this method is
     * called. Allowable methods include access to resources,
     * e.g. connectors. No access is allowed to "user components"
     * (e.g. timer services or bean methods), as these might change
     * the state of data being managed by the caller, and might change
     * the state of data that has already been flushed by another
     * caller of {@link
     * #registerInterposedSynchronization(Synchronization)}. The
     * general context is the component context of the caller of
     * {@link
     * #registerInterposedSynchronization(Synchronization)}.</p>
     *
     * <p>The {@link Synchronization#afterCompletion(int)} callback
     * will be invoked in an undefined context. No access is permitted
     * to "user components" as defined above. Resources can be closed
     * but no transactional work can be performed with them.</p>
     *
     * <p>If this method is invoked without an active transaction
     * context, an {@link IllegalStateException} is thrown.</p>
     *
     * <p>If this method is invoked after the two-phase commit
     * processing has started, an {@link IllegalStateException} is
     * thrown.</p>
     *
     * @param synchronization the {@link Synchronization} to register;
     * must not be {@code null}
     *
     * @exception IllegalStateException if no transaction is active or
     * two-phase commit processing has started or if a {@code null}
     * {@code delegate} was supplied at {@linkplain
     * #DelegatingTransactionSynchronizationRegistry(TransactionSynchronizationRegistry)
     * construction time}
     *
     * @see Synchronization
     *
     * @see Synchronization#beforeCompletion()
     *
     * @see Synchronization#afterCompletion(int)
     */
    @Override
    public void registerInterposedSynchronization(final Synchronization synchronization) {
        if (this.delegate == null) {
            throw new IllegalStateException("delegate == null");
        }
        this.delegate.registerInterposedSynchronization(synchronization);
    }

    /**
     * Return the status of the transaction bound to the current
     * thread at the time this method is called.
     *
     * <p>This is the result of executing {@link
     * TransactionManager#getStatus()} in the context of the
     * transaction bound to the current thread at the time this method
     * is called.</p>
     *
     * @return the status of the transaction bound to the current
     * thread at the time this method is called; will be equal the
     * value of one of the constants defined in the {@link Status}
     * class
     *
     * @exception IllegalStateException if a {@code null} {@code
     * delegate} was supplied at {@linkplain
     * #DelegatingTransactionSynchronizationRegistry(TransactionSynchronizationRegistry)
     * construction time}
     *
     * @see TransactionManager#getStatus()
     *
     * @see Status
     */
    @Override
    public int getTransactionStatus() {
        if (this.delegate == null) {
            throw new IllegalStateException("delegate == null");
        }
        return this.delegate.getTransactionStatus();
    }

    /**
     * Sets the {@code rollbackOnly} status of the transaction bound
     * to the current thread at the time this method is called.
     *
     * @exception IllegalStateException if no transaction is active or
     * if a {@code null} {@code delegate} was supplied at {@linkplain
     * #DelegatingTransactionSynchronizationRegistry(TransactionSynchronizationRegistry)
     * construction time}
     */
    @Override
    public void setRollbackOnly() {
        if (this.delegate == null) {
            throw new IllegalStateException("delegate == null");
        }
        this.delegate.setRollbackOnly();
    }

    /**
     * Get the {@code rollbackOnly} status of the transaction bound to
     * the current thread at the time this method is called.
     *
     * @return the {@code rollbackOnly} status
     *
     * @exception IllegalStateException if no transaction is active or
     * if a {@code null} {@code delegate} was supplied at {@linkplain
     * #DelegatingTransactionSynchronizationRegistry(TransactionSynchronizationRegistry)
     * construction time}
     */
    @Override
    public boolean getRollbackOnly() {
        if (this.delegate == null) {
            throw new IllegalStateException("delegate == null");
        }
        return this.delegate.getRollbackOnly();
    }

}
