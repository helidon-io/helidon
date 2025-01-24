/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

import io.helidon.common.NativeImageHelper;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckResponse.Status;
import io.helidon.health.HealthCheckType;

/**
 * A health check that looks for thread deadlocks. Automatically created and registered via CDI.
 * <p>
 * This health check can be referred to in properties as {@code deadlock}. So for example, to exclude this
 * health check from being exposed, use {@code helidon.health.exclude: deadlock}.
 */
public class DeadlockHealthCheck implements HealthCheck {
    private static final System.Logger LOGGER = System.getLogger(DeadlockHealthCheck.class.getName());
    private static final String NAME = "deadlock";
    private static final String PATH = "deadlock";

    /**
     * Used for detecting deadlocks.
     */
    private final ThreadMXBean threadBean;
    private final boolean disabled;

        // this will be ignored if not within CDI
    DeadlockHealthCheck(ThreadMXBean threadBean) {
        this.threadBean = threadBean;
        // in Graal native image, we cannot use this check, as it would always fail
        this.disabled = NativeImageHelper.isNativeImage();
    }

    /**
     * Create a new deadlock health check to use.
     *
     * @param threadBean thread mx bean to get thread monitoring data from
     * @return a new health check
     */
    public static DeadlockHealthCheck create(ThreadMXBean threadBean) {
        return new DeadlockHealthCheck(threadBean);
    }

    /**
     * Create a new deadlock health check to use.
     *
     * @return a new health check
     */
    public static DeadlockHealthCheck create() {
        return create(ManagementFactory.getThreadMXBean());
    }

    @Override
    public HealthCheckType type() {
        return HealthCheckType.LIVENESS;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String path() {
        return PATH;
    }

    @Override
    public HealthCheckResponse call() {
        if (disabled) {
            LOGGER.log(Level.TRACE, "Running in graal native image, this health-check always returns up.");
            return HealthCheckResponse.builder()
                    .detail("enabled", "false")
                    .detail("description", "in native image")
                    .status(Status.UP)
                    .build();
        }

        HealthCheckResponse.Builder builder = HealthCheckResponse.builder();

        try {
            // Thanks to https://stackoverflow.com/questions/1102359/programmatic-deadlock-detection-in-java#1102410
            long[] deadlockedThreads = threadBean.findDeadlockedThreads();
            if (deadlockedThreads != null) {
                builder.status(Status.DOWN);
                LOGGER.log(Level.TRACE, "Health check observed deadlocked threads: " + Arrays.toString(deadlockedThreads));
            }
        } catch (Throwable e) {
            // ThreadBean does not work - probably in native image. Report UP, not ERROR, because we do not want that failure to
            // contaminate the overall health result.
            LOGGER.log(Level.TRACE, "Error invoking ThreadMXBean to find deadlocks; cannot complete this healthcheck", e);
            builder.status(Status.ERROR);
        }
        return builder.build();
    }
}

