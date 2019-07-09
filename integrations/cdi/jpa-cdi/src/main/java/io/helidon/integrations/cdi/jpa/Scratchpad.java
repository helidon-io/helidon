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
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.InjectLiteral;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.ProcessProducer;
import javax.enterprise.inject.spi.Producer;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.configurator.BeanConfigurator;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.persistence.SynchronizationType;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;

import io.helidon.integrations.cdi.delegates.DelegatingInjectionTarget;

/**
 * An area for rough draft sketching around a new approach to JPA
 * integration.
 *
 * @deprecated This class is slated for removal or renaming.  Do not
 * use it.
 */
@Deprecated
public final class Scratchpad implements Extension {

    /**
     * Creates a new {@link Scratchpad}.
     *
     * @deprecated This is not a real class yet.
     */
    @Deprecated
    public Scratchpad() {
        super();
    }

    /**
     * Converts something like {@code @PersistenceContext(unitName =
     * "fred") private EntityManager em;} to
     * {@code @Inject @Named("fred") @Synchronized @JPATransactionScoped
     * private EntityManager em;}.
     */
    private static <T> void processTypesWithPersistenceContextAnnotations(@Observes @WithAnnotations(PersistenceContext.class)
                                                                          final ProcessAnnotatedType<T> event) {

        event.configureAnnotatedType()
            .filterFields(f -> f.isAnnotationPresent(PersistenceContext.class)
                          && !f.isAnnotationPresent(Inject.class))
            .forEach(fc -> {
                    final PersistenceContext pc = fc.getAnnotated().getAnnotation(PersistenceContext.class);
                    assert pc != null;
                    fc.remove(a -> a == pc);
                    fc.add(InjectLiteral.INSTANCE);
                    fc.add(ContainerManaged.Literal.INSTANCE);
                    if (PersistenceContextType.EXTENDED.equals(pc.type())) {
                        fc.add(Extended.Literal.INSTANCE);
                    } else {
                        fc.add(JPATransactionScoped.Literal.INSTANCE);
                    }
                    if (SynchronizationType.UNSYNCHRONIZED.equals(pc.synchronization())) {
                        fc.add(Unsynchronized.Literal.INSTANCE);
                    } else {
                        fc.add(Synchronized.Literal.INSTANCE);
                    }
                    fc.add(NamedLiteral.of(pc.unitName().trim()));
                });
    }

    private static <R> void installSpecialProducer(@Observes final ProcessProducer<?, R> event) {
        final Producer<R> producer = event.getProducer();
        final Collection<?> keys = getKeys(producer);
        if (keys != null && !keys.isEmpty()) {
            event.setProducer(new EntityManagerReferencingProducer<>(producer, keys));
        }
    }

    private static <T> void installSpecialInjectionTarget(@Observes final ProcessInjectionTarget<T> event) {
        final InjectionTarget<T> injectionTarget = event.getInjectionTarget();
        final Collection<?> keys = getKeys(injectionTarget);
        if (keys != null && !keys.isEmpty()) {
            event.setInjectionTarget(new DelegatingInjectionTarget<>(injectionTarget,
                                                                     new EntityManagerReferencingProducer<>(injectionTarget,
                                                                                                            keys)));
        }
    }

    private static Collection<?> getKeys(final Producer<?> producer) {
        final Set<InjectionPoint> injectionPoints = producer.getInjectionPoints();
        final Set<Object> keys = new HashSet<>();
        for (final InjectionPoint injectionPoint : injectionPoints) {
            final Type type = injectionPoint.getType();
            if (type instanceof Class && EntityManager.class.isAssignableFrom((Class<?>) type)) {
                final Annotated annotated = injectionPoint.getAnnotated();
                assert annotated != null;
                // Because of processing the annotated type stuff
                // above, we have converted all PersistenceContext
                // annotations into sequences like:
                //   @Inject
                //   @ContainerManaged
                //   @Named("fred")
                //   @JPATransactionScoped
                //   @Synchronized
                // ...so we look for @Named here.
                final Named named = annotated.getAnnotation(Named.class);
                if (named != null) {
                    keys.add(named.value().trim());
                } else {
                    keys.add("");
                }
            }
        }
        return keys;
    }

