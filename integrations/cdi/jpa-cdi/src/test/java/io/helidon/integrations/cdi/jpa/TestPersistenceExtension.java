/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.Map;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;

import io.helidon.microprofile.config.ConfigCdiExtension;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class TestPersistenceExtension {

    private SeContainer sec;

    private boolean hibernate;

    private TestPersistenceExtension() {
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
        this.hibernate = cdiSeJtaPlatformClass != null;
        SeContainerInitializer i = SeContainerInitializer.newInstance()
            .disableDiscovery()
            .addExtensions(PersistenceExtension.class,
                           ConfigCdiExtension.class,
                           com.arjuna.ats.jta.cdi.TransactionExtension.class,
                           io.helidon.integrations.datasource.hikaricp.cdi.HikariCPBackedDataSourceExtension.class)
            .addBeanClasses(Caturgiator.class,
                            Frobnicator.class);
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
    final void testSpike() {
        Instance<Frobnicator> fi = sec.select(Frobnicator.class);
        Frobnicator f = fi.get();

        assertThat(f.em.isOpen(), is(true));
        assertThat(f.em, instanceOf(JtaEntityManager.class));
        Map<?, ?> properties = f.em.getProperties();
        assertThat(properties.containsKey("java.vendor.url"), is(true)); // proves MicroProfile Config integration works
        // This is kind of odd. Eclipselink implements EntityManager#getProperties() such that the returned map has
        // all discoverable properties: those from the persistence unit, and of course any explicitly specified by
        // the user. Hibernate does not: properties starting with "eclipselink.", as an arbitrary example, are not
        // present.
        assertThat(properties.get("eclipselink.jdbc.native-sql"), this.hibernate ? nullValue() : is("true"));

        assertThat(f.emf.isOpen(), is(true));
        properties = f.emf.getProperties();
        assertThat(properties.containsKey("java.vendor.url"), is(true)); // (proves that MicroProfile Config works)
        // Note that this assertion also means that Hibernate's strange properties behavior above does not apply to the
        // equivalent scenario involving EntityManagerFactory instances (for no particular reason?).
        assertThat(properties.get("eclipselink.jdbc.native-sql"), is("true"));

        // (Setting system properties to a different value does not change anything, because the persistence unit
        // properties in META-INF/persistence.xml overrule any other sources.)
        String old = System.setProperty("eclipselink.jdbc.native-sql", "false");
        try {
            assertThat(properties.get("eclipselink.jdbc.native-sql"), is("true"));
            // (Note that Helidon's MicroProfile Config implementation reflects the change.)
            assertThat(sec.select(Config.class).get()
                       .getOptionalValue("eclipselink.jdbc.native-sql", String.class).orElse(null),
                       is("false"));
        } finally {
            if (old == null) {
                System.clearProperty("eclipselink.jdbc.native-sql");
            } else {
                System.setProperty("eclipselink.jdbc.native-sql", old);
            }
        }

        Instance<Caturgiator> ci = sec.select(Caturgiator.class);
        Caturgiator c = ci.get();
        assertThat(c.em, is(f.em));

        fi.destroy(f);
        assertThat(c.em.isOpen(), is(true));
        ci.destroy(c);
    }

    @DataSourceDefinition(
        name = "test",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:TestPersistenceExtension",
        serverName = "",
        properties = {
            "user=sa"
        }
    )
    @Dependent
    private static class Frobnicator {

        @PersistenceUnit(unitName = "test")
        private EntityManagerFactory emf;

        @PersistenceContext(unitName = "test")
        private EntityManager em;

        @Inject
        Frobnicator() {
            super();
        }

    }

    @Dependent
    private static class Caturgiator {

        @PersistenceContext(unitName = "test")
        private EntityManager em;

        @Inject
        Caturgiator() {
            super();
        }

    }

}
