/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.health.checks;

import java.lang.management.ManagementFactory;
import java.util.List;

import io.helidon.common.NativeImageHelper;
import io.helidon.common.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.spi.HealthCheckProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for {@link io.helidon.health.spi.HealthCheckProvider}.
 */
public class BuiltInHealthCheckProvider implements HealthCheckProvider {
    /**
     * Default constructor is required by {@link java.util.ServiceLoader}.
     */
    public BuiltInHealthCheckProvider() {
    }

    @Override
    public List<HealthCheck> healthChecks(Config config) {
        if (NativeImageHelper.isNativeImage()) {
            return List.of(diskSpace(config), heapMemory(config));
        } else {
            return List.of(diskSpace(config), heapMemory(config), deadlock());
        }
    }

    private DeadlockHealthCheck deadlock() {
        return DeadlockHealthCheck.create(ManagementFactory.getThreadMXBean());
    }


    private HeapMemoryHealthCheck heapMemory(Config config) {
        return HeapMemoryHealthCheck.builder()
                .config(config)
                .build();
    }

    private DiskSpaceHealthCheck diskSpace(Config config) {
        return DiskSpaceHealthCheck.builder()
                .config(config)
                .build();
    }
}
