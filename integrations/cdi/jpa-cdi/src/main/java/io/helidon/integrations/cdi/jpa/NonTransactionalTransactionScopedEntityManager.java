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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.inject.Instance;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.persistence.SynchronizationType;
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


    NonTransactionalTransactionScopedEntityManager(final Instance<Object> instance,
                                                   final Set<? extends Annotation> suppliedQualifiers) {
        super(createDelegate(instance, suppliedQualifiers));
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

    @Override
    public void close() {
        super.close();
    }

    private static EntityManager createDelegate(final Instance<Object> instance,
                                                final Set<? extends Annotation> suppliedQualifiers) {
        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);
        final EntityManagerFactory emf = JpaExtension.getContainerManagedEntityManagerFactory(instance, suppliedQualifiers);
        assert emf != null;
        assert emf.isOpen();
        // We are only going to use syncType for non-transactional
        // EntityManagers (see below).  In such cases, if it is not
        // UNSYNCHRONIZED, it must be null (not SYNCHRONIZED).
        final SynchronizationType syncType =
            suppliedQualifiers.contains(Unsynchronized.Literal.INSTANCE) ? SynchronizationType.UNSYNCHRONIZED : null;
        // Create the actual EntityManager that will be produced.  It
        // itself will be in @Dependent scope but its delegate may be
        // something else.  Revisit: need to supply properties somehow
        @SuppressWarnings("rawtypes") // the API requires it sadly
        final Map properties = new HashMap();
        final EntityManager delegate = emf.createEntityManager(syncType, properties);
        assert delegate != null;
        assert delegate.isOpen();
        return delegate;
    }

}
