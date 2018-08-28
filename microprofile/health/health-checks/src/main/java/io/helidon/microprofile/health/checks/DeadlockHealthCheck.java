/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.health.checks;

import java.lang.management.ThreadMXBean;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

/**
 * A health check that looks for thread deadlocks. Automatically created and registered via CDI.
 *
 * This health check can be referred to in properties as "deadlock". So for example, to exclude this
 * health check from being exposed, use "helidon.health.exclude: deadlock".
 */
@Health
@ApplicationScoped
public final class DeadlockHealthCheck implements HealthCheck {
    /**
     * Used for detecting deadlocks. Injected in the constructor.
     */
    private final ThreadMXBean threadBean;

    @Inject
    DeadlockHealthCheck(ThreadMXBean threadBean) {
        this.threadBean = threadBean;
    }

    @Override
    public HealthCheckResponse call() {
        // Thanks to https://stackoverflow.com/questions/1102359/programmatic-deadlock-detection-in-java#1102410
        boolean noDeadLock = (threadBean.findDeadlockedThreads() == null);
        return HealthCheckResponse.named("deadlock")
                .state(noDeadLock)
                .build();
    }
}

