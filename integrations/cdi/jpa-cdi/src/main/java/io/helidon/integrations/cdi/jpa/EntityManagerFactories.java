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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.TypeLiteral;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.SynchronizationType;

final class EntityManagerFactories {

    /**
     * The {@link Logger} for use by all instances of this class.
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final Logger LOGGER = Logger.getLogger(EntityManagerFactories.class.getName(), "messages");

    private EntityManagerFactories() {
        super();
    }

    static EntityManager createContainerManagedEntityManager(final Instance<Object> instance,
                                                             final Set<? extends Annotation> suppliedQualifiers) {
        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);
        final EntityManagerFactory emf = getContainerManagedEntityManagerFactory(instance, suppliedQualifiers);
        assert emf != null;
        assert emf.isOpen();
        final SynchronizationType syncType =
            suppliedQualifiers.contains(Unsynchronized.Literal.INSTANCE) ? SynchronizationType.UNSYNCHRONIZED : null;
        final Set<Annotation> selectionQualifiers;
        if (suppliedQualifiers.isEmpty()) {
            selectionQualifiers = Collections.singleton(ContainerManaged.Literal.INSTANCE);
        } else {
            selectionQualifiers = new HashSet<>(suppliedQualifiers);
            selectionQualifiers.remove(Any.Literal.INSTANCE);
            selectionQualifiers.add(ContainerManaged.Literal.INSTANCE);
        }
        final TypeLiteral<Map<?, ?>> typeLiteral = new TypeLiteral<Map<?, ?>>() {
                private static final long serialVersionUID = 1L;
            };
        final Map<?, ?> properties;
        Instance<Map<?, ?>> propertiesInstance =
            instance.select(typeLiteral, selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
        if (propertiesInstance != null && !propertiesInstance.isUnsatisfied()) {
            selectionQualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
            selectionQualifiers.add(ContainerManaged.Literal.INSTANCE);
            propertiesInstance =
                instance.select(typeLiteral, selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
            if (propertiesInstance != null && !propertiesInstance.isUnsatisfied()) {
                properties = propertiesInstance.get();
            } else {
                properties = null;
            }
        } else {
            properties = null;
        }
        final EntityManager returnValue = emf.createEntityManager(syncType, properties);
        assert returnValue != null;
        assert returnValue.isOpen();
        return returnValue;
    }

    static Instance<EntityManagerFactory>
        getContainerManagedEntityManagerFactoryInstance(final Instance<Object> instance,
                                                        final Set<? extends Annotation> suppliedQualifiers) {
        final String cn = JpaExtension.class.getName();
        final String mn = "getContainerManagedEntityManagerFactoryInstance";
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
            // Get an Instance that can give us an
            // EntityManagerFactory.  We want to preserve
            // user-supplied qualifiers, but we don't need any of
            // *our* qualifiers other than @ContainerManaged
            // (e.g. @Synchronized, @Unsynchronized, @CDITransactionScoped,
            // etc. etc.).  The EntityManagerFactory it vends will be
            // in @ApplicationScoped scope, or it better be, anyway.
            selectionQualifiers.remove(Any.Literal.INSTANCE);
            selectionQualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
            selectionQualifiers.add(ContainerManaged.Literal.INSTANCE);
        }
        final Instance<EntityManagerFactory> returnValue =
            instance.select(EntityManagerFactory.class, selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    static EntityManagerFactory getContainerManagedEntityManagerFactory(final Instance<Object> instance,
                                                                        final Set<? extends Annotation> suppliedQualifiers) {
        final String cn = JpaExtension.class.getName();
        final String mn = "getContainerManagedEntityManagerFactory";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, suppliedQualifiers});
        }
        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);
        final EntityManagerFactory returnValue =
            getContainerManagedEntityManagerFactoryInstance(instance, suppliedQualifiers).get();
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }


}
