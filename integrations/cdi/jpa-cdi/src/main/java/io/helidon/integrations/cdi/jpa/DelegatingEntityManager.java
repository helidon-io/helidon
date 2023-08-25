/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import jakarta.persistence.StoredProcedureQuery;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.metamodel.Metamodel;

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


    private final Supplier<? extends EntityManager> supplier;


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
        this((Supplier<? extends EntityManager>) null);
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
    DelegatingEntityManager(EntityManager delegate) {
        this(delegate == null ? (Supplier<? extends EntityManager>) null : () -> delegate);
    }

    DelegatingEntityManager(Supplier<? extends EntityManager> supplier) {
        super();
        this.supplier = supplier == null ? this::acquireDelegate : supplier;
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
     * @return an {@link EntityManager}
     *
     * @exception PersistenceException if an error occurs
     *
     * @see #acquireDelegate()
     *
     * @see #DelegatingEntityManager(EntityManager)
     */
    EntityManager delegate() {
        return this.supplier.get();
    }

    /**
     * Returns an {@link EntityManager} to which all operations will
     * be forwarded.
     *
     * <p>Overrides of this method must not return {@code null}.</p>
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
     * @exception PersistenceException if an error occurs
     *
     * @see #delegate()
     *
     * @see #DelegatingEntityManager(EntityManager)
     */
    EntityManager acquireDelegate() {
        throw new PersistenceException();
    }

    @Override
    public void persist(Object entity) {
        this.delegate().persist(entity);
    }

    @Override
    public <T> T merge(T entity) {
        return this.delegate().merge(entity);
    }

    @Override
    public void remove(Object entity) {
        this.delegate().remove(entity);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey) {
        return this.delegate().find(entityClass, primaryKey);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
        return this.delegate().find(entityClass, primaryKey, properties);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode) {
        return this.delegate().find(entityClass, primaryKey, lockMode);
    }

    @Override
    public <T> T find(Class<T> entityClass,
                      Object primaryKey,
                      LockModeType lockMode,
                      Map<String, Object> properties) {
        return this.delegate().find(entityClass, primaryKey, lockMode, properties);
    }

    @Override
    public <T> T getReference(Class<T> entityClass, Object primaryKey) {
        return this.delegate().getReference(entityClass, primaryKey);
    }

    @Override
    public void flush() {
        this.delegate().flush();
    }

    @Override
    public void setFlushMode(FlushModeType flushMode) {
        this.delegate().setFlushMode(flushMode);
    }

    @Override
    public FlushModeType getFlushMode() {
        return this.delegate().getFlushMode();
    }

    @Override
    public void lock(Object entity,
                     LockModeType lockMode) {
        this.delegate().lock(entity, lockMode);
    }

    @Override
    public void lock(Object entity,
                     LockModeType lockMode,
                     Map<String, Object> properties) {
        this.delegate().lock(entity, lockMode, properties);
    }

    @Override
    public void refresh(Object entity) {
        this.delegate().refresh(entity);
    }

    @Override
    public void refresh(Object entity,
                        Map<String, Object> properties) {
        this.delegate().refresh(entity, properties);
    }

    @Override
    public void refresh(Object entity,
                        LockModeType lockMode) {
        this.delegate().refresh(entity, lockMode);
    }

    @Override
    public void refresh(Object entity,
                        LockModeType lockMode,
                        Map<String, Object> properties) {
        this.delegate().refresh(entity, lockMode, properties);
    }

    @Override
    public void clear() {
        this.delegate().clear();
    }

    @Override
    public void detach(Object entity) {
        this.delegate().detach(entity);
    }

    @Override
    public boolean contains(Object entity) {
        return this.delegate().contains(entity);
    }

    @Override
    public LockModeType getLockMode(Object entity) {
        return this.delegate().getLockMode(entity);
    }

    @Override
    public void setProperty(String propertyName, Object propertyValue) {
        this.delegate().setProperty(propertyName, propertyValue);
    }

    @Override
    public Map<String, Object> getProperties() {
        return this.delegate().getProperties();
    }

    @Override
    public Query createQuery(String qlString) {
        return this.delegate().createQuery(qlString);
    }

    @Override
    public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
        return this.delegate().createQuery(criteriaQuery);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Query createQuery(CriteriaUpdate criteriaUpdate) {
        return this.delegate().createQuery(criteriaUpdate);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Query createQuery(CriteriaDelete criteriaDelete) {
        return this.delegate().createQuery(criteriaDelete);
    }

    @Override
    public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
        return this.delegate().createQuery(qlString, resultClass);
    }

    @Override
    public Query createNamedQuery(String sqlString) {
        return this.delegate().createNamedQuery(sqlString);
    }

    @Override
    public <T> TypedQuery<T> createNamedQuery(String sqlString, Class<T> resultClass) {
        return this.delegate().createNamedQuery(sqlString, resultClass);
    }

    @Override
    public Query createNativeQuery(String sqlString) {
        return this.delegate().createNativeQuery(sqlString);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Query createNativeQuery(String sqlString, Class resultClass) {
        return this.delegate().createNativeQuery(sqlString, resultClass);
    }

    @Override
    public Query createNativeQuery(String sqlString, String resultSetMapping) {
        return this.delegate().createNativeQuery(sqlString, resultSetMapping);
    }

    @Override
    public StoredProcedureQuery createNamedStoredProcedureQuery(String procedureName) {
        return this.delegate().createNamedStoredProcedureQuery(procedureName);
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName) {
        return this.delegate().createStoredProcedureQuery(procedureName);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class... resultClasses) {
        return this.delegate().createStoredProcedureQuery(procedureName, resultClasses);
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
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
    public <T> T unwrap(Class<T> c) {
        if (c != null && c.isInstance(this)) {
            return c.cast(this);
        }
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
    public <T> EntityGraph<T> createEntityGraph(Class<T> rootType) {
        return this.delegate().createEntityGraph(rootType);
    }

    @Override
    public EntityGraph<?> createEntityGraph(String graphName) {
        return this.delegate().createEntityGraph(graphName);
    }

    @Override
    public EntityGraph<?> getEntityGraph(String graphName) {
        return this.delegate().getEntityGraph(graphName);
    }

    @Override
    public <T> List<EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) {
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
