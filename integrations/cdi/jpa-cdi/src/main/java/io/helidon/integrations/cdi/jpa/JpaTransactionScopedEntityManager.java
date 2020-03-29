/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.StoredProcedureQuery;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.metamodel.Metamodel;

import io.helidon.integrations.cdi.referencecountedcontext.ReferenceCountedContext;

/**
 * A {@link DelegatingEntityManager} that adheres to the JPA
 * specification's rules for transaction-scoped {@link
 * EntityManager}s.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>As with all {@link EntityManager} implementations, instances of
 * this class are not safe for concurrent use by multiple threads.</p>
 */
@Vetoed
final class JpaTransactionScopedEntityManager extends DelegatingEntityManager {


    /*
     * Instance fields.
     */


    private final BeanManager beanManager;

    private final Instance<Object> instance;

    private final TransactionSupport transactionSupport;

    private final Bean<NonTransactionalEntityManager> nonTransactionalEntityManagerBean;

    private final NonTransactionalEntityManager nonTransactionalEntityManager;

    private final int startingReferenceCount;

    private final Bean<?> oppositeSynchronizationBean;

    private final CdiTransactionScopedEntityManager cdiTransactionScopedEntityManager;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JpaTransactionScopedEntityManager}.
     *
     * @param instance an {@link Instance} representing the CDI
     * container; must not be {@code null}
     *
     * @param suppliedQualifiers a {@link Set} of qualifier {@link
     * Annotation}s; must not be {@code null}
     */
    JpaTransactionScopedEntityManager(final Instance<Object> instance,
                                      final Set<? extends Annotation> suppliedQualifiers) {
        super();
        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);
        this.transactionSupport = instance.select(TransactionSupport.class).get();
        if (!transactionSupport.isEnabled()) {
            throw new IllegalArgumentException("!instance.select(TransactionSupport.class).get().isEnabled()");
        }

        this.beanManager = instance.select(BeanManager.class).get();

        this.instance = instance;

        // This large block is devoted to honoring the slightly odd
        // provision in the JPA specification that there be only one
        // transaction-scoped EntityManager for a given transaction.
        // The EntityManager for a given transaction may, of course,
        // be SynchronizationType.SYNCHRONIZED or
        // SynchronizationType.UNSYNCHRONIZED.  Which one it is is
        // determined by who "gets there first".  That is, by spec, if
        // someone requests a synchronized transaction scoped entity
        // manager, then the EntityManager that is established for the
        // transaction will happen to be synchronized.  If someone
        // else somehow wants to get their hands on an unsynchronized
        // transaction scoped EntityManager, we have to detect this
        // mixed synchronization case and throw an error.
        //
        // The mixed synchronization detection has to happen on each
        // and every EntityManager method invocation, by spec. (!)
        //
        // So here we establish the necessary Beans and Instances to
        // get handles on the @Synchronized @CdiTransaction scoped
        // bean and the @Unsynchronized @CdiTransactionScoped bean
        // relevant to the other qualifiers that are present
        // (e.g. @Named("test")).  In acquireDelegate(), below, we'll
        // use these "handles" to do the mixed synchronization
        // testing.
        final Set<Annotation> selectionQualifiers = new HashSet<>(suppliedQualifiers);
        selectionQualifiers.remove(Any.Literal.INSTANCE);
        selectionQualifiers.remove(Default.Literal.INSTANCE);
        selectionQualifiers.remove(Extended.Literal.INSTANCE);
        selectionQualifiers.remove(JpaTransactionScoped.Literal.INSTANCE);
        selectionQualifiers.remove(NonTransactional.Literal.INSTANCE);
        selectionQualifiers.add(CdiTransactionScoped.Literal.INSTANCE);
        selectionQualifiers.add(ContainerManaged.Literal.INSTANCE);
        final Annotation[] cdiTransactionScopedEntityManagerSelectionQualifiersArray;
        if (suppliedQualifiers.contains(Unsynchronized.Literal.INSTANCE)) {
            selectionQualifiers.remove(Synchronized.Literal.INSTANCE);
            selectionQualifiers.add(Unsynchronized.Literal.INSTANCE);
            cdiTransactionScopedEntityManagerSelectionQualifiersArray =
                selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]);
            selectionQualifiers.remove(Unsynchronized.Literal.INSTANCE);
            selectionQualifiers.add(Synchronized.Literal.INSTANCE);
        } else {
            selectionQualifiers.remove(Unsynchronized.Literal.INSTANCE);
            selectionQualifiers.add(Synchronized.Literal.INSTANCE);
            cdiTransactionScopedEntityManagerSelectionQualifiersArray =
                selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]);
            selectionQualifiers.remove(Synchronized.Literal.INSTANCE);
            selectionQualifiers.add(Unsynchronized.Literal.INSTANCE);
        }

        Set<Bean<?>> beans =
            this.beanManager.getBeans(EntityManager.class,
                                      selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
        assert beans != null;
        this.oppositeSynchronizationBean = Objects.requireNonNull(this.beanManager.resolve(beans));

        // This is a proxy whose scope will be
        // javax.transaction.TransactionScoped, and therefore will be
        // lazily "inflated".  In other words, the acquisition of this
        // reference here does not cause a contextual instance to be
        // created.  Invoking any method on it will cause the
        // contextual instance to be created at that point, including
        // toString(), so debug with care.
        this.cdiTransactionScopedEntityManager =
            instance.select(CdiTransactionScopedEntityManager.class,
                            cdiTransactionScopedEntityManagerSelectionQualifiersArray).get();
        assert this.cdiTransactionScopedEntityManager.getClass().isSynthetic();

        selectionQualifiers.remove(CdiTransactionScoped.Literal.INSTANCE);
        selectionQualifiers.remove(ContainerManaged.Literal.INSTANCE);
        selectionQualifiers.remove(Synchronized.Literal.INSTANCE);
        selectionQualifiers.remove(Unsynchronized.Literal.INSTANCE);
        selectionQualifiers.add(NonTransactional.Literal.INSTANCE);

        beans = this.beanManager.getBeans(NonTransactionalEntityManager.class,
                                          selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
        assert beans != null;
        @SuppressWarnings("unchecked")
        final Bean<NonTransactionalEntityManager> nonTransactionalEntityManagerBean =
            (Bean<NonTransactionalEntityManager>) this.beanManager.resolve(beans);
        this.nonTransactionalEntityManagerBean = nonTransactionalEntityManagerBean;
        assert this.nonTransactionalEntityManagerBean != null;
        beans = null;

        final ReferenceCountedContext context = ReferenceCountedContext.getInstanceFrom(this.beanManager);
        assert context != null;
        assert context.isActive(); // it's always active
        this.startingReferenceCount = context.getReferenceCount(this.nonTransactionalEntityManagerBean);

        this.nonTransactionalEntityManager =
            (NonTransactionalEntityManager) this.beanManager.getReference(this.nonTransactionalEntityManagerBean,
                                                                          NonTransactionalEntityManager.class,
                                                                          this.beanManager.createCreationalContext(null)); // fix
    }



    /**
     * Acquires and returns a delegate {@link EntityManager}, adhering
     * to the rules spelled out by the JPA specification around
     * transaction-scoped {@link EntityManager}s.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>If a {@linkplain TransactionSupport#inTransaction() JTA
     * transaction is active}, then an {@link EntityManager} that is
     * joined to it is returned.  Otherwise a non-transactional {@link
     * EntityManager} is returned.</p>
     *
     * <p>Recall that this method is invoked by all {@link
     * DelegatingEntityManager} methods.</p>
     *
     * @return a non-{@code null} {@link EntityManager} that will be
     * used as this {@link JpaTransactionScopedEntityManager}'s
     * delegate
     */
    @Override
    protected EntityManager acquireDelegate() {
        final EntityManager returnValue;
        final int status = this.transactionSupport.getStatus();
        switch (status) {
        case TransactionSupport.STATUS_ACTIVE:
            // If we are or were just in a transaction, then we're
            // obligated to see if there's a transaction-scoped
            // EntityManager already affiliated with the current
            // transaction.  If there is, and its synchronization type
            // doesn't match ours, we're supposed to throw an
            // exception.  Remember that the transaction-scoped
            // context can go inactive at any point due to a rollback
            // on another thread, and may already be inactive at
            // *this* point.  This also means the status that dropped
            // us into this case statement may be stale.
            final Context transactionScopedContext = this.transactionSupport.getContext();
            if (transactionScopedContext == null || !transactionScopedContext.isActive()) {
                returnValue = this.nonTransactionalEntityManager;
            } else {
                EntityManager candidateReturnValue = this.cdiTransactionScopedEntityManager;
                try {
                    final Object existingContextualInstance = transactionScopedContext.get(this.oppositeSynchronizationBean);
                    if (existingContextualInstance != null) {
                        // The Context in question reported that it
                        // has already created (and is therefore
                        // storing) an instance with the "wrong"
                        // synchronization type.  We must throw an
                        // exception.
                        throw new PersistenceException(Messages.format("mixedSynchronizationTypes",
                                                                       this.oppositeSynchronizationBean,
                                                                       existingContextualInstance));
                    }
                } catch (final ContextNotActiveException contextNotActiveException) {
                    candidateReturnValue = this.nonTransactionalEntityManager;
                } finally {
                    returnValue = candidateReturnValue;
                }
            }
            break;
        default:
            returnValue = this.nonTransactionalEntityManager;
            break;
        }
        assert returnValue != null;
        return returnValue;
    }

    /**
     * Throws an {@link IllegalStateException} when invoked.
     *
     * @return nothing
     *
     * @exception IllegalStateException when invoked
     *
     * @see EntityManager#getTransaction()
     */
    @Override
    public EntityTransaction getTransaction() {
        throw new IllegalStateException(Messages.format("jpaTransactionScopedEntityManagerGetTransaction"));
    }

    /**
     * Throws an {@link IllegalStateException} when invoked.
     *
     * @exception IllegalStateException when invoked
     */
    @Override
    public void close() {
        // Wildfly allows end users to close UNSYNCHRONIZED
        // container-managed EntityManagers:
        // https://github.com/wildfly/wildfly/blob/7f80f0150297bbc418a38e7e23da7cf0431f7c28/jpa/subsystem/src/main/java/org/jboss/as/jpa/container/UnsynchronizedEntityManagerWrapper.java#L75-L78
        // I don't know why.  Glassfish does not; it's the reference
        // application; we follow suit:
        // https://github.com/javaee/glassfish/blob/f9e1f6361dcc7998cacccb574feef5b70bf84e23/appserver/common/container-common/src/main/java/com/sun/enterprise/container/common/impl/EntityManagerWrapper.java#L752-L761
        throw new IllegalStateException(Messages.format("jpaTransactionScopedEntityManagerClose"));
    }

    void dispose(final Instance<Object> instance) {
        final ReferenceCountedContext context = ReferenceCountedContext.getInstanceFrom(this.beanManager);
        assert context != null;
        assert context.isActive();
        final int finalReferenceCount = context.getReferenceCount(this.nonTransactionalEntityManagerBean);
        context.decrementReferenceCount(this.nonTransactionalEntityManagerBean,
                                        finalReferenceCount - this.startingReferenceCount);
    }


    /*
     * Overrides that translate ContextNotActiveException.
     *
     * Because the underlying delegate is often a
     * CdiTransactionScopedEntityManager in
     * javax.transaction.TransactionScoped scope, and because that
     * scope's lifetime is equal to the current transaction's
     * lifetime, and because a transaction can roll back at any point
     * due to timeout on a background thread, it is *always* possible
     * that the delegate returned by the delegate() method (and
     * therefore indirectly by the acquireDelegate() method above) is
     * essentially invalid: you can take delivery of it but as soon as
     * you call a method on it it may turn out that the transaction
     * has rolled back.  Your method invocation will thus result in a
     * ContextNotActiveException.
     *
     * In this case we must revert to a non-transactional delegate,
     * i.e. one whose underlying persistence context is always empty.
     * The overrides that follow perform this translation.
     */


    @Override
    public void persist(final Object entity) {
        try {
            super.persist(entity);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.persist(entity);
        }
    }

    @Override
    public <T> T merge(final T entity) {
        try {
            return super.merge(entity);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.merge(entity);
        }
    }

    @Override
    public void remove(final Object entity) {
        try {
            super.remove(entity);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.remove(entity);
        }
    }

    @Override
    public <T> T find(final Class<T> entityClass,
                      final Object primaryKey) {
        try {
            return super.find(entityClass, primaryKey);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.find(entityClass, primaryKey);
        }
    }

    @Override
    public <T> T find(final Class<T> entityClass,
                      final Object primaryKey,
                      final Map<String, Object> properties) {
        try {
            return super.find(entityClass, primaryKey, properties);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.find(entityClass, primaryKey, properties);
        }
    }

    @Override
    public <T> T find(final Class<T> entityClass,
                      final Object primaryKey,
                      final LockModeType lockMode) {
        try {
            return super.find(entityClass, primaryKey, lockMode);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.find(entityClass, primaryKey, lockMode);
        }
    }

    @Override
    public <T> T find(final Class<T> entityClass,
                      final Object primaryKey,
                      final LockModeType lockMode,
                      final Map<String, Object> properties) {
        try {
            return super.find(entityClass, primaryKey, lockMode, properties);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.find(entityClass, primaryKey, lockMode, properties);
        }
    }

    @Override
    public <T> T getReference(final Class<T> entityClass,
                              final Object primaryKey) {
        try {
            return super.getReference(entityClass, primaryKey);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.getReference(entityClass, primaryKey);
        }
    }

    @Override
    public void flush() {
        try {
            super.flush();
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.flush();
        }
    }

    @Override
    public void setFlushMode(final FlushModeType flushMode) {
        try {
            super.setFlushMode(flushMode);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.setFlushMode(flushMode);
        }
    }

    @Override
    public FlushModeType getFlushMode() {
        try {
            return super.getFlushMode();
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.getFlushMode();
        }
    }

    @Override
    public void lock(final Object entity,
                     final LockModeType lockMode) {
        try {
            super.lock(entity, lockMode);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.lock(entity, lockMode);
        }
    }

    @Override
    public void lock(final Object entity,
                     final LockModeType lockMode,
                     final Map<String, Object> properties) {
        try {
            super.lock(entity, lockMode, properties);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.lock(entity, lockMode, properties);
        }
    }

    @Override
    public void refresh(final Object entity) {
        try {
            super.refresh(entity);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.refresh(entity);
        }
    }

    @Override
    public void refresh(final Object entity,
                        final Map<String, Object> properties) {
        try {
            super.refresh(entity, properties);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.refresh(entity, properties);
        }
    }

    @Override
    public void refresh(final Object entity,
                        final LockModeType lockMode) {
        try {
            super.refresh(entity, lockMode);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.refresh(entity, lockMode);
        }
    }

    @Override
    public void refresh(final Object entity,
                        final LockModeType lockMode,
                        final Map<String, Object> properties) {
        try {
            super.refresh(entity, lockMode, properties);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.refresh(entity, lockMode, properties);
        }
    }

    @Override
    public void clear() {
        try {
            super.clear();
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.clear();
        }
    }

    @Override
    public void detach(final Object entity) {
        try {
            super.detach(entity);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.detach(entity);
        }
    }

    @Override
    public boolean contains(final Object entity) {
        try {
            return super.contains(entity);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.contains(entity);
        }
    }

    @Override
    public LockModeType getLockMode(final Object entity) {
        try {
            return super.getLockMode(entity);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.getLockMode(entity);
        }
    }

    @Override
    public Map<String, Object> getProperties() {
        try {
            return super.getProperties();
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.getProperties();
        }
    }

    @Override
    public void setProperty(final String propertyName,
                            final Object propertyValue) {
        try {
            super.setProperty(propertyName, propertyValue);
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.setProperty(propertyName, propertyValue);
        }
    }

    @Override
    public Query createQuery(final String jpql) {
        try {
            return super.createQuery(jpql);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createQuery(jpql);
        }
    }

    @Override
    public <T> TypedQuery<T> createQuery(final CriteriaQuery<T> criteriaQuery) {
        try {
            return super.createQuery(criteriaQuery);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createQuery(criteriaQuery);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Query createQuery(final CriteriaUpdate criteriaUpdate) {
        try {
            return super.createQuery(criteriaUpdate);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createQuery(criteriaUpdate);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Query createQuery(final CriteriaDelete criteriaDelete) {
        try {
            return super.createQuery(criteriaDelete);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createQuery(criteriaDelete);
        }
    }

    @Override
    public <T> TypedQuery<T> createQuery(final String jpql, final Class<T> resultClass) {
        try {
            return super.createQuery(jpql, resultClass);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createQuery(jpql, resultClass);
        }
    }

    @Override
    public Query createNamedQuery(final String sql) {
        try {
            return super.createNamedQuery(sql);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createNamedQuery(sql);
        }
    }

    @Override
    public <T> TypedQuery<T> createNamedQuery(final String sql, final Class<T> resultClass) {
        try {
            return super.createNamedQuery(sql, resultClass);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createNamedQuery(sql, resultClass);
        }
    }

    @Override
    public Query createNativeQuery(final String sql) {
        try {
            return super.createNativeQuery(sql);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createNativeQuery(sql);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Query createNativeQuery(final String sql, final Class resultClass) {
        try {
            return super.createNativeQuery(sql, resultClass);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createNativeQuery(sql, resultClass);
        }
    }

    @Override
    public Query createNativeQuery(final String sql, final String resultSetMapping) {
        try {
            return super.createNativeQuery(sql, resultSetMapping);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createNativeQuery(sql, resultSetMapping);
        }
    }

    @Override
    public StoredProcedureQuery createNamedStoredProcedureQuery(final String procedureName) {
        try {
            return super.createNamedStoredProcedureQuery(procedureName);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createNamedStoredProcedureQuery(procedureName);
        }
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(final String procedureName) {
        try {
            return super.createStoredProcedureQuery(procedureName);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createStoredProcedureQuery(procedureName);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public StoredProcedureQuery createStoredProcedureQuery(final String procedureName,
                                                           final Class... resultClasses) {
        try {
            return super.createStoredProcedureQuery(procedureName, resultClasses);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createStoredProcedureQuery(procedureName, resultClasses);
        }
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(final String procedureName,
                                                           final String... resultSetMappings) {
        try {
            return super.createStoredProcedureQuery(procedureName, resultSetMappings);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createStoredProcedureQuery(procedureName, resultSetMappings);
        }
    }

    @Override
    public void joinTransaction() {
        try {
            super.joinTransaction();
        } catch (final ContextNotActiveException contextNotActiveException) {
            this.nonTransactionalEntityManager.joinTransaction();
        }
    }

    @Override
    public boolean isJoinedToTransaction() {
        try {
            return super.isJoinedToTransaction();
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.isJoinedToTransaction();
        }
    }

    @Override
    public <T> T unwrap(final Class<T> c) {
        try {
            return super.unwrap(c);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.unwrap(c);
        }
    }

    @Override
    public Object getDelegate() {
        try {
            return super.getDelegate();
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.getDelegate();
        }
    }

    @Override
    public boolean isOpen() {
        try {
            return super.isOpen();
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.isOpen();
        }
    }

    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        try {
            return super.getEntityManagerFactory();
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.getEntityManagerFactory();
        }
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        try {
            return super.getCriteriaBuilder();
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.getCriteriaBuilder();
        }
    }

    @Override
    public Metamodel getMetamodel() {
        try {
            return super.getMetamodel();
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.getMetamodel();
        }
    }

    @Override
    public <T> EntityGraph<T> createEntityGraph(final Class<T> rootType) {
        try {
            return super.createEntityGraph(rootType);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createEntityGraph(rootType);
        }
    }

    @Override
    public EntityGraph<?> createEntityGraph(final String graphName) {
        try {
            return super.createEntityGraph(graphName);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.createEntityGraph(graphName);
        }
    }

    @Override
    public EntityGraph<?> getEntityGraph(final String graphName) {
        try {
            return super.getEntityGraph(graphName);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.getEntityGraph(graphName);
        }
    }

    @Override
    public <T> List<EntityGraph<? super T>> getEntityGraphs(final Class<T> entityClass) {
        try {
            return super.getEntityGraphs(entityClass);
        } catch (final ContextNotActiveException contextNotActiveException) {
            return this.nonTransactionalEntityManager.getEntityGraphs(entityClass);
        }
    }

}
