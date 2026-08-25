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

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.ScopeNotActiveException;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ScopedRegistryShutdownTest {
    @Test
    void lookupDuringPreDestroyDoesNotDeadlockShutdown() throws Exception {
        ShutdownLookupService.reset();
        var config = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(ShutdownLookupService__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownLookupTarget__ServiceDescriptor.INSTANCE)
                .build();
        var manager = ServiceRegistryManager.create(config);
        var registry = manager.registry();
        registry.get(ShutdownLookupService.class);

        var threadFactory = Thread.ofPlatform().daemon().factory();
        try (var executor = Executors.newFixedThreadPool(2, threadFactory)) {
            var lookup = executor.submit(() -> {
                ShutdownLookupService.awaitDestroyStarted();
                try {
                    return assertThrows(ScopeNotActiveException.class,
                                        () -> registry.get(TypeName.create(ShutdownLookupTarget.class)));
                } finally {
                    ShutdownLookupService.lookupCompleted();
                }
            });
            var shutdown = executor.submit(manager::shutdown);

            lookup.get(10, TimeUnit.SECONDS);
            shutdown.get(10, TimeUnit.SECONDS);
        }
    }
}
