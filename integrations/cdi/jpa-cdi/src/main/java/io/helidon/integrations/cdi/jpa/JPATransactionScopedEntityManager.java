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

import javax.enterprise.inject.Instance;
import javax.inject.Provider;
import javax.persistence.EntityManager;

final class JPATransactionScopedEntityManager extends DelegatingEntityManager {

    private final TransactionSupport transactionSupport;

    private final Provider<EntityManager> cdiTransactionScopedEntityManagerProvider;

    private final Provider<EntityManager> nonTransactionalEntityManagerProvider;

    JPATransactionScopedEntityManager(final Instance<Object> instance,
                                      final Set<? extends Annotation> suppliedQualifiers) {
        super();
        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);
        this.transactionSupport = Objects.requireNonNull(instance.select(TransactionSupport.class).get());
        if (!transactionSupport.isActive()) {
            throw new IllegalArgumentException("!transactionSupport.isActive()");
        }
        this.cdiTransactionScopedEntityManagerProvider =
            Objects.requireNonNull(JpaExtension.getCDITransactionScopedEntityManagerInstance(instance, suppliedQualifiers));
        final Set<Annotation> nonTransactionalQualifiers = new HashSet<>(suppliedQualifiers);
        nonTransactionalQualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
        nonTransactionalQualifiers.add(NonTransactional.Literal.INSTANCE);
        final Annotation[] nonTransactionalQualifiersArray =
            nonTransactionalQualifiers.toArray(new Annotation[nonTransactionalQualifiers.size()]);
        this.nonTransactionalEntityManagerProvider =
            Objects.requireNonNull(instance.select(EntityManager.class, nonTransactionalQualifiersArray));

    }

    @Override
    protected EntityManager acquireDelegate() {
        final EntityManager returnValue;
        if (this.transactionSupport.inTransaction()) {
            returnValue = this.cdiTransactionScopedEntityManagerProvider.get();
        } else {
            returnValue = this.nonTransactionalEntityManagerProvider.get();
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
