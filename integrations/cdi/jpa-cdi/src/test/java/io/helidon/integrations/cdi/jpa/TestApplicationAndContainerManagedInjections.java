/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.Objects;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import io.helidon.microprofile.config.ConfigCdiExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static jakarta.persistence.Persistence.createEntityManagerFactory;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TestApplicationAndContainerManagedInjections {

    private SeContainer sec;

    private TestApplicationAndContainerManagedInjections() {
        super();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    final void initializeCdiContainer() {
        System.setProperty(JpaExtension.class.getName() + ".enabled", "false");
        System.setProperty(PersistenceExtension.class.getName() + ".enabled", "true");
        Class<?> cdiSeJtaPlatformClass;
        try {
            // Load it dynamically because Hibernate won't be on the classpath when we're testing with Eclipselink
            cdiSeJtaPlatformClass =
                Class.forName("io.helidon.integrations.cdi.hibernate.CDISEJtaPlatform",
                              false,
                              Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            cdiSeJtaPlatformClass = null;
        }
        SeContainerInitializer i = SeContainerInitializer.newInstance()
            .disableDiscovery()
            .addExtensions(ConfigCdiExtension.class,
                           PersistenceExtension.class,
                           com.arjuna.ats.jta.cdi.TransactionExtension.class,
                           io.helidon.integrations.datasource.hikaricp.cdi.HikariCPBackedDataSourceExtension.class)
            .addBeanClasses(Frobnicator.class);
        if (cdiSeJtaPlatformClass != null) {
            i = i.addBeanClasses(cdiSeJtaPlatformClass);
        }
        this.sec = i.initialize();
    }

    @AfterEach
    final void closeCdiContainer() {
        if (this.sec != null) {
            this.sec.close();
        }
        System.setProperty(PersistenceExtension.class.getName() + ".enabled", "false");
        System.setProperty(JpaExtension.class.getName() + ".enabled", "true");
    }

    @Test
    final void testApplicationAndContainerManagedInjections() {
        Instance<Frobnicator> fi = sec.select(Frobnicator.class);
        Frobnicator f = fi.get();
        assertThat(f.containerManagedEm.isOpen(), is(true));
        assertThat(f.containerManagedEm, instanceOf(JtaEntityManager.class));
        assertThat(f.applicationManagedEm.isOpen(), is(true));
        fi.destroy(f);
    }

    @DataSourceDefinition(
        name = "test",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:TestApplicationAndContainerManagedInjections",
        serverName = "",
        properties = {
            "user=sa"
        }
    )
    @Dependent
    private static class Frobnicator {

        @PersistenceContext(unitName = "test")
        private EntityManager containerManagedEm;

        private final EntityManager applicationManagedEm;

        @Inject
        Frobnicator(EntityManager applicationManagedEm) {
            super();
            this.applicationManagedEm = Objects.requireNonNull(applicationManagedEm);
        }

        @Produces
        @Singleton
        static EntityManagerFactory produceApplicationManagedEntityManagerFactory() {
            return createEntityManagerFactory("test-resource-local");
        }

        static void disposeApplicationManagedEntityManagerFactory(@Disposes EntityManagerFactory applicationManagedEmf) {
            applicationManagedEmf.close();
        }

        @Produces
        @Dependent
        static EntityManager produceApplicationManagedEntityManager(EntityManagerFactory applicationManagedEmf) {
            return applicationManagedEmf.createEntityManager();
        }

        static void disposeApplicationManagedEntityManager(@Disposes EntityManager applicationManagedEm) {
            applicationManagedEm.close();
        }

    }

}