    private static void addJPATransactionScopedEntityManager(final BeanConfigurator<EntityManager> beanConfigurator,
                                                             final SynchronizationType synchronizationType,
                                                             final Set<Annotation> qualifiers) {
        Objects.requireNonNull(beanConfigurator);

        beanConfigurator.addType(EntityManager.class)
            .scope(Dependent.class)
            .addQualifiers(qualifiers)
            .produceWith(instance -> {
                    // Build up an array of qualifiers that we'll use
                    // in Instance#select(Class, Annotation...) calls.
                    // Remove @Any from the set if it's present.  We
                    // don't want to select() @Any anything.
                    final int qualifiersSize;
                    final int qualifiersArraySize;
                    final Annotation[] qualifiersArray;
                    final Iterator<? extends Annotation> qualifiersIterator;
                    if (qualifiers == null || qualifiers.isEmpty()) {
                        qualifiersSize = 0;
                        qualifiersArraySize = 0;
                        qualifiersIterator = null;
                        qualifiersArray = new Annotation[0];
                    } else if (qualifiers.contains(Any.Literal.INSTANCE)) {
                        qualifiersSize = qualifiers.size();
                        qualifiersArraySize = qualifiers.size() - 1;
                        assert qualifiersArraySize >= 0;
                        qualifiersIterator = qualifiersArraySize == 0 ? null : qualifiers.iterator();
                        qualifiersArray = new Annotation[qualifiersArraySize];
                    } else {
                        qualifiersSize = qualifiers.size();
                        qualifiersArraySize = qualifiersSize;
                        assert qualifiersArraySize > 0;
                        qualifiersIterator = null;
                        qualifiersArray = qualifiers.toArray(new Annotation[qualifiersArraySize]);
                    }
                    if (qualifiersIterator != null) {
                        for (int i = 0; i < qualifiersArraySize; i++) {
                            assert qualifiersIterator.hasNext();
                            final Annotation qualifier = qualifiersIterator.next();
                            assert qualifier != null;
                            if (!(qualifier instanceof Any)) {
                                qualifiersArray[i] = qualifier;
                            }
                        }
                    }

                    // Get an Instance that can give us a @Default
                    // Transaction.  We deliberately do not use
                    // qualifiers.  It will be in TransactionScoped
                    // scope.
                    final Instance<Transaction> instanceTransaction = instance.select(Transaction.class);

                    // Get an Instance that can give us
                    // a @Default @CDITransactionScoped @Synchronized
                    // (or @Unsynchronized) EntityManager.  It will be
                    // in TransactionScoped scope.
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
                    // a @Default @ContainerManaged
                    // EntityManagerFactory.  It will be in
                    // ApplicationScoped scope.
                    final Annotation[] qualifiersWithContainerManagedArray = new Annotation[qualifiersSize + 1];
                    System.arraycopy(qualifiersArray, 0, qualifiersWithContainerManagedArray, 0, qualifiersArray.length);
                    qualifiersWithContainerManagedArray[qualifiersWithContainerManagedArray.length - 1] =
                        ContainerManaged.Literal.INSTANCE;
                    final Instance<EntityManagerFactory> instanceEmf =
                        instance.select(EntityManagerFactory.class, qualifiersWithContainerManagedArray);

                    // Create the actual EntityManager that will be
                    // produced.  It itself will be in Dependent scope
                    // but its delegate may be something else.
                    final EntityManager returnValue = new DelegatingEntityManager() {
                        @Override
                        protected EntityManager acquireDelegate() {
                            final EntityManager returnValue;
                            boolean inTransaction;
                            try {
                                inTransaction = inTransaction(instanceTransaction);
                            } catch (final SystemException systemException) {
                                throw new CreationException(systemException.getMessage(), systemException);
                            }
                            if (inTransaction) {
                                returnValue = instanceCdiTransactionScopedEntityManager.get();
                            } else {
                                returnValue =
                                    new NonTransactionalTransactionScopedEntityManager(instanceEmf.get()
                                                                                       .createEntityManager(synchronizationType),
                                                                                       this);
                            }
                            return returnValue;
                        }

                        @Override
                        public void close() {
                            if (SynchronizationType.UNSYNCHRONIZED.equals(synchronizationType)) {
                                super.close();
                            } else {
                                throw new IllegalStateException();
                            }
                        }
                    };
                    return returnValue;
                }); // deliberately no disposeWith()

    }

    private static boolean inTransaction(final Instance<? extends Transaction> instanceTransaction) throws SystemException {
        final boolean returnValue;
        if (instanceTransaction == null || instanceTransaction.isUnsatisfied()) {
            returnValue = false;
        } else {
            final Transaction transaction = instanceTransaction.get();
            returnValue = transaction != null && transaction.getStatus() == Status.STATUS_ACTIVE;
        }
        return returnValue;
    }

    private static void addCDITransactionScopedEntityManager(final BeanConfigurator<EntityManager> beanConfigurator,
                                                             final SynchronizationType synchronizationType,
                                                             final Set<Annotation> qualifiers) {
        Objects.requireNonNull(beanConfigurator);

        // Adds an EntityManager that is not wrapped in any way and is in
        // CDI's TransactionScoped scope.  Used as a delegate only.

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
