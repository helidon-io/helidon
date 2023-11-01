/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.hibernate;

import java.net.URL;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceProviderResolverHolder;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;

import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;

import static jakarta.persistence.spi.PersistenceProviderResolverHolder.getPersistenceProviderResolver;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

final class TestDialectDetection {

    TestDialectDetection() {
        super();
    }

    @Test
    void testDialectDetection() {
        PersistenceProvider pp = getPersistenceProviderResolver().getPersistenceProviders().get(0);
        try (EntityManagerFactory emf = pp.createContainerEntityManagerFactory(new PUI(), null)) {
            SessionFactoryImplementor sfi = emf.unwrap(SessionFactoryImplementor.class);
            assertThat(sfi.getServiceRegistry().getService(DialectFactory.class), instanceOf(DataSourceBackedDialectFactory.class));
            assertThat(sfi.getJdbcServices().getDialect(), instanceOf(H2Dialect.class));
        }
    }

    private static class PUI implements PersistenceUnitInfo {

        private final Properties properties;

        private final JdbcDataSource dataSource;

        private PUI() {
            super();
            this.properties = new Properties();
            this.dataSource = new JdbcDataSource();
            this.dataSource.setURL("jdbc:h2:mem:" + this.getClass().getName());
            this.dataSource.setUser("sa");
            this.dataSource.setPassword("sa");
        }

        @Override
        public void addTransformer(ClassTransformer ignored) {

        }

        @Override
        public boolean excludeUnlistedClasses() {
            return true;
        }

        @Override
        public ClassLoader getClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }

        @Override
        public List<URL> getJarFileUrls() {
            return List.of();
        }

        @Override
        public DataSource getJtaDataSource() {
            return this.dataSource;
        }

        @Override
        public List<String> getManagedClassNames() {
            return List.of();
        }

        @Override
        public List<String> getMappingFileNames() {
            return List.of();
        }

        @Override
        public ClassLoader getNewTempClassLoader() {
            return this.getClassLoader();
        }

        @Override
        public DataSource getNonJtaDataSource() {
            return this.dataSource;
        }

        @Override
        public String getPersistenceProviderClassName() {
            return null;
        }

        @Override
        public String getPersistenceUnitName() {
            return "TestDialectDetection";
        }

        @Override
        public URL getPersistenceUnitRootUrl() {
            return null;
        }

        @Override
        public String getPersistenceXMLSchemaVersion() {
            return "3.1";
        }

        @Override
        public Properties getProperties() {
            return this.properties;
        }

        @Override
        public SharedCacheMode getSharedCacheMode() {
            return SharedCacheMode.UNSPECIFIED;
        }

        @Override
        public PersistenceUnitTransactionType getTransactionType() {
            return PersistenceUnitTransactionType.RESOURCE_LOCAL;
        }

        @Override
        public ValidationMode getValidationMode() {
            return ValidationMode.NONE;
        }

    }

}
