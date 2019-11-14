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
import java.util.Map;
import java.util.Set;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Vetoed;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.StoredProcedureQuery;
import javax.persistence.TransactionRequiredException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaQuery;

/**
 * A {@link DelegatingEntityManager} that is definitionally not backed
 * by an extended persistence context and that assumes there is no JTA
 * transaction in effect.
 *
 * <p>This class obeys the requirements imposed upon it by sections
 * 3.3.2, 3.3.3 and 7.6.2 of the JPA 2.2 specification.</p>
 *
 * <p>Instances of this class are never directly seen by the end user.
 * Specifically, instances of this class are themselves returned by a
 * {@link DelegatingEntityManager} implementation's {@link
 * #acquireDelegate()} method and only under appropriate
 * circumstances.</p>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>As with all {@link EntityManager} implementations, instances of
 * this class are not safe for concurrent use by multiple threads.</p>
 */
@Vetoed
final class NonTransactionalEntityManager extends DelegatingEntityManager {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link NonTransactionalEntityManager}.
     *
     * @param instance an {@link Instance} representing the CDI
     * container
     *
     * @param suppliedQualifiers a {@link Set} of qualifier {@link
     * Annotation}s; must not be {@code null}
     *
     * @exception NullPointerException if either parameter value is
     * {@code null}
     */
    NonTransactionalEntityManager(final Instance<Object> instance,
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

    @Override
    public <T> TypedQuery<T> createNamedQuery(final String name, final Class<T> resultClass) {
        return new ClearingTypedQuery<>(this, super.createNamedQuery(name, resultClass));
    }

    @Override
    public <T> TypedQuery<T> createQuery(final CriteriaQuery<T> criteriaQuery) {
        return new ClearingTypedQuery<>(this, super.createQuery(criteriaQuery));
    }

    @Override
    public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
        return new ClearingTypedQuery<>(this, super.createQuery(qlString, resultClass));
    }

    @Override
    public <T> T find(final Class<T> entityClass,
                      final Object primaryKey,
                      final Map<String, Object> properties) {
        final T returnValue = super.find(entityClass, primaryKey, properties);
        this.clear();
        return returnValue;
    }

    @Override
    public <T> T find(final Class<T> entityClass,
                      final Object primaryKey,
                      final LockModeType lockMode) {
        final T returnValue = super.find(entityClass, primaryKey, lockMode);
        this.clear();
        return returnValue;
    }

    @Override
    public <T> T find(final Class<T> entityClass,
                      final Object primaryKey,
                      final LockModeType lockMode,
                      final Map<String, Object> properties) {
        final T returnValue = super.find(entityClass, primaryKey, lockMode, properties);
        this.clear();
        return returnValue;
    }

    @Override
    public <T> T find(final Class<T> entityClass,
                      final Object primaryKey) {
        final T returnValue = super.find(entityClass, primaryKey);
        this.clear();
        return returnValue;
    }

    @Override
    public Query createNamedQuery(final String name) {
        return new ClearingQuery(this, super.createNamedQuery(name));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Query createNativeQuery(final String sqlString, final Class resultClass) {
        return new ClearingQuery(this, super.createNativeQuery(sqlString, resultClass));
    }

    @Override
    public Query createNativeQuery(final String sqlString, final String resultSetMapping) {
        return new ClearingQuery(this, super.createNativeQuery(sqlString, resultSetMapping));
    }

    @Override
    public Query createNativeQuery(final String sqlString) {
        return new ClearingQuery(this, super.createNativeQuery(sqlString));
    }

    @Override
    public Query createQuery(final String jpqlString) {
        return new ClearingQuery(this, super.createQuery(jpqlString));
    }

    @Override
    public <T> T getReference(final Class<T> entityClass, final Object primaryKey) {
        final T returnValue = super.getReference(entityClass, primaryKey);
        this.clear();
        return returnValue;
    }

    @Override
    public StoredProcedureQuery createNamedStoredProcedureQuery(final String name) {
        return new ClearingStoredProcedureQuery(this, super.createNamedStoredProcedureQuery(name));
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(final String procedureName) {
        return new ClearingStoredProcedureQuery(this, super.createStoredProcedureQuery(procedureName));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public StoredProcedureQuery createStoredProcedureQuery(final String procedureName, final Class... resultClasses) {
        return new ClearingStoredProcedureQuery(this, super.createStoredProcedureQuery(procedureName, resultClasses));
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(final String procedureName, final String... resultSetMappings) {
        return new ClearingStoredProcedureQuery(this, super.createStoredProcedureQuery(procedureName, resultSetMappings));
    }
    
    /**
     * Throws a {@link TransactionRequiredException} when invoked.
     *
     * @param entity ignored
     *
     * @exception TransactionRequiredException when invoked
     */
    @Override
    public void persist(final Object entity) {
        throw new TransactionRequiredException();
    }

    /**
     * Throws a {@link TransactionRequiredException} when invoked.
     *
     * @param entity ignored
     *
     * @exception TransactionRequiredException when invoked
     */
    @Override
    public <T> T merge(final T entity) {
        throw new TransactionRequiredException();
    }

    /**
     * Throws a {@link TransactionRequiredException} when invoked.
     *
     * @param entity ignored
     *
     * @exception TransactionRequiredException when invoked
     */
    @Override
    public void remove(final Object entity) {
        throw new TransactionRequiredException();
    }

    /**
     * Throws a {@link TransactionRequiredException} when invoked.
     *
     * @param entity ignored
     *
     * @exception TransactionRequiredException when invoked
     */
    @Override
    public void refresh(final Object entity) {
        throw new TransactionRequiredException();
    }

    /**
     * Throws a {@link TransactionRequiredException} when invoked.
     *
     * @param entity ignored
     *
     * @param properties ignored
     *
     * @exception TransactionRequiredException when invoked
     */
    @Override
    public void refresh(final Object entity,
                        final Map<String, Object> properties) {
        throw new TransactionRequiredException();
    }

    /**
     * Throws a {@link TransactionRequiredException} when invoked.
     *
     * @param entity ignored
     *
     * @param lockMode ignored
     *
     * @exception TransactionRequiredException when invoked
     */
    @Override
    public void refresh(final Object entity,
                        final LockModeType lockMode) {
        throw new TransactionRequiredException();
    }

    /**
     * Throws a {@link TransactionRequiredException} when invoked.
     *
     * @param entity ignored
     *
     * @param lockMode ignored
     *
     * @param properties ignored
     *
     * @exception TransactionRequiredException when invoked
     */
    @Override
    public void refresh(final Object entity,
                        final LockModeType lockMode,
                        final Map<String, Object> properties) {
        throw new TransactionRequiredException();
    }

    /**
     * Throws a {@link TransactionRequiredException} when invoked.
     *
     * @exception TransactionRequiredException when invoked
     */
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
