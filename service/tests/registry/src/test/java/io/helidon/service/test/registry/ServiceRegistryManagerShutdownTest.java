/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.service.test.registry;

import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ServiceRegistryManagerShutdownTest {

    @Test
    void shutdownDoesNotCreateGlobalRegistry() {
        resetGlobalRegistry();
        var manager = ServiceRegistryManager.create();

        try {
            manager.registry();
            assertThat("Creating an independent registry must not configure the global registry",
                       GlobalServiceRegistry.configured(),
                       is(false));

            manager.shutdown();

            assertThat("Shutting down an independent registry must not configure the global registry",
                       GlobalServiceRegistry.configured(),
                       is(false));
        } finally {
            manager.shutdown();
            resetGlobalRegistry();
        }
    }

    private static void resetGlobalRegistry() {
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        manager.shutdown();
    }
}
