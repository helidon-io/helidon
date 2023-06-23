/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.List;

import io.helidon.common.NativeImageHelper;
import io.helidon.common.config.Config;
import io.helidon.health.HealthCheck;

/**
 * Utility class for built-in {@link io.helidon.health.HealthCheck health checks}.
 *
 * @see #healthChecks()
 */
public final class HealthChecks {

    static final String CONFIG_KEY_HEALTH_PREFIX = "helidon.health";

    private HealthChecks() {
    }

    /**
     * Deadlock health check.
     *
     * @return deadlock health check
     */
    public static HealthCheck deadlockCheck() {
        return DeadlockHealthCheck.create(ManagementFactory.getThreadMXBean());
    }

    /**
     * Disk space health check.
     *
     * @return disk space health check with default configuration
     * @see DiskSpaceHealthCheck#builder()
     */
    public static HealthCheck diskSpaceCheck() {
        return DiskSpaceHealthCheck.create();
    }

    /**
     * Disk space health check, set up via config.
     *
     * @param config configuration to use in setting up the disk space check
     * @return disk space health check with default configuration
     * @see DiskSpaceHealthCheck#builder()
     */
    public static HealthCheck diskSpaceCheck(Config config) {
        return DiskSpaceHealthCheck.builder().config(config).build();
    }

    /**
     * Memory health check.
     *
     * @return memory health check with default configuration
     * @see HeapMemoryHealthCheck#builder()
     */
    public static HeapMemoryHealthCheck heapMemoryCheck() {
        return HeapMemoryHealthCheck.create();
    }

    /**
     * Memory health check.
     *
     * @param config the configuration to use in setting up the heap memory check
     * @return memory health check with default configuration
     * @see HeapMemoryHealthCheck#builder()
     */
    public static HeapMemoryHealthCheck heapMemoryCheck(Config config) {
        return HeapMemoryHealthCheck.builder().config(config).build();
    }

    /**
     * Built-in health checks.
     *
     * @return built-in health checks
     */
    public static HealthCheck[] healthChecks() {
        if (NativeImageHelper.isNativeImage()) {
            return new HealthCheck[] {
                    //diskSpaceCheck(), // - bug
                    heapMemoryCheck()
            };
        } else {
            return new HealthCheck[] {
                    deadlockCheck(),
                    diskSpaceCheck(),
                    heapMemoryCheck()
            };
        }
    }

    /**
     * Built-in health checks, set up using "helidon.health" configuration.
     *
     * @param config configuration rooted at "helidon.health"
     * @return built-in health checks, set up using the provided configuration
     */
    public static HealthCheck[] healthChecks(Config config) {
        List<HealthCheck> result = new ArrayList<>();
        if (!NativeImageHelper.isNativeImage()) {
            result.add(deadlockCheck());
            result.add(diskSpaceCheck(config.get(DiskSpaceHealthCheck.CONFIG_KEY_DISKSPACE_PREFIX)));
        }
        result.add(heapMemoryCheck(config.get(HeapMemoryHealthCheck.CONFIG_KEY_HEAP_PREFIX)));

        return result.toArray(new HealthCheck[0]);
    }
}
