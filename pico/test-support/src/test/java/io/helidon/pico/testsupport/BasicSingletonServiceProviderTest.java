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

package io.helidon.pico.testsupport;

import java.util.Collections;
import java.util.Set;

import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.PicoServices;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.spi.impl.DefaultPicoServices;
import io.helidon.pico.testsubjects.Hello;
import io.helidon.pico.testsubjects.HelloImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BasicSingletonServiceProvider}.
 */
public class BasicSingletonServiceProviderTest {

    /**
     * Tests initialization.
     */
    @BeforeEach
    public void init() {
        PicoServices picoServices = PicoServices.picoServices().get();
        assertNotNull(picoServices);
        ((DefaultPicoServices) picoServices).reset();
    }

    /**
     * Sanity.
     */
    @Test
    public void sanity() {
        ServiceInfoBasics serviceInfoBasics = new ServiceInfoBasics() {
            @Override
            public double weight() {
                return 1;
            }

            @Override
            public String serviceTypeName() {
                return HelloImpl.class.getName();
            }

            @Override
            public Set<QualifierAndValue> qualifiers() {
                return Collections.singleton(DefaultQualifierAndValue.createNamed("name"));
            }

            @Override
            public Set<String> contractsImplemented() {
                return Collections.singleton(Hello.class.getName());
            }

            @Override
            public Integer runLevel() {
                return 2;
            }
        };

        BasicSingletonServiceProvider<HelloImpl> serviceProvider = new BasicSingletonServiceProvider<>(HelloImpl.class, serviceInfoBasics);
        assertTrue(serviceProvider.isCustom());
        assertFalse(serviceProvider.isProvider());
        assertEquals("BasicSingletonServiceProvider:io.helidon.pico.testsubjects.HelloImpl:INIT",
                     serviceProvider.description());
        assertNotNull(serviceProvider.get());
        assertSame(serviceProvider.get(), serviceProvider.get());
        assertEquals("BasicSingletonServiceProvider:io.helidon.pico.testsubjects.HelloImpl:ACTIVE",
                     serviceProvider.description());
        assertNull(serviceProvider.dependencies());
        assertEquals(serviceProvider, serviceProvider.activator());
        assertEquals(serviceProvider, serviceProvider.deActivator());
        assertNull(serviceProvider.getPostConstructMethod());
        assertNull(serviceProvider.preDestroyMethod());

        assertNull(serviceProvider.serviceInfo().moduleName());
    }

}
