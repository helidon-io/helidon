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

import java.util.Map;
import java.util.Objects;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.persistence.TransactionRequiredException;

/**
 * A {@link DelegatingEntityManager} that is definitionally not backed
 * by an extended persistence context and that assumes there is no JTA
 * transaction in effect.
 *
 * <p>Instances of this class are never directly seen by the end user.
 * Specifically, instances of this class are themselves returned by a
 * {@link DelegatingEntityManager} implementation's {@link
 * #acquireDelegate()} method.</p>
 */
final class NonTransactionalTransactionScopedEntityManager extends DelegatingEntityManager {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link
     * NonTransactionalTransactionScopedEntityManager}.
     *
     * @exception NullPointerException if {@code delegate} is {@code
     * null}
     */
    NonTransactionalTransactionScopedEntityManager(final EntityManager delegate, final Object key) {
        super(Objects.requireNonNull(delegate));
        NonTransactionalTransactionScopedEntityManagerReferences.put(key, this);
    }


    /*
     * Instance methods.
     */


    /**
     * Throws a {@link PersistenceException} when invoked.
     *
     * @return a non-{@code null} {@link EntityManager}
     */
    @Override
    protected EntityManager acquireDelegate() {
        throw new PersistenceException();
    }

    @Override
    public void persist(final Object entity) {
        throw new TransactionRequiredException();
    }

    @Override
    public <T> T merge(final T entity) {
        throw new TransactionRequiredException();
    }

    @Override
    public void remove(final Object entity) {
        throw new TransactionRequiredException();
    }

    @Override
    public void refresh(final Object entity) {
        throw new TransactionRequiredException();
    }

    @Override
    public void refresh(final Object entity,
                        final Map<String, Object> properties) {
        throw new TransactionRequiredException();
    }

    @Override
    public void refresh(final Object entity,
                        final LockModeType lockMode) {
        throw new TransactionRequiredException();
    }

    @Override
    public void refresh(final Object entity,
                        final LockModeType lockMode,
                        final Map<String, Object> properties) {
        throw new TransactionRequiredException();
    }

    @Override
    public void flush() {
        // See
        // https://github.com/javaee/glassfish/blob/f9e1f6361dcc7998cacccb574feef5b70bf84e23/appserver/common/container-common/src/main/java/com/sun/enterprise/container/common/impl/EntityManagerWrapper.java#L429-L430
        // but also note that Wildfly does *not* do this:
        // https://github.com/wildfly/wildfly/blob/cb3f5429e4bb5423236564c1f3afd8b4a2430ec0/jpa/subsystem/src/main/java/org/jboss/as/jpa/container/AbstractEntityManager.java#L454-L466.
        // We follow the reference application (Glassfish).
        throw new TransactionRequiredException();
    }

}
