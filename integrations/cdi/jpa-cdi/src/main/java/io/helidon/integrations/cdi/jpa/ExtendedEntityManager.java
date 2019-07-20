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
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.persistence.EntityManager;

/**
 * A {@link DelegatingEntityManager} created to support extended
 * persistence contexts.
 */
final class ExtendedEntityManager extends DelegatingEntityManager {


    /*
     * Instance fields.
     */


    private final EntityManager delegate;

    private final TransactionSupport transactionSupport;

    private final Set<? extends Annotation> suppliedQualifiers;

    private final BeanManager beanManager;


    /*
     * Constructors.
     */


    ExtendedEntityManager(final Instance<Object> instance,
                          final Set<? extends Annotation> suppliedQualifiers,
                          final BeanManager beanManager) {
        this(EntityManagers.createContainerManagedEntityManager(instance, suppliedQualifiers),
             instance.select(TransactionSupport.class).get(),
             suppliedQualifiers,
             beanManager);

    }

    ExtendedEntityManager(final EntityManager delegate,
                          final TransactionSupport transactionSupport,
                          final Set<? extends Annotation> suppliedQualifiers,
                          final BeanManager beanManager) {
        super();
        this.delegate = Objects.requireNonNull(delegate);
        this.transactionSupport = Objects.requireNonNull(transactionSupport);
        this.suppliedQualifiers = Objects.requireNonNull(suppliedQualifiers);
        this.beanManager = Objects.requireNonNull(beanManager);
        if (!transactionSupport.isActive()) {
            throw new IllegalArgumentException("!transactionSupport.isActive()");
        }
    }


    /*
     * Instance methods.
     */


    /**
     * Acquires and returns the delegate {@link EntityManager} that
     * this {@link ExtendedEntityManager} must use according to the
     * rules for extended persistence contexts spelled out in the JPA
     * specification.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return the delegate {@link EntityManager}; never {@code null}
     */
    @Override
    protected EntityManager acquireDelegate() {

        // In all cases, we return the EntityManager supplied to us at
        // construction time.  In addition, however, we need to join
        // it to any active transaction (see below), checking first to
        // make sure that no *other* EntityManager is currently joined
        // to that transaction.  See section 7.6.3.1 of the JPA
        // specification for details.
        final EntityManager returnValue = this.delegate;

        final Context context = transactionSupport.getContext();
        if (context != null && context.isActive()) {

            // If the Context is active, it means a JTA transaction is
            // in play.  Now we need to see if an EntityManager is
            // already enrolled in it.

            // Look for an EntityManager bean annotated with, among
            // other possible things, @CdiTransactionScoped
            // and @ContainerManaged.
            final Set<Annotation> selectionQualifiers = new HashSet<>(this.suppliedQualifiers);
            selectionQualifiers.remove(Extended.Literal.INSTANCE);
            selectionQualifiers.remove(JpaTransactionScoped.Literal.INSTANCE);
            selectionQualifiers.remove(NonTransactional.Literal.INSTANCE);
            selectionQualifiers.add(CdiTransactionScoped.Literal.INSTANCE);
            selectionQualifiers.add(ContainerManaged.Literal.INSTANCE);

            final Set<Bean<?>> cdiTransactionScopedEntityManagerBeans =
                this.beanManager.getBeans(EntityManager.class,
                                          selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
            assert cdiTransactionScopedEntityManagerBeans != null;
            assert !cdiTransactionScopedEntityManagerBeans.isEmpty();

            final Bean<?> cdiTransactionScopedEntityManagerBean =
                this.beanManager.resolve(cdiTransactionScopedEntityManagerBeans);
            assert cdiTransactionScopedEntityManagerBean != null;

            // Using that bean, check the Context to see if there's
            // already a container-managed EntityManager enrolled in
            // the transaction (without accidentally creating a new
            // one, hence the single-argument Context#get(Contextual)
            // invocation, not the dual-argument
            // Context#get(Contextual, CreationalContext) one).  We
            // have to do this to honor section 7.6.3.1 of the JPA
            // specification.

            if (context.get(cdiTransactionScopedEntityManagerBean) != null) {

                // If there IS already a container-managed
                // EntityManager enrolled in the transaction, we need
                // to follow JPA section 7.6.3.1 and throw an analog
                // of EJBException; see
                // https://github.com/wildfly/wildfly/blob/7f80f0150297bbc418a38e7e23da7cf0431f7c28/jpa/subsystem/src/main/java/org/jboss/as/jpa/container/ExtendedEntityManager.java#L149
                // as an arbitrary example
                throw new CreationException("section 7.6.3.1 violation"); // Revisit: message

            }

            this.delegate.joinTransaction();
        }
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
