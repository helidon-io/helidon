/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.jpa.simple;

import java.util.function.Consumer;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Persistence unit context.
 */
@ApplicationScoped
public class PU {
    
    private static final SeContainer CONTAINER = initContainer();

    private static SeContainer initContainer() {
        final SeContainerInitializer initializer = SeContainerInitializer
                .newInstance()
                .addBeanClasses(PU.class);
        assertNotNull(initializer);
        return initializer.initialize();
    }

    /**
     * Provides an instance of persistence unit context.
     *
     * @return an instance of persistence unit context
     */
    public static PU getInstance() {
        return CONTAINER.select(PU.class).get();
    }
    
    @PersistenceContext(unitName = "test")
    private EntityManager em;

    /**
     * Get EntityManager instance.
     *
     * @return EntityManager instance
     */
    public EntityManager getEm() {
        return em;
    }

    /**
     * Get EntityManager instance with all caches evicted.
     *
     * @return EntityManager instance with all caches evicted
     */
    public EntityManager getCleanEm() {
        em.flush();
        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();
        return em;
    }

    /***
     * Run provided function in transaction.
     *
     * @param tx function to be run in transaction
     */
    @Transactional
    public void tx(Consumer<PU> tx) {
        tx.accept(this);
    }

}
