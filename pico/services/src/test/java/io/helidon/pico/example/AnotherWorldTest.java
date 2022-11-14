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

package io.helidon.pico.example;

import java.util.List;
import java.util.Optional;

import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.Module;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.spi.impl.DefaultPicoServices;
import io.helidon.pico.spi.impl.DefaultPicoServicesConfig;
import io.helidon.pico.spi.impl.DefaultServices;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * @see ExampleTest for the basics; this test is NOT the basics
 */
public class AnotherWorldTest {
    DefaultPicoServices picoServices;

    @BeforeEach
    public void init() {
        Module anotherWorldModule = new AnotherWorldModule();
        picoServices = new DefaultPicoServices(new FakeConfig());
        picoServices.clear();
        Optional<ServiceBinder> binder = picoServices.createServiceBinder(anotherWorldModule);
        anotherWorldModule.configure(binder.get());
    }

    @Test
    public void activation() {
        DefaultServices services = picoServices.services();
        assertEquals(1, services.getSize());

        List<ServiceProvider<Object>> allProviders = services.lookup(DefaultServiceInfo.builder().build(), false);
        ServiceProvider<Object> anotherWorldProvider = allProviders.get(0);
        assertEquals("AnotherWorldProviderImpl$$picodiActivator:io.helidon.pico.example.WorldImpl:INIT",
                anotherWorldProvider.description());

        World firstWorld = (World)anotherWorldProvider.get();
        assertEquals("world 1", firstWorld.getName());
        World secondWorld = (World)anotherWorldProvider.get();
        assertNotSame(firstWorld, secondWorld);
        assertEquals("world 2", secondWorld.getName());

        assertEquals("AnotherWorldProviderImpl$$picodiActivator:io.helidon.pico.example.WorldImpl:ACTIVE",
                anotherWorldProvider.description());
    }


    static class FakeConfig extends DefaultPicoServicesConfig {
        FakeConfig() {
            setValue(DefaultPicoServicesConfig.KEY_SUPPORTS_DYNAMIC, true);
            setValue(DefaultPicoServicesConfig.KEY_BIND_MODULES, false);
            setValue(DefaultPicoServicesConfig.KEY_BIND_APPLICATION, false);
        }
    }

}
