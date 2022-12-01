/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class TestJpaExtension2 {

    private SeContainer c;

    private TestJpaExtension2() {
        super();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    final void initializeCdiContainer() {
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
            .addExtensions(JpaExtension2.class,
                           com.arjuna.ats.jta.cdi.TransactionExtension.class,
                           io.helidon.integrations.datasource.hikaricp.cdi.HikariCPBackedDataSourceExtension.class)
            .addBeanClasses(Frobnicator.class,
                            JtaAdaptingDataSourceProvider.class);
        if (cdiSeJtaPlatformClass != null) {
            i = i.addBeanClasses(cdiSeJtaPlatformClass);
        }
        this.c = i.initialize();
    }

    @AfterEach
    final void closeCdiContainer() {
        if (this.c != null) {
            this.c.close();
        }
    }

    @Test
    final void testSpike() {
        Frobnicator f = c.select(Frobnicator.class).get();
        assertThat(f.em.isOpen(), is(true));
    }

    @DataSourceDefinition(
        name = "test",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:TestJpaExtension2",
        serverName = "",
        properties = {
            "user=sa"
        }
    )
    @Dependent
    private static class Frobnicator {

        @PersistenceContext(unitName = "test")
        private EntityManager em;

        @Inject
        Frobnicator() {
            super();
        }

    }

}
