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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.integrations.cdi.jpa.TransactionRegistry.CompletionStatus;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import jakarta.persistence.StoredProcedureQuery;
import jakarta.persistence.SynchronizationType;
import jakarta.persistence.TransactionRequiredException;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.metamodel.Metamodel;

import static jakarta.persistence.SynchronizationType.SYNCHRONIZED;
import static jakarta.persistence.SynchronizationType.UNSYNCHRONIZED;

class JtaEntityManager extends DelegatingEntityManager {

    private static final Logger LOGGER = Logger.getLogger(JtaEntityManager.class.getName());

    private static final ThreadLocal<Map<JtaEntityManager, AbsentTransactionEntityManager>> AT_EMS =
        ThreadLocal.withInitial(() -> new HashMap<>(5));

    private final BiFunction<? super SynchronizationType, ? super Map<?, ?>, ? extends EntityManager> emf;

    private final SynchronizationType syncType;

    private final Map<?, ?> properties;

    private final BooleanSupplier activeTransaction;

    private final Consumer<? super Consumer<? super CompletionStatus>> completionListeners;

    private final Function<? super Object, ?> transactionalResourceGetter;

    private final BiConsumer<? super Object, ? super Object> transactionalResourceSetter;

    JtaEntityManager(BooleanSupplier activeTransaction,
                     Consumer<? super Consumer<? super CompletionStatus>> completionListeners,
                     Function<? super Object, ?> transactionalResourceGetter,
                     BiConsumer<? super Object, ? super Object> transactionalResourceSetter,
                     BiFunction<? super SynchronizationType, ? super Map<?, ?>, ? extends EntityManager> emf,
                     SynchronizationType syncType,
                     Map<?, ?> properties) {
        super();
        this.activeTransaction = Objects.requireNonNull(activeTransaction, "activeTransaction");
        this.completionListeners = Objects.requireNonNull(completionListeners, "completionListeners");
        this.transactionalResourceGetter = Objects.requireNonNull(transactionalResourceGetter, "transactionalResourceGetter");
        this.transactionalResourceSetter = Objects.requireNonNull(transactionalResourceSetter, "transactionalResourceSetter");
        this.emf = Objects.requireNonNull(emf, "emf");
        // JPA permits null SynchronizationType and properties.
        this.syncType = syncType;
        if (syncType == null) {
            this.properties = properties == null ? null : Map.copyOf(properties);
        } else if (properties == null || properties.isEmpty()) {
            this.properties = Map.of(SynchronizationType.class.getName(), syncType);
        } else {
            Map<Object, Object> m = new LinkedHashMap<>(properties);
            m.put(SynchronizationType.class.getName(), syncType);
            this.properties = Collections.unmodifiableMap(m);
        }
    }

    void dispose() {
        AbsentTransactionEntityManager em = AT_EMS.get().remove(this);
        if (em != null) {
            em.closeDelegate();
        }
    }

    @Override
    EntityManager acquireDelegate() {
        try {
            return
                this.activeTransaction.getAsBoolean()
                ? this.computeIfAbsentForActiveTransaction()
                : this.computeIfAbsentForNoTransaction();
        } catch (PersistenceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PersistenceException(e.getMessage(), e);
        }
    }

