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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Named;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;

/**
 * A utility class for acquiring and creating container-managed {@link
 * EntityManagerFactory} instances.
 *
 * @see #getContainerManagedEntityManagerFactory(Instance, Set)
 *
 * @see EntityManagerFactory
 *
 * @see PersistenceProvider#createContainerEntityManagerFactory(PersistenceUnitInfo,
 * Map)
 */
final class EntityManagerFactories {


    /*
     * Static fields.
     */


    /**
     * The {@link Logger} for use by all instances of this class.
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final Logger LOGGER = Logger.getLogger(EntityManagerFactories.class.getName(),
                                                          EntityManagerFactories.class.getPackage().getName() + ".Messages");


    /*
     * Constructors.
     */


    private EntityManagerFactories() {
        super();
    }


    /*
     * Static methods.
     */


    static EntityManagerFactory getContainerManagedEntityManagerFactory(final Instance<Object> instance,
                                                                        final Set<? extends Annotation> suppliedQualifiers) {
        final String cn = EntityManagerFactories.class.getName();
        final String mn = "getContainerManagedEntityManagerFactory";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, suppliedQualifiers});
        }

        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);

        final Set<Annotation> selectionQualifiers;
        if (suppliedQualifiers.isEmpty()) {
            selectionQualifiers = Collections.singleton(ContainerManaged.Literal.INSTANCE);
        } else {
            selectionQualifiers = new HashSet<>(suppliedQualifiers);
            selectionQualifiers.remove(Any.Literal.INSTANCE);
            selectionQualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
            selectionQualifiers.add(ContainerManaged.Literal.INSTANCE);
        }
        final EntityManagerFactory returnValue =
            instance.select(EntityManagerFactory.class,
                            selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()])).get();

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    /**
     * {@linkplain
     * PersistenceProvider#createContainerEntityManagerFactory(PersistenceUnitInfo,
     * Map) Creates and returns a new
     * <code>EntityManagerFactory</code>} when invoked, honoring
     * sections 3.6.2, 9.1, 9.5 and 9.5.1 of the JPA 2.2
     * specification.
     *
     * @param instance an {@link Instance} used to acquire contextual
     * references; must not be {@code null}
     *
     * @param suppliedQualifiers a {@link Set} of qualifier
     * annotations with which the CDI bean whose producer method this
     * method effectively is will be associated; must not be {@code
     * null}
     *
     * @param beanManager a {@link BeanManager}; must not be {@code null}
     *
     * @exception NullPointerException if any parameter value is
     * {@code null}
     *
     * @exception PersistenceException if a container-managed {@link
     * EntityManagerFactory} could not be created
     *
     * @see
     * PersistenceProvider#createContainerEntityManagerFactory(PersistenceUnitInfo,
     * Map)
     */
    static EntityManagerFactory createContainerManagedEntityManagerFactory(final Instance<Object> instance,
                                                                           final Set<? extends Annotation> suppliedQualifiers,
                                                                           final BeanManager beanManager) {
        final String cn = EntityManagerFactories.class.getName();
        final String mn = "createContainerManagedEntityManagerFactory";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, suppliedQualifiers});
        }

        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);
        Objects.requireNonNull(beanManager);

        final PersistenceUnitInfo pu = getPersistenceUnitInfo(instance, suppliedQualifiers);
        if (PersistenceUnitTransactionType.RESOURCE_LOCAL.equals(pu.getTransactionType())) {
            throw new PersistenceException(Messages.format("resourceLocalPersistenceUnitDisallowed", pu));
        }

        PersistenceProvider persistenceProvider = null;
        try {
            persistenceProvider = getPersistenceProvider(instance, suppliedQualifiers, pu);
        } catch (final ReflectiveOperationException reflectiveOperationException) {
            throw new PersistenceException(reflectiveOperationException.getMessage(),
                                           reflectiveOperationException);
        }

        // Revisit: there should be a way to acquire these, or maybe
        // not, given that we can source PersistenceUnitInfos as beans
        // anyway.
        final Map<String, Object> properties = new HashMap<>();

        properties.put("javax.persistence.bean.manager", beanManager);

        Class<?> validatorFactoryClass = null;
        try {
            validatorFactoryClass = Class.forName("javax.validation.ValidatorFactory");
        } catch (final ClassNotFoundException classNotFoundException) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.logp(Level.INFO, cn, mn, "noValidatorFactoryClass", classNotFoundException);
            }
        }
        if (validatorFactoryClass != null) {
            final Instance<?> validatorFactoryInstance = instance.select(validatorFactoryClass);
            if (!validatorFactoryInstance.isUnsatisfied()) {
                properties.put("javax.persistence.validation.factory", validatorFactoryInstance.get());
            }
        }

        final EntityManagerFactory returnValue = persistenceProvider.createContainerEntityManagerFactory(pu, properties);
        assert returnValue != null;
        assert returnValue.isOpen();

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    private static PersistenceProvider getPersistenceProvider(final Instance<Object> instance,
                                                              final Set<? extends Annotation> suppliedQualifiers,
                                                              final PersistenceUnitInfo persistenceUnitInfo)
        throws ReflectiveOperationException {
        final String cn = EntityManagerFactories.class.getName();
        final String mn = "getPersistenceProvider";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, suppliedQualifiers, persistenceUnitInfo});
        }

        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);
        Objects.requireNonNull(persistenceUnitInfo);

        final Set<Annotation> selectionQualifiers = new HashSet<>(suppliedQualifiers);
        selectionQualifiers.remove(Any.Literal.INSTANCE);
        selectionQualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
        selectionQualifiers.removeIf(q -> q instanceof Named);
        final Annotation[] selectionQualifiersArray =
            selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]);
        final PersistenceProvider returnValue;
        final String providerClassName = persistenceUnitInfo.getPersistenceProviderClassName();
        if (providerClassName == null) {
            returnValue = instance.select(PersistenceProvider.class, selectionQualifiersArray).get();
        } else {
            returnValue =
                (PersistenceProvider) instance.select(Class.forName(providerClassName,
                                                                    true,
                                                                    Thread.currentThread().getContextClassLoader()),
                                                      selectionQualifiersArray).get();
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    private static PersistenceUnitInfo getPersistenceUnitInfo(final Instance<Object> instance,
                                                              final Set<? extends Annotation> suppliedQualifiers) {
        final String cn = EntityManagerFactories.class.getName();
        final String mn = "getPersistenceUnitInfo";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, suppliedQualifiers});
        }

        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);

        final Set<Annotation> selectionQualifiers = new HashSet<>(suppliedQualifiers);
        selectionQualifiers.remove(Any.Literal.INSTANCE);
        selectionQualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);

        Instance<PersistenceUnitInfo> puInstance;
        if (selectionQualifiers.isEmpty()) {
            puInstance = instance.select(PersistenceUnitInfo.class);
        } else {
            puInstance = instance.select(PersistenceUnitInfo.class,
                                         selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
            if (puInstance.isUnsatisfied()) {
                // We looked for @Qualifier @Named("x"); now look for
                // just @Qualifier...
                selectionQualifiers.removeIf(q -> q instanceof Named);
                puInstance = instance.select(PersistenceUnitInfo.class,
                                             selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
                if (puInstance.isUnsatisfied() && !selectionQualifiers.equals(Collections.singleton(Default.Literal.INSTANCE))) {
                    // ...now just @Default...
                    puInstance = instance.select(PersistenceUnitInfo.class);
                    if (puInstance.isUnsatisfied()) {
                        // ...now any at all.  Obviously this case
                        // will resolve only if there is exactly one
                        // PersistenceUnitInfo bean, and somewhat
                        // bizarrely it won't have the @Default
                        // qualifier.
                        puInstance = instance.select(PersistenceUnitInfo.class, Any.Literal.INSTANCE);
                    }
                }
            }
        }

        // This may very well throw a resolution exception; that is
        // anticipated.
        final PersistenceUnitInfo returnValue = puInstance.get();

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

}
