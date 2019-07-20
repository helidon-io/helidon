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

/**
 * A utility class for creating container-managed {@link
 * EntityManager} instances, honoring the container-related
 * requirements of the JPA specification.
 *
 * @see #createContainerManagedEntityManager(Instance, Set)
 */
final class EntityManagers {


    /*
     * Static fields.
     */


    /**
     * The {@link Logger} for use by all instances of this class.
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final Logger LOGGER = Logger.getLogger(EntityManagers.class.getName(), "messages");


    /*
     * Constructors.
     */


    private EntityManagers() {
        super();
    }


    /*
     * Static methods.
     */


    static EntityManager createContainerManagedEntityManager(final Instance<Object> instance,
                                                             final Set<? extends Annotation> suppliedQualifiers) {
        final String cn = EntityManagers.class.getName();
        final String mn = "createContainerManagedEntityManager";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, suppliedQualifiers});
        }

        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);

        // Acquire the right EntityManagerFactory for this creation
        // request.
        final EntityManagerFactory emf =
            EntityManagerFactories.getContainerManagedEntityManagerFactory(instance, suppliedQualifiers);
        assert emf != null;
        assert emf.isOpen();

        // Trim down the selection qualifiers to use to look for
        // persistence context properties.
        final Set<Annotation> selectionQualifiers;
        if (suppliedQualifiers.isEmpty()) {
            selectionQualifiers = Collections.singleton(ContainerManaged.Literal.INSTANCE);
        } else {
            selectionQualifiers = new HashSet<>(suppliedQualifiers);
            selectionQualifiers.remove(Any.Literal.INSTANCE);
            selectionQualifiers.add(ContainerManaged.Literal.INSTANCE);
        }

        // Go look for persistence context properties.  Many (most?)
        // times there won't be any.
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

        // Work out what SynchronizationType to use based on the
        // qualifiers that were handed to us.
        final SynchronizationType syncType =
            suppliedQualifiers.contains(Unsynchronized.Literal.INSTANCE)
            ? SynchronizationType.UNSYNCHRONIZED
            : SynchronizationType.SYNCHRONIZED;

        // Use the synchronization type we computed and the properties
        // we found to actually create the EntityManager.
        final EntityManager returnValue = emf.createEntityManager(syncType, properties);
        assert returnValue != null;
        assert returnValue.isOpen();

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

}