    private ActiveTransactionEntityManager computeIfAbsentForActiveTransaction() {
        ActiveTransactionEntityManager em = (ActiveTransactionEntityManager) this.transactionalResourceGetter.apply(this);
        if (em == null) {
            ActiveTransactionEntityManager newEm =
                new ActiveTransactionEntityManager(this.emf.apply(this.syncType, this.properties));
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, this.getClass().getName(), "computeIfAbsentForActiveTransaction",
                            "Created ActiveTransactionEntityManager delegate ({0})", newEm);
            }
            em = newEm;
            Object thread = Thread.currentThread();
            try {
                this.completionListeners.accept((Consumer<? super CompletionStatus>) cts -> {
                        // Remember, this can be invoked asynchronously.
                        if (Thread.currentThread() == thread) {
                            newEm.closeDelegate();
                        } else {
                            newEm.closePending = true; // volatile write
                        }
                    });
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, this.getClass().getName(), "computeIfAbsentForActiveTransaction",
                                "Registered listener to close delegate ({0}) upon transaction completion", newEm);
                }
                this.transactionalResourceSetter.accept(this, em);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, this.getClass().getName(), "computeIfAbsentForActiveTransaction",
                                "Registered delegate ({0})", em);
                }
            } catch (RuntimeException | Error e) {
                try {
                    em.closeDelegate();
                } catch (RuntimeException | Error e2) {
                    e.addSuppressed(e2);
                }
                throw e;
            }
        } else if ((this.syncType == null || this.syncType == SYNCHRONIZED) && synchronizationType(em) == UNSYNCHRONIZED) {
            // Check for mixed synchronization types per section 7.6.4.1
            // (https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#a11820):
            //
            // "If there is a persistence context of type SynchronizationType.UNSYNCHRONIZED associated with the JTA
            // transaction and the target component specifies a persistence context of type
            // SynchronizationType.SYNCHRONIZED, the IllegalStateException is thrown by the container."
            throw new IllegalStateException("SynchronizationType.UNSYNCHRONIZED EntityManager already associated");
        }
        return em;
    }

    private AbsentTransactionEntityManager computeIfAbsentForNoTransaction() {
        Map<JtaEntityManager, AbsentTransactionEntityManager> ems = AT_EMS.get();
        AbsentTransactionEntityManager em = ems.get(this);
        if (em == null) {
            // This AbsentTransactionEntityManager is closed by the dispose() method above.
            em = new AbsentTransactionEntityManager(this.emf.apply(syncType, properties));
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, this.getClass().getName(), "computeIfAbsentForNoTransaction",
                            "Created AbsentTransactionEntityManager delegate ({0})", em);
            }
            ems.put(this, em);
        }
        return em;
    }


    /*
     * Static methods.
     */


    private static SynchronizationType synchronizationType(EntityManager em) {
        Map<?, ?> properties = em.getProperties();
        return properties == null ? null : (SynchronizationType) properties.get(SynchronizationType.class.getName());
    }


    /*
     * Inner and nested classes.
     */


    private static final class ActiveTransactionEntityManager extends DelegatingEntityManager {

        private volatile boolean closePending;

        private ActiveTransactionEntityManager(EntityManager delegate) {
            super(Objects.requireNonNull(delegate, "delegate"));
        }

        private void closeIfPending() {
            if (this.closePending) {
                super.close();
            }
        }

        @Override
        public void persist(Object entity) {
            this.closeIfPending();
            super.persist(entity);
        }

        @Override
        public <T> T merge(T entity) {
            this.closeIfPending();
            return super.merge(entity);
        }

        @Override
        public void remove(Object entity) {
            this.closeIfPending();
            super.remove(entity);
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey) {
            this.closeIfPending();
            return super.find(entityClass, primaryKey);
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
            this.closeIfPending();
            return super.find(entityClass, primaryKey, properties);
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode) {
            this.closeIfPending();
            return super.find(entityClass, primaryKey, lockMode);
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode, Map<String, Object> properties) {
            this.closeIfPending();
            return super.find(entityClass, primaryKey, lockMode, properties);
        }

        @Override
        public <T> T getReference(Class<T> entityClass, Object primaryKey) {
            this.closeIfPending();
            return super.getReference(entityClass, primaryKey);
        }

        @Override
        public void flush() {
            this.closeIfPending();
            super.flush();
        }

        @Override
        public void setFlushMode(FlushModeType flushMode) {
            this.closeIfPending();
            super.setFlushMode(flushMode);
        }

        @Override
        public FlushModeType getFlushMode() {
            this.closeIfPending();
            return super.getFlushMode();
        }

        @Override
        public void lock(Object entity, LockModeType lockMode) {
            this.closeIfPending();
            super.lock(entity, lockMode);
        }

        @Override
        public void lock(Object entity, LockModeType lockMode, Map<String, Object> properties) {
            this.closeIfPending();
            super.lock(entity, lockMode, properties);
        }

        @Override
        public void refresh(Object entity) {
            this.closeIfPending();
            super.refresh(entity);
        }

        @Override
        public void refresh(Object entity, Map<String, Object> properties) {
            this.closeIfPending();
            super.refresh(entity, properties);
        }

        @Override
        public void refresh(Object entity, LockModeType lockMode) {
            this.closeIfPending();
            super.refresh(entity, lockMode);
        }

        @Override
        public void refresh(Object entity, LockModeType lockMode, Map<String, Object> properties) {
            this.closeIfPending();
            super.refresh(entity, lockMode, properties);
        }

        @Override
        public void clear() {
            this.closeIfPending();
            super.clear();
        }

        @Override
        public void detach(Object entity) {
            this.closeIfPending();
            super.detach(entity);
        }

        @Override
        public boolean contains(Object entity) {
            this.closeIfPending();
            return super.contains(entity);
        }

        @Override
        public LockModeType getLockMode(Object entity) {
            this.closeIfPending();
            return super.getLockMode(entity);
        }

        @Override
        public void setProperty(String propertyName, Object propertyValue) {
            this.closeIfPending();
            super.setProperty(propertyName, propertyValue);
        }

        @Override
        public Map<String, Object> getProperties() {
            this.closeIfPending();
            return super.getProperties();
        }

        @Override
        public Query createQuery(String qlString) {
            this.closeIfPending();
            return super.createQuery(qlString);
        }

        @Override
        public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
            this.closeIfPending();
            return super.createQuery(criteriaQuery);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Query createQuery(CriteriaUpdate criteriaUpdate) {
            this.closeIfPending();
            return super.createQuery(criteriaUpdate);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Query createQuery(CriteriaDelete criteriaDelete) {
            this.closeIfPending();
            return super.createQuery(criteriaDelete);
        }

        @Override
        public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
            this.closeIfPending();
            return super.createQuery(qlString, resultClass);
        }

        @Override
        public Query createNamedQuery(String sqlString) {
            this.closeIfPending();
            return super.createNamedQuery(sqlString);
        }

        @Override
        public <T> TypedQuery<T> createNamedQuery(String sqlString, Class<T> resultClass) {
            this.closeIfPending();
            return super.createNamedQuery(sqlString, resultClass);
        }

        @Override
        public Query createNativeQuery(String sqlString) {
            this.closeIfPending();
            return super.createNativeQuery(sqlString);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Query createNativeQuery(String sqlString, Class resultClass) {
            this.closeIfPending();
            return super.createNativeQuery(sqlString, resultClass);
        }

        @Override
        public Query createNativeQuery(String sqlString, String resultSetMapping) {
            this.closeIfPending();
            return super.createNativeQuery(sqlString, resultSetMapping);
        }

        @Override
        public StoredProcedureQuery createNamedStoredProcedureQuery(String procedureName) {
            this.closeIfPending();
            return super.createNamedStoredProcedureQuery(procedureName);
        }

        @Override
        public StoredProcedureQuery createStoredProcedureQuery(String procedureName) {
            this.closeIfPending();
            return super.createStoredProcedureQuery(procedureName);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class... resultClasses) {
            this.closeIfPending();
            return super.createStoredProcedureQuery(procedureName, resultClasses);
        }

        @Override
        public StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
            this.closeIfPending();
            return super.createStoredProcedureQuery(procedureName, resultSetMappings);
        }

        @Override
        public void joinTransaction() {
            this.closeIfPending();
            super.joinTransaction();
        }

        @Override
        public boolean isJoinedToTransaction() {
            this.closeIfPending();
            return super.isJoinedToTransaction();
        }

        @Override
        public <T> T unwrap(Class<T> c) {
            this.closeIfPending();
            if (c != null && c.isInstance(this)) {
                return c.cast(this);
            }
            return super.unwrap(c);
        }

        @Override
        public Object getDelegate() {
            this.closeIfPending();
            return super.getDelegate();
        }

        private void closeDelegate() {
            super.close();
            this.closePending = false;
        }

        @Override
        public void close() {
            if (this.isOpen()) {
                throw new IllegalStateException("close() cannot be called on a container-managed EntityManager");
            }
            super.close();
        }

        @Override
        public boolean isOpen() {
            this.closeIfPending();
            return super.isOpen();
        }

        @Override
        public EntityTransaction getTransaction() {
            this.closeIfPending();
            return super.getTransaction();
        }

        @Override
        public EntityManagerFactory getEntityManagerFactory() {
            this.closeIfPending();
            return super.getEntityManagerFactory();
        }

        @Override
        public CriteriaBuilder getCriteriaBuilder() {
            this.closeIfPending();
            return super.getCriteriaBuilder();
        }

        @Override
        public Metamodel getMetamodel() {
            this.closeIfPending();
            return super.getMetamodel();
        }

        @Override
        public <T> EntityGraph<T> createEntityGraph(Class<T> rootType) {
            this.closeIfPending();
            return super.createEntityGraph(rootType);
        }

        @Override
        public EntityGraph<?> createEntityGraph(String graphName) {
            this.closeIfPending();
            return super.createEntityGraph(graphName);
        }

        @Override
        public EntityGraph<?> getEntityGraph(String graphName) {
            this.closeIfPending();
            return super.getEntityGraph(graphName);
        }

        @Override
        public <T> List<EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) {
            this.closeIfPending();
            return super.getEntityGraphs(entityClass);
        }

    }

    private static final class ClearingQuery extends DelegatingQuery {

        private final Runnable clearer;

        private ClearingQuery(Runnable persistenceContextClearer, Query delegate) {
            super(delegate);
            this.clearer = Objects.requireNonNull(persistenceContextClearer, "persistenceContextClearer");
        }

        @Override
        @SuppressWarnings("rawtypes")
        public List getResultList() {
            try {
                return super.getResultList();
            } finally {
                this.clearer.run();
            }
        }

        @Override
        public Object getSingleResult() {
            try {
                return super.getSingleResult();
            } finally {
                this.clearer.run();
            }
        }

    }

    private static final class ClearingStoredProcedureQuery extends DelegatingStoredProcedureQuery {

        private final Runnable clearer;

        private ClearingStoredProcedureQuery(Runnable persistenceContextClearer, StoredProcedureQuery delegate) {
            super(delegate);
            this.clearer = Objects.requireNonNull(persistenceContextClearer, "persistenceContextClearer");
        }

        @Override
        @SuppressWarnings("rawtypes")
        public List getResultList() {
            try {
                return super.getResultList();
            } finally {
                this.clearer.run();
            }
        }

        @Override
        public Object getSingleResult() {
            try {
                return super.getSingleResult();
            } finally {
                this.clearer.run();
            }
        }

    }

    private static final class ClearingTypedQuery<X> extends DelegatingTypedQuery<X> {

        private final Runnable clearer;

        private ClearingTypedQuery(Runnable persistenceContextClearer, TypedQuery<X> delegate) {
            super(delegate);
            this.clearer = Objects.requireNonNull(persistenceContextClearer, "persistenceContextClearer");
        }

        @Override
        public List<X> getResultList() {
            try {
                return super.getResultList();
            } finally {
                this.clearer.run();
            }
        }

        @Override
        public X getSingleResult() {
            try {
                return super.getSingleResult();
            } finally {
                this.clearer.run();
            }
        }

    }

    private static final class AbsentTransactionEntityManager extends DelegatingEntityManager {

        AbsentTransactionEntityManager(EntityManager delegate) {
            super(Objects.requireNonNull(delegate, "delegate"));
        }

        @Override
        public void close() {
            if (this.isOpen()) {
                throw new IllegalStateException("close() cannot be called on a container-managed EntityManager");
            }
            super.close();
        }

        private void closeDelegate() {
            super.close();
        }

        @Override
        public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) {
            return new ClearingTypedQuery<>(this::clear, super.createNamedQuery(name, resultClass));
        }

        @Override
        public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
            return new ClearingTypedQuery<>(this::clear, super.createQuery(criteriaQuery));
        }

        @Override
        public <T> TypedQuery<T> createQuery(String jpql, Class<T> resultClass) {
            return new ClearingTypedQuery<>(this::clear, super.createQuery(jpql, resultClass));
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
            try {
                return super.find(entityClass, primaryKey, properties);
            } finally {
                this.clear();
            }
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey) {
            try {
                return super.find(entityClass, primaryKey);
            } finally {
                this.clear();
            }
        }

        @Override
        public Query createNamedQuery(String name) {
            return new ClearingQuery(this::clear, super.createNamedQuery(name));
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Query createNativeQuery(String sql, Class resultClass) {
            return new ClearingQuery(this::clear, super.createNativeQuery(sql, resultClass));
        }

        @Override
        public Query createNativeQuery(String sql, String resultSetMapping) {
            return new ClearingQuery(this::clear, super.createNativeQuery(sql, resultSetMapping));
        }

        @Override
        public Query createNativeQuery(String sql) {
            return new ClearingQuery(this::clear, super.createNativeQuery(sql));
        }

        @Override
        public Query createQuery(String jpql) {
            return new ClearingQuery(this::clear, super.createQuery(jpql));
        }

        @Override
        public <T> T getReference(Class<T> entityClass, Object primaryKey) {
            try {
                return super.getReference(entityClass, primaryKey);
            } finally {
                this.clear();
            }
        }

        @Override
        public StoredProcedureQuery createNamedStoredProcedureQuery(String name) {
            return new ClearingStoredProcedureQuery(this::clear, super.createNamedStoredProcedureQuery(name));
        }

        @Override
        public StoredProcedureQuery createStoredProcedureQuery(String procedureName) {
            return new ClearingStoredProcedureQuery(this::clear, super.createStoredProcedureQuery(procedureName));
        }

        @Override
        @SuppressWarnings("rawtypes")
        public StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class... resultClasses) {
            return new ClearingStoredProcedureQuery(this::clear, super.createStoredProcedureQuery(procedureName, resultClasses));
        }

        @Override
        public StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
            return
                new ClearingStoredProcedureQuery(this::clear, super.createStoredProcedureQuery(procedureName, resultSetMappings));
        }

        /**
         * Returns {@code false} when invoked.
         *
         * @return {@code false} in all cases
         */
        @Override
        public boolean isJoinedToTransaction() {
            return false;
        }

        /**
         * Throws a {@link TransactionRequiredException} when invoked.
         *
         * @exception TransactionRequiredException when invoked
         */
        @Override
        public void joinTransaction() {
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
        public void persist(Object entity) {
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
        public <T> T merge(T entity) {
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
        public void remove(Object entity) {
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
        public void refresh(Object entity) {
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
        public void refresh(Object entity, Map<String, Object> properties) {
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
        public void refresh(Object entity, LockModeType lockMode) {
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
        public void refresh(Object entity, LockModeType lockMode, Map<String, Object> properties) {
            throw new TransactionRequiredException();
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode) {
            if (lockMode != null && lockMode != LockModeType.NONE) {
                throw new TransactionRequiredException();
            }
            try {
                return super.find(entityClass, primaryKey, lockMode);
            } finally {
                this.clear();
            }
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode, Map<String, Object> properties) {
            if (lockMode != null && !lockMode.equals(LockModeType.NONE)) {
                throw new TransactionRequiredException();
            }
            try {
                return super.find(entityClass, primaryKey, lockMode, properties);
            } finally {
                this.clear();
            }
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
        public void lock(Object entity, LockModeType lockMode) {
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
        public void lock(Object entity, LockModeType lockMode, Map<String, Object> properties) {
            throw new TransactionRequiredException();
        }

        /**
         * Throws a {@link TransactionRequiredException} when invoked.
         *
         * @param entity ignored
         *
         * @return nothing
         *
         * @exception TransactionRequiredException when invoked
         */
        @Override
        public LockModeType getLockMode(Object entity) {
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

}
