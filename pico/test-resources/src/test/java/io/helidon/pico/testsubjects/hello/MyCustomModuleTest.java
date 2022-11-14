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

package io.helidon.pico.testsubjects.hello;

import java.util.List;

import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link io.helidon.pico.testsubjects.hello.MyCustomModule}.
 */
public class MyCustomModuleTest {

    /**
     * Sanity.
     */
    @Test
    public void sanity() {
        PicoServices picoServices = PicoServices.picoServices().orElseThrow();
        Services services = picoServices.services();
        List<ServiceProvider<Object>> allServices = services.lookup(DefaultServiceInfo.builder().build());
        assertEquals(
                "[BasicModule, BasicSingletonServiceProvider]",
                String.valueOf(ServiceProvider.toIdentities(allServices)));
        World world = services.lookupFirst(World.class).get();
        assertNotNull(world);
        assertSame(world, services.lookupFirst(World.class).get());
    }

}
