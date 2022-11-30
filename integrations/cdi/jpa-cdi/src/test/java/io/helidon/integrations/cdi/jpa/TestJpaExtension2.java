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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

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
        this.c = SeContainerInitializer.newInstance()
            .disableDiscovery()
            .addExtensions(JpaExtension2.class)
            .addBeanClasses(Frobnicator.class)
            .initialize();
    }

    @AfterEach
    final void closeCdiContainer() {
        this.c.close();
    }

    @Test
    final void testSpike() {
        Frobnicator f = c.select(Frobnicator.class).get();
        f.toString();
    }

    @ApplicationScoped
    private static class Frobnicator {

        @Inject
        Frobnicator() {
            super();
        }

        @Produces
        @Dependent
        private String string() {
            return "Hello";
        }
        
    }
    
}
