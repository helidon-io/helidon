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
import java.util.HashSet;
import java.util.Set;

import javax.annotation.sql.DataSourceDefinition;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TransactionRequiredException;
import javax.transaction.Transactional;

import io.helidon.integrations.datasource.hikaricp.cdi.HikariCPBackedDataSourceExtension;
import io.helidon.integrations.jta.cdi.NarayanaExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Dependent
@DataSourceDefinition(
    name = "test",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestAnnotationRewriting {

    @PersistenceContext(unitName = "fred")
    private EntityManager em;
    
    private SeContainer cdiContainer;

    TestAnnotationRewriting() {
        super();
    }
    
    @BeforeEach
    void startCdiContainer() {
        System.setProperty("jpaAnnotationRewritingEnabled", "true");
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance()
            .addBeanClasses(this.getClass());
        assertNotNull(initializer);
        this.cdiContainer = initializer.initialize();
    }
  
    @AfterEach
    void shutDownCdiContainer() {
        if (this.cdiContainer != null) {
            this.cdiContainer.close();
        }
    }

    @PersistenceContext(unitName = "fred")
    private void testAnnotationRewriting(@Observes @Initialized(ApplicationScoped.class) final Object event,
                                         final EntityManager fred) {
        assertNotNull(event);
        assertNotNull(fred);
        assertNotNull(this.em);
        assertTrue(this.em.isOpen());
        assertFalse(this.em.isJoinedToTransaction());
    }

    @Test
    void testAnnotationRewriting() {
        
    }

    @Test
    void testNonTransactionalEntityManager() {
        final Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(JPATransactionScoped.Literal.INSTANCE);
        final EntityManager entityManager = this.cdiContainer.select(EntityManager.class, qualifiers.toArray(new Annotation[qualifiers.size()])).get();
        assertTrue(entityManager instanceof DelegatingEntityManager);
        assertTrue(entityManager.isOpen());
        assertFalse(entityManager.isJoinedToTransaction());
        try {
            entityManager.persist(new Object());
            fail("A TransactionRequiredException should have been thrown");
        } catch (final TransactionRequiredException expected) {

        }
        try {
            entityManager.close();
            fail("Closed EntityManager; should not have been able to");
        } catch (final IllegalStateException expected) {

        }
    }

    @Test
    void testTransactionalEntityManager() {
        final TestAnnotationRewriting testInstance = this.cdiContainer.select(TestAnnotationRewriting.class).get();
        assertNotNull(testInstance);
        testInstance.testEntityManagerIsJoinedToTransactionInTransactionalAnnotatedMethod();
    }
    
    @Transactional
    void testEntityManagerIsJoinedToTransactionInTransactionalAnnotatedMethod() {
        assertNotNull(this.em);
        assertTrue(this.em.isJoinedToTransaction());
        try {
            this.em.close();
            fail("Closed EntityManager; should not have been able to");
        } catch (final IllegalStateException expected) {

        }
    }
    
}
