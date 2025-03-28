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

import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.NativeImageHelper;
import io.helidon.health.HealthCheckException;
import io.helidon.health.common.BuiltInHealthCheck;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

/**
 * A health check that looks for thread deadlocks. Automatically created and registered via CDI.
 * <p>
 * This health check can be referred to in properties as {@code deadlock}. So for example, to exclude this
 * health check from being exposed, use {@code health.exclude: deadlock}.
 * </p>
 */
@Liveness
@ApplicationScoped // this will be ignored if not within CDI
@BuiltInHealthCheck
public class DeadlockHealthCheck implements HealthCheck {
    private static final Logger LOGGER = Logger.getLogger(DeadlockHealthCheck.class.getName());
    private static final String NAME = "deadlock";

    /**
     * Used for detecting deadlocks. Injected in the constructor.
     */
    private final ThreadMXBean threadBean;
    private final boolean disabled;

    @Inject
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
     * @return a new health check to register with
     *         {@link io.helidon.health.HealthSupport.Builder#addLiveness(org.eclipse.microprofile.health.HealthCheck...)}
     */
    public static DeadlockHealthCheck create(ThreadMXBean threadBean) {
        return new DeadlockHealthCheck(threadBean);
    }

    @Override
    public HealthCheckResponse call() {
        if (disabled) {
            LOGGER.log(Level.FINEST, "Running in graal native image, this health-check always returns up.");
            return HealthCheckResponse.builder()
                    .name(NAME)
                    .withData("enabled", "false")
                    .withData("description", "in native image")
                    .up()
                    .build();
        }

        HealthCheckResponseBuilder builder = HealthCheckResponse.builder()
                .name(NAME);

        try {
            // Thanks to https://stackoverflow.com/questions/1102359/programmatic-deadlock-detection-in-java#1102410
            long[] deadlockedThreads = threadBean.findDeadlockedThreads();
            if (deadlockedThreads != null) {
                builder.down();
                LOGGER.log(Level.FINEST, "Health check observed deadlocked threads: " + Arrays.toString(deadlockedThreads));
            }
        } catch (Throwable e) {
            throw new HealthCheckException("Error invoking ThreadMXBean to find deadlocks; cannot complete this healthcheck", e);
        }
        return builder.build();
    }
}

