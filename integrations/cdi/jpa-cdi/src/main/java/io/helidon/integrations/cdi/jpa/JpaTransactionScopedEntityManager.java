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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

/**
 * A {@link DelegatingEntityManager} that adheres to the JPA
 * specification's rules for transaction-scoped {@link
 * EntityManager}s.
 */
final class JpaTransactionScopedEntityManager extends DelegatingEntityManager {


    /*
     * Instance fields.
     */

    private final BeanManager beanManager;

    private final Instance<Object> instance;

    private final TransactionSupport transactionSupport;

    private final Annotation[] cdiTransactionScopedEntityManagerSelectionQualifiersArray;

    private final Provider<EntityManager> nonTransactionalEntityManagerProvider;

    private final boolean isUnsynchronized;

    private final Bean<?> cdiTransactionScopedEntityManagerOppositeSynchronizationBean;


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
        this.transactionSupport = Objects.requireNonNull(instance.select(TransactionSupport.class).get());
        if (!transactionSupport.isActive()) {
            throw new IllegalArgumentException("!instance.select(TransacitonSupport.class).get().isActive()");
        }

        this.beanManager = instance.select(BeanManager.class).get();
        this.instance = instance;
        this.isUnsynchronized = suppliedQualifiers.contains(Unsynchronized.Literal.INSTANCE);

        // This large block is devoted to honoring the slightly odd
        // provision in the JPA specification that there be only one
        // transaction-scoped EntityManager for a given transaction.
        // The EntityManager for a given transaction may, of course,
        // be SynchronizationType.SYNCHRONIZED or
        // SynchronizationType.UNSYNCHRONIZED.  Which one it is is
        // determined by who "gets there first".  That is, by spec, if
        // someone requests a synchronized transaction scoped entity
        // manager, then the entity manager that is established for
        // the transaction will happen to be synchronized.  If someone
        // else somehow wants to get their hands on an unsynchronized
        // transaction scoped entity manager, we have to detect this
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
        if (this.isUnsynchronized) {
            selectionQualifiers.remove(Synchronized.Literal.INSTANCE);
            selectionQualifiers.add(Unsynchronized.Literal.INSTANCE);
            this.cdiTransactionScopedEntityManagerSelectionQualifiersArray =
                selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]);
            selectionQualifiers.remove(Unsynchronized.Literal.INSTANCE);
            selectionQualifiers.add(Synchronized.Literal.INSTANCE);
        } else {
            selectionQualifiers.remove(Unsynchronized.Literal.INSTANCE);
            selectionQualifiers.add(Synchronized.Literal.INSTANCE);
            this.cdiTransactionScopedEntityManagerSelectionQualifiersArray =
                selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]);
            selectionQualifiers.remove(Synchronized.Literal.INSTANCE);
            selectionQualifiers.add(Unsynchronized.Literal.INSTANCE);
        }
        final Set<Bean<?>> beans =
            this.beanManager.getBeans(EntityManager.class,
                                      selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
        assert beans != null;
        assert beans.size() == 1 : "beans.size() != 1: " + beans;
        this.cdiTransactionScopedEntityManagerOppositeSynchronizationBean = this.beanManager.resolve(beans);
        assert this.cdiTransactionScopedEntityManagerOppositeSynchronizationBean != null;

        selectionQualifiers.remove(CdiTransactionScoped.Literal.INSTANCE);
        selectionQualifiers.remove(ContainerManaged.Literal.INSTANCE);
        selectionQualifiers.remove(Synchronized.Literal.INSTANCE);
        selectionQualifiers.remove(Unsynchronized.Literal.INSTANCE);
        selectionQualifiers.add(NonTransactional.Literal.INSTANCE);
        this.nonTransactionalEntityManagerProvider =
            Objects.requireNonNull(instance.select(EntityManager.class,
                                                   selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()])));
        assert this.nonTransactionalEntityManagerProvider != null;
    }

    /**
     * Acquires and returns a delegate {@link EntityManager}, adhering
     * to the rules spelled out by the JPA specification around
     * transaction-scoped entity managers.
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
        if (this.transactionSupport.inTransaction()) {
            // If we're in a transaction, then we're obligated to see
            // if there's a transaction-scoped entity manager already
            // affiliated with the current transaction.  If there is,
            // and its synchronization type doesn't match ours, we're
            // supposed to throw an exception.
            final Context context =
                this.beanManager.getContext(this.cdiTransactionScopedEntityManagerOppositeSynchronizationBean.getScope());
            assert context != null;
            assert context.isActive();
            final Object contextualInstance =
                context.get(this.cdiTransactionScopedEntityManagerOppositeSynchronizationBean);
            if (contextualInstance != null) {
                // The Context in question reported that it has
                // already created (and is therefore storing) an
                // instance with the "wrong" synchronization type.  We
                // must throw an exception.
                throw new PersistenceException(Messages.format("mixedSynchronizationTypes",
                                                               this.cdiTransactionScopedEntityManagerOppositeSynchronizationBean,
                                                               contextualInstance));
            }
            returnValue =
                this.instance.select(EntityManager.class,
                                     this.cdiTransactionScopedEntityManagerSelectionQualifiersArray).get();
        } else {
            returnValue = this.nonTransactionalEntityManagerProvider.get();
        }
        assert returnValue != null;
        return returnValue;
    }

    @Override
    public void close() {
        // Revisit: Wildfly allows end users to close UNSYNCHRONIZED
        // container-managed EntityManagers:
        // https://github.com/wildfly/wildfly/blob/7f80f0150297bbc418a38e7e23da7cf0431f7c28/jpa/subsystem/src/main/java/org/jboss/as/jpa/container/UnsynchronizedEntityManagerWrapper.java#L75-L78
        // I don't know why.  Glassfish does not:
        // https://github.com/javaee/glassfish/blob/f9e1f6361dcc7998cacccb574feef5b70bf84e23/appserver/common/container-common/src/main/java/com/sun/enterprise/container/common/impl/EntityManagerWrapper.java#L752-L761
        throw new IllegalStateException();
    }

}
