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

import java.util.List;
import java.util.Map;

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.StoredProcedureQuery;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.metamodel.Metamodel;

/**
 * A partial {@link EntityManager} implementation that forwards all
 * calls to an underlying {@link EntityManager}.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>As with all {@link EntityManager} implementations, instances of
 * this class are not safe for concurrent use by multiple threads.</p>
 */
abstract class DelegatingEntityManager implements EntityManager, AutoCloseable {


    /*
     * Instance fields.
     */


    /**
     * The {@link EntityManager} to which all operations will be
     * forwarded if it is non-{@code null}.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see #DelegatingEntityManager(EntityManager)
     *
     * @see #delegate()
     *
     * @see #acquireDelegate()
     */
    private final EntityManager delegate;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link DelegatingEntityManager} that will
     * indirectly invoke the {@link #acquireDelegate()} method as part
     * of each method invocation to acquire its delegate.
     *
     * @see #delegate()
     *
     * @see #acquireDelegate()
     */
    DelegatingEntityManager() {
        this(null);
    }

    /**
     * Creates a new {@link DelegatingEntityManager}.
     *
     * @param delegate the {@link EntityManager} to which all
     * operations may be forwarded (but see the {@link #delegate()}
     * method); may be {@code null} in which case the {@link
     * #acquireDelegate()} method will be used to supply a delegate
     * for each method call
     *
     * @see #delegate()
     *
     * @see #acquireDelegate()
     */
    DelegatingEntityManager(final EntityManager delegate) {
        super();
        this.delegate = delegate;
    }


    /*
     * Instance methods.
     */


    /**
     * Returns the {@link EntityManager} to which a method invocation
     * should be forwarded.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>This method will call the {@link #acquireDelegate()} method
     * if a {@code null} delegate {@link EntityManager} was
     * {@linkplain #DelegatingEntityManager(EntityManager) supplied at
     * construction time}.</p>
     *
     * @return an {@link EntityManager}; never {@code null}
     *
     * @exception javax.persistence.PersistenceException if an error
     * occurs
     *
     * @see #acquireDelegate()
     *
     * @see #DelegatingEntityManager(EntityManager)
     */
    protected EntityManager delegate() {
        final EntityManager returnValue;
        if (this.delegate == null) {
            returnValue = this.acquireDelegate();
        } else {
            returnValue = this.delegate;
        }
        return returnValue;
    }

    /**
     * Returns an {@link EntityManager} to which all operations will
     * be forwarded.
     *
     * <p>Implementations of this method must not return {@code
     * null}.</p>
     *
     * <p>This method is called by the {@link #delegate()} method and
     * potentially on every method invocation of instances of this
     * class so implementations of it should be as fast as
     * possible.</p>
     *
     * <p>Implementations of this method must not call the {@link
     * #delegate()} method.</p>
     *
     * @return a non-{@code null} {@link EntityManager}
     *
     * @exception javax.persistence.PersistenceException if an error
     * occurs
     *
     * @see #delegate()
     *
     * @see #DelegatingEntityManager(EntityManager)
     */
    protected abstract EntityManager acquireDelegate();

    @Override
    public void persist(final Object entity) {
        this.delegate().persist(entity);
    }

    @Override
    public <T> T merge(final T entity) {
        return this.delegate().merge(entity);
    }

    @Override
    public void remove(final Object entity) {
        this.delegate().remove(entity);
    }

    @Override
    public <T> T find(final Class<T> entityClass, final Object primaryKey) {
        return this.delegate().find(entityClass, primaryKey);
    }

    @Override
    public <T> T find(final Class<T> entityClass, final Object primaryKey, final Map<String, Object> properties) {
        return this.delegate().find(entityClass, primaryKey, properties);
    }

    @Override
    public <T> T find(final Class<T> entityClass, final Object primaryKey, final LockModeType lockMode) {
        return this.delegate().find(entityClass, primaryKey, lockMode);
    }

    @Override
    public <T> T find(final Class<T> entityClass,
                      final Object primaryKey,
                      final LockModeType lockMode,
                      final Map<String, Object> properties) {
        return this.delegate().find(entityClass, primaryKey, lockMode, properties);
    }

    @Override
    public <T> T getReference(final Class<T> entityClass, final Object primaryKey) {
        return this.delegate().getReference(entityClass, primaryKey);
    }

    @Override
    public void flush() {
        this.delegate().flush();
    }

    @Override
    public void setFlushMode(final FlushModeType flushMode) {
        this.delegate().setFlushMode(flushMode);
    }

    @Override
    public FlushModeType getFlushMode() {
        return this.delegate().getFlushMode();
    }

    @Override
    public void lock(final Object entity,
                     final LockModeType lockMode) {
        this.delegate().lock(entity, lockMode);
    }

    @Override
    public void lock(final Object entity,
                     final LockModeType lockMode,
                     final Map<String, Object> properties) {
        this.delegate().lock(entity, lockMode, properties);
    }

    @Override
    public void refresh(final Object entity) {
        this.delegate().refresh(entity);
    }

    @Override
    public void refresh(final Object entity,
                        final Map<String, Object> properties) {
        this.delegate().refresh(entity, properties);
    }

    @Override
    public void refresh(final Object entity,
                        final LockModeType lockMode) {
        this.delegate().refresh(entity, lockMode);
    }

    @Override
    public void refresh(final Object entity,
                        final LockModeType lockMode,
                        final Map<String, Object> properties) {
        this.delegate().refresh(entity, lockMode, properties);
    }

    @Override
    public void clear() {
        this.delegate().clear();
    }

    @Override
    public void detach(final Object entity) {
        this.delegate().detach(entity);
    }

    @Override
    public boolean contains(final Object entity) {
        return this.delegate().contains(entity);
    }

    @Override
    public LockModeType getLockMode(final Object entity) {
        return this.delegate().getLockMode(entity);
    }

    @Override
    public void setProperty(final String propertyName, final Object propertyValue) {
        this.delegate().setProperty(propertyName, propertyValue);
    }

    @Override
    public Map<String, Object> getProperties() {
        return this.delegate().getProperties();
    }

    @Override
    public Query createQuery(final String qlString) {
        return this.delegate().createQuery(qlString);
    }

    @Override
    public <T> TypedQuery<T> createQuery(final CriteriaQuery<T> criteriaQuery) {
        return this.delegate().createQuery(criteriaQuery);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Query createQuery(final CriteriaUpdate criteriaUpdate) {
        return this.delegate().createQuery(criteriaUpdate);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Query createQuery(final CriteriaDelete criteriaDelete) {
        return this.delegate().createQuery(criteriaDelete);
    }

    @Override
    public <T> TypedQuery<T> createQuery(final String qlString, final Class<T> resultClass) {
        return this.delegate().createQuery(qlString, resultClass);
    }

    @Override
    public Query createNamedQuery(final String sqlString) {
        return this.delegate().createNamedQuery(sqlString);
    }

    @Override
    public <T> TypedQuery<T> createNamedQuery(final String sqlString, final Class<T> resultClass) {
        return this.delegate().createNamedQuery(sqlString, resultClass);
    }

    @Override
    public Query createNativeQuery(final String sqlString) {
        return this.delegate().createNativeQuery(sqlString);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Query createNativeQuery(final String sqlString, final Class resultClass) {
        return this.delegate().createNativeQuery(sqlString, resultClass);
    }

    @Override
    public Query createNativeQuery(final String sqlString, final String resultSetMapping) {
        return this.delegate().createNativeQuery(sqlString, resultSetMapping);
    }

    @Override
    public StoredProcedureQuery createNamedStoredProcedureQuery(final String procedureName) {
        return this.delegate().createNamedStoredProcedureQuery(procedureName);
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(final String procedureName) {
        return this.delegate().createStoredProcedureQuery(procedureName);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public StoredProcedureQuery createStoredProcedureQuery(final String procedureName, final Class... resultClasses) {
        return this.delegate().createStoredProcedureQuery(procedureName, resultClasses);
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(final String procedureName, final String... resultSetMappings) {
        return this.delegate().createStoredProcedureQuery(procedureName, resultSetMappings);
    }

    @Override
    public void joinTransaction() {
        this.delegate().joinTransaction();
    }

    @Override
    public boolean isJoinedToTransaction() {
        return this.delegate().isJoinedToTransaction();
    }

    @Override
    public <T> T unwrap(final Class<T> c) {
        return this.delegate().unwrap(c);
    }

    @Override
    public Object getDelegate() {
        return this.delegate().getDelegate();
    }

    @Override
    public void close() {
        this.delegate().close();
    }

    @Override
    public boolean isOpen() {
        return this.delegate().isOpen();
    }

    @Override
    public EntityTransaction getTransaction() {
        return this.delegate().getTransaction();
    }

    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        return this.delegate().getEntityManagerFactory();
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return this.delegate().getCriteriaBuilder();
    }

    @Override
    public Metamodel getMetamodel() {
        return this.delegate().getMetamodel();
    }

    @Override
    public <T> EntityGraph<T> createEntityGraph(final Class<T> rootType) {
        return this.delegate().createEntityGraph(rootType);
    }

    @Override
    public EntityGraph<?> createEntityGraph(final String graphName) {
        return this.delegate().createEntityGraph(graphName);
    }

    @Override
    public EntityGraph<?> getEntityGraph(final String graphName) {
        return this.delegate().getEntityGraph(graphName);
    }

    @Override
    public <T> List<EntityGraph<? super T>> getEntityGraphs(final Class<T> entityClass) {
        return this.delegate().getEntityGraphs(entityClass);
    }

    /*
     * Note: this class deliberately does *not* override toString().
     * Container-managed, JPA-transaction-scoped EntityManagers, when
     * a transaction is not present, are supposed to acquire or create
     * a potentially new EntityManager for all non-transactional
     * operations (including toString()).  toString() could easily
     * create hundreds if not thousands of non-transactional
     * EntityManagers "by mistake".  Consequently it is not overridden
     * here.
     */

}
