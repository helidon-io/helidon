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

final class ExtendedEntityManager extends DelegatingEntityManager {

    private final EntityManager delegate;

    private final TransactionSupport transactionSupport;

    private final Set<? extends Annotation> suppliedQualifiers;

    private final BeanManager beanManager;

    ExtendedEntityManager(final Instance<Object> instance,
                          final Set<? extends Annotation> suppliedQualifiers,
                          final BeanManager beanManager) {
        this(EntityManagerFactories.createContainerManagedEntityManager(instance, suppliedQualifiers),
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

    @Override
    protected EntityManager acquireDelegate() {
        final EntityManager returnValue;
        final Context context = transactionSupport.getContext();
        if (context != null && context.isActive()) {
            final Set<Annotation> qualifiers = new HashSet<>(this.suppliedQualifiers);
            qualifiers.remove(Extended.Literal.INSTANCE);
            qualifiers.remove(JPATransactionScoped.Literal.INSTANCE);
            qualifiers.add(CDITransactionScoped.Literal.INSTANCE);
            qualifiers.add(ContainerManaged.Literal.INSTANCE);
            final Set<Bean<?>> cdiTransactionScopedEntityManagerBeans =
                this.beanManager.getBeans(EntityManager.class, qualifiers.toArray(new Annotation[qualifiers.size()]));
            final Bean<?> cdiTransactionScopedEntityManagerBean =
                this.beanManager.resolve(cdiTransactionScopedEntityManagerBeans);
            // This is a little dicey.
            if (context.get(cdiTransactionScopedEntityManagerBean) != null) {
                // JPA section 7.6.3.1; see
                // https://github.com/wildfly/wildfly/blob/7f80f0150297bbc418a38e7e23da7cf0431f7c28/jpa/subsystem/src/main/java/org/jboss/as/jpa/container/ExtendedEntityManager.java#L149
                // as an arbitrary example
                throw new CreationException("section 7.6.3.1 violation"); // Revisit: message
            }
            this.delegate.joinTransaction();
        }
        returnValue = this.delegate;
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
