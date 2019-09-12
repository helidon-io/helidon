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
package io.helidon.integrations.cdi.jpa.weld;

import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.InjectionPoint;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.jboss.weld.injection.spi.JpaInjectionServices;
import org.jboss.weld.injection.spi.ResourceReferenceFactory;

/**
 * A {@link JpaInjectionServices} implementation that integrates JPA
 * functionality into Weld-based CDI environments.
 *
 * @see JpaInjectionServices
 *
 * @deprecated This extension is no longer needed and is slated for
 * removal.
 */
@Deprecated
final class WeldJpaInjectionServices implements JpaInjectionServices {


    /*
     * Static fields.
     */


    /**
     * The {@link Logger} for use by all instances of this class.
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final Logger LOGGER = Logger.getLogger(WeldJpaInjectionServices.class.getName(),
                                                          WeldJpaInjectionServices.class.getPackage().getName() + ".Messages");


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link WeldJpaInjectionServices}.
     *
     * <p>Oddly, the fact that this constructor is {@code private}
     * does not prevent Weld from loading it as a service.  This is an
     * unexpected bonus as nothing about this class should be {@code
     * public}.</p>
     */
    WeldJpaInjectionServices() {
        super();
    }

    /**
     * Throws an {@link IllegalArgumentException} when invoked.
     *
     * @exception IllegalArgumentException when invoked
     *
     * @see ResourceReferenceFactory#createResource()
     *
     * @deprecated This class is deprecated, no longer needed and is
     * slated for removal.
     */
    @Deprecated
    @Override
    public ResourceReferenceFactory<EntityManager> registerPersistenceContextInjectionPoint(final InjectionPoint injectionPoint) {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "registerPersistenceContextInjectionPoint";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, injectionPoint);
        }
        final ResourceBundle messages = ResourceBundle.getBundle(this.getClass().getPackage().getName() + ".Messages");
        assert messages != null;
        throw new IllegalArgumentException(messages.getString("deprecated"));
    }

    /**
     * Throws an {@link IllegalArgumentException} when invoked.
     *
     * @exception IllegalArgumentException when invoked
     *
     * @see ResourceReferenceFactory#createResource()
     *
     * @deprecated This class is deprecated, no longer needed and is
     * slated for removal.
     */
    @Deprecated
    @Override
    public ResourceReferenceFactory<EntityManagerFactory> registerPersistenceUnitInjectionPoint(final InjectionPoint ip) {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "registerPersistenceUnitInjectionPoint";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, ip);
        }
        final ResourceBundle messages = ResourceBundle.getBundle(this.getClass().getPackage().getName() + ".Messages");
        assert messages != null;
        throw new IllegalArgumentException(messages.getString("deprecated"));
    }

    /**
     * Invoked by Weld automatically to clean up any resources held by
     * this class.
     */
    @Override
    public void cleanup() {

    }

    /**
     * Throws an {@link IllegalArgumentException} when invoked.
     *
     * @param injectionPoint ignored
     *
     * @return nothing
     *
     * @exception IllegalArgumentException when invoked
     *
     * @see #registerPersistenceContextInjectionPoint(InjectionPoint)}
     *
     * @deprecated This class is deprecated, no longer needed and is
     * slated for removal.
     */
    @Deprecated
    public EntityManager resolvePersistenceContext(final InjectionPoint injectionPoint) {
        return this.registerPersistenceContextInjectionPoint(injectionPoint).createResource().getInstance();
    }

    /**
     * Throws an {@link IllegalArgumentException} when invoked.
     *
     * @param injectionPoint ignored
     *
     * @return nothing
     *
     * @exception IllegalArgumentException when invoked
     *
     * @see #registerPersistenceContextInjectionPoint(InjectionPoint)}
     *
     * @deprecated This class is deprecated, no longer needed and is
     * slated for removal.
     */
    @Deprecated
    public EntityManagerFactory resolvePersistenceUnit(final InjectionPoint injectionPoint) {
        return this.registerPersistenceUnitInjectionPoint(injectionPoint).createResource().getInstance();
    }

}
