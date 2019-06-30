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
import java.util.Objects;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.configurator.BeanConfigurator;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.SynchronizationType;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;

@Deprecated
final class Scratchpad {

  private Scratchpad() {
    super();
  }

  private static void addJPATransactionScopedEntityManager(final BeanConfigurator<EntityManager> beanConfigurator,
                                                           final SynchronizationType synchronizationType,
                                                           final Set<Annotation> qualifiers) {
    Objects.requireNonNull(beanConfigurator);

    beanConfigurator.addType(EntityManager.class)
      .scope(Dependent.class)
      .addQualifiers(qualifiers)
      .produceWith(instance -> {
          final int qualifiersSize = qualifiers.size();

          // Let's pretend qualifiers has @Default and @Any.

          // Revisit: should we pull @Any out forcibly during the
          // select() calls?  Seems like it.

          // Get an Instance that can give us a @Default Transaction.
          final Annotation[] qualifiersArray = qualifiers.toArray(new Annotation[qualifiersSize]);
          final Instance<Transaction> instanceTransaction = instance.select(Transaction.class, qualifiersArray);

          // Get an Instance that can give us
          // a @Default @CDITransactionScoped @Synchronized
          // (or @Unsynchronized) EntityManager.
          final Annotation[] qualifiersWithCdiTransactionScopedArray = new Annotation[qualifiersSize + 2];
          System.arraycopy(qualifiersArray, 0, qualifiersWithCdiTransactionScopedArray, 0, qualifiersArray.length);
          qualifiersWithCdiTransactionScopedArray[qualifiersWithCdiTransactionScopedArray.length - 2] =
            CDITransactionScoped.Literal.INSTANCE;
          if (synchronizationType == null || synchronizationType.equals(SynchronizationType.SYNCHRONIZED)) {
            qualifiersWithCdiTransactionScopedArray[qualifiersWithCdiTransactionScopedArray.length - 1] =
              Synchronized.Literal.INSTANCE;
          } else {
            qualifiersWithCdiTransactionScopedArray[qualifiersWithCdiTransactionScopedArray.length - 1] =
              Unsynchronized.Literal.INSTANCE;
          }
          final Instance<EntityManager> instanceCdiTransactionScopedEntityManager =
            instance.select(EntityManager.class, qualifiersWithCdiTransactionScopedArray);

          // Get an Instance that can give us
          // a @Default @ContainerManaged EntityManagerFactory.
          final Annotation[] qualifiersWithContainerManagedArray = new Annotation[qualifiersSize + 1];
          System.arraycopy(qualifiersArray, 0, qualifiersWithContainerManagedArray, 0, qualifiersArray.length);
          qualifiersWithContainerManagedArray[qualifiersWithContainerManagedArray.length - 1] =
            ContainerManaged.Literal.INSTANCE;
          final Instance<EntityManagerFactory> instanceEntityManagerFactory =
            instance.select(EntityManagerFactory.class, qualifiersWithContainerManagedArray);

          final EntityManager returnValue = new DelegatingEntityManager() {
              @Override
              protected EntityManager delegate() {
                final EntityManager returnValue;
                boolean inTransaction;
                try {
                  inTransaction = inTransaction();
                } catch (final SystemException systemException) {
                  throw new CreationException(systemException.getMessage(), systemException);
                }
                if (inTransaction) {
                  returnValue = instanceCdiTransactionScopedEntityManager.get();
                } else {
                  returnValue = instanceEntityManagerFactory.get().createEntityManager(synchronizationType);
                }
                return returnValue;
              }

              private boolean inTransaction() throws SystemException {
                final boolean returnValue;
                if (instanceTransaction == null || instanceTransaction.isUnsatisfied()) {
                  returnValue = false;
                } else {
                  final Transaction transaction = instanceTransaction.get();
                  returnValue = transaction != null && transaction.getStatus() == Status.STATUS_ACTIVE;
                }
                return returnValue;
              }
            };
          return returnValue;
        });

  }

  private static void addCDITransactionScopedEntityManager(final BeanConfigurator<EntityManager> beanConfigurator,
                                                              final SynchronizationType synchronizationType,
                                                              final Set<Annotation> qualifiers) {
    Objects.requireNonNull(beanConfigurator);

    // Adds an EntityManager that is not wrapped in any way and is in
    // CDI's TransactionScope.  Used as a delegate only.

    beanConfigurator.addType(EntityManager.class)
      .scope(javax.transaction.TransactionScoped.class)
      .addQualifiers(qualifiers)
      .addQualifiers(CDITransactionScoped.Literal.INSTANCE);
    if (synchronizationType == null || synchronizationType.equals(SynchronizationType.SYNCHRONIZED)) {
      beanConfigurator.addQualifiers(Synchronized.Literal.INSTANCE);
    } else {
      beanConfigurator.addQualifiers(Unsynchronized.Literal.INSTANCE);
    }
    beanConfigurator.produceWith(instance -> {
        final Annotation[] qualifiersArray;
        if (qualifiers.contains(ContainerManaged.Literal.INSTANCE)) {
          qualifiersArray = qualifiers.toArray(new Annotation[qualifiers.size()]);
        } else {
          qualifiersArray = qualifiers.toArray(new Annotation[qualifiers.size() + 1]);
          qualifiersArray[qualifiers.size() - 1] = ContainerManaged.Literal.INSTANCE;
        }
        final EntityManagerFactory emf = instance.select(EntityManagerFactory.class, qualifiersArray).get();
        // Revisit: get properties and pass them too
        return emf.createEntityManager(synchronizationType);
      })
      .disposeWith((em, instance) -> {
          em.close();
        });
  }

}
