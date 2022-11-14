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

package io.helidon.pico.spi.impl;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;

import io.helidon.pico.example.Hello;
import io.helidon.pico.example.HelloImpl$$picodiActivator;
import io.helidon.pico.example.WorldImpl$$picodiActivator;
import io.helidon.pico.ActivationLog;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.Injector;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link io.helidon.pico.spi.impl.DefaultPicoServices}.
 */
public class DefaultPicoServicesTest {
    PicoServices picoServices;

    /**
     * Tests initialization.
     */
    @BeforeEach
    public void init() {
        picoServices = PicoServices.picoServices().get();
        assertNotNull(picoServices);
        ((DefaultPicoServices) picoServices).reset();
    }

    @Test
    public void testGetPicoServices() {
        assertEquals(DefaultPicoServices.class, picoServices.getClass());
        assertSame(picoServices, PicoServices.picoServices().get());
        assertTrue(((DefaultPicoServices)picoServices).isGlobal());

        Services services = picoServices.services();
        assertNotNull(services);
        assertEquals(DefaultServices.class, services.getClass());
        assertSame(services, picoServices.services());

        Optional<? extends Injector> injector = picoServices.injector();
        assertNotNull(injector);
        assertTrue(injector.isPresent());
        assertSame(injector.get(), picoServices.injector().get());

        Optional<? extends PicoServicesConfig> config = picoServices.config();
        assertNotNull(config);
        assertTrue(config.isPresent());
        assertEquals(DefaultPicoServicesConfig.class, config.get().getClass());
        assertSame(config.get(), picoServices.config().get());

        Optional<ActivationLog> activationLog = picoServices.activationLog();
        assertNotNull(activationLog);
        assertFalse(activationLog.isPresent());
        if (activationLog.isPresent()) {
            assertEquals(0, activationLog.get().toQuery().get().fullActivationLog().size());
        }

        // now reset ...
        ((DefaultPicoServices)picoServices).reset();
        Services services2 = picoServices.services();
        assertNotNull(services2);
        assertEquals(DefaultServices.class, services2.getClass());
        assertNotSame(services, services2);

        Optional<ActivationLog> activationLog2 = picoServices.activationLog();
        assertNotNull(activationLog2);
        assertFalse(activationLog2.isPresent());
        if (activationLog2.isPresent()) {
            assertNotSame(activationLog, activationLog2);
        }
    }

    @Test
    public void testHelloExample() {
        assertEquals(DefaultPicoServices.class, picoServices.getClass());
        assertSame(picoServices, PicoServices.picoServices().get());

        Services services = picoServices.services();
        assertNotNull(services);
    }

    @Test
    public void runLevel0() {
        HelloImpl$$picodiActivator.INSTANCE.reset();
        WorldImpl$$picodiActivator.INSTANCE.reset();

        Services services = picoServices.services();
        // This assumes a set of applications to start during main() - you are expected to change this to match your specifications
        List<ServiceProvider<Object>> lazilyActivatedStartupServices =
                     services.lookup(DefaultServiceInfo.builder().runLevel(0).build(), true);
        assertEquals("[HelloImpl$$picodiActivator:io.helidon.pico.example.HelloImpl:INIT]",
                     String.valueOf(ServiceProvider.toDescriptions(lazilyActivatedStartupServices)));
        lazilyActivatedStartupServices.forEach(ServiceProvider::get);
        assertEquals("[HelloImpl$$picodiActivator:io.helidon.pico.example.HelloImpl:ACTIVE]",
                     String.valueOf(ServiceProvider.toDescriptions(lazilyActivatedStartupServices)));
    }

    /**
     * We should only return services of weight less than the weight provided in the query.
     */
    @Test
    public void weight() {
        TestableServices services = new TestableServices();

        TestableService<Hello> sp1 = new TestableService<>("sp1",
                                                           DefaultServiceInfo.builder()
                                                                   .weight(11.10)
                                                                   .serviceTypeName("fake service provider 1")
                                                                   .contractImplemented(Hello.class.getName())
                                                                   .contractImplemented(Closeable.class.getName())
                                                                   .build());
        services.bind(sp1);
        TestableService<Hello> sp2 = new TestableService<>("sp2",
                                                           DefaultServiceInfo.builder()
                                                                   .weight(10.09)
                                                                   .serviceTypeName("fake service provider 2")
                                                                   .contractImplemented(Hello.class.getName())
                                                                   .contractImplemented(Closeable.class.getName())
                                                                   .build());
        services.bind(sp2);
        TestableService<Hello> sp3 = new TestableService<>("sp3",
                                                           DefaultServiceInfo.builder()
                                                                   .weight(11.19)
                                                                   .serviceTypeName("fake service provider 3")
                                                                   .contractImplemented(Hello.class.getName())
                                                                   .contractImplemented(Closeable.class.getName())
                                                                   .build());
        services.bind(sp3);
        TestableService<Hello> sp4 = new TestableService<>("sp4",
                                                           DefaultServiceInfo.builder()
                                                                   .weight(11.10)   // same weight as #1, secondary sort on name
                                                                   .serviceTypeName("fake service provider 4")
                                                                   .contractImplemented(Hello.class.getName())
                                                                   .contractImplemented(Closeable.class.getName())
                                                                   .build());
        services.bind(sp4);

        assertEquals("[sp3, sp1, sp4, sp2]", services.lookup(Hello.class).toString());

        assertEquals("[sp3, sp1, sp4, sp2]", services.lookup(DefaultServiceInfo.builder()
                                                           .contractImplemented(Closeable.class.getName())
                                                           .weight(12.0)
                                                           .build(), false).toString());
        assertEquals("[sp1, sp4, sp2]", services.lookup(DefaultServiceInfo.builder()
                                                                .contractImplemented(Closeable.class.getName())
                                                                .weight(11.19)
                                                                .build(), false).toString());
        assertEquals("[sp2]", services.lookup(DefaultServiceInfo.builder()
                                                           .contractImplemented(Closeable.class.getName())
                                                           .weight(11.10)
                                                           .build(), false).toString());
        assertEquals("[sp2]", services.lookup(DefaultServiceInfo.builder()
                                                      .contractImplemented(Closeable.class.getName())
                                                      .weight(10.10)
                                                      .build(), false).toString());
        assertEquals("[]", services.lookup(DefaultServiceInfo.builder()
                                                      .contractImplemented(Closeable.class.getName())
                                                      .weight(10.09)
                                                      .build(), false).toString());
        assertEquals("[]", services.lookup(DefaultServiceInfo.builder()
                                                   .contractImplemented(Closeable.class.getName())
                                                   .weight(1D)
                                                   .build(), false).toString());
    }

}
