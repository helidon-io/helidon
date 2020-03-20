/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.health.common.BuiltInHealthCheck;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * A health check that looks for thread deadlocks. Automatically created and registered via CDI.
 *
 * This health check can be referred to in properties as "deadlock". So for example, to exclude this
 * health check from being exposed, use "helidon.health.exclude: deadlock".
 */
@Liveness
@ApplicationScoped // this will be ignored if not within CDI
@BuiltInHealthCheck
public final class DeadlockHealthCheck implements HealthCheck {
    private static final Logger LOGGER = Logger.getLogger(DeadlockHealthCheck.class.getName());

    /**
     * Used for detecting deadlocks. Injected in the constructor.
     */
    private final ThreadMXBean threadBean;

    @Inject
        // this will be ignored if not within CDI
    DeadlockHealthCheck(ThreadMXBean threadBean) {
        this.threadBean = threadBean;
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
        boolean noDeadLock = false;
        try {
            // Thanks to https://stackoverflow.com/questions/1102359/programmatic-deadlock-detection-in-java#1102410
            noDeadLock = (threadBean.findDeadlockedThreads() == null);
        } catch (Throwable e) {
            // ThreadBean does not work - probably in native image
            LOGGER.log(Level.FINEST, "Failed to find deadlocks in ThreadMXBean, ignoring this healthcheck", e);
        }
        return HealthCheckResponse.named("deadlock")
                .state(noDeadLock)
                .build();
    }
}

