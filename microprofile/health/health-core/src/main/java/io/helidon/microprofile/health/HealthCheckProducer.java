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

package io.helidon.microprofile.health;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

/**
 * A CDI producer for providing various objects that would otherwise be looked up via static methods.
 * This was useful for testing and decoupling the code using these dependencies from the process by which
 * these dependencies are looked up.
 */
@ApplicationScoped
class HealthCheckProducer {
    /**
     * Gets a ThreadMXBean implementation.
     *
     * @return a non-null ThreadMXBean
     */
    @Produces
    ThreadMXBean produceThreadMXBean() {
        return ManagementFactory.getThreadMXBean();
    }

    /**
     * Gets a RuntimeMXBean implementation.
     *
     * @return a non-null RuntimeMXBean
     */
    @Produces
    RuntimeMXBean produceRuntimeMXBean() {
        return ManagementFactory.getRuntimeMXBean();
    }

    /**
     * Gets a Runtime implementation.
     *
     * @return a non-null Runtime
     */
    @Produces
    Runtime produceRuntime() {
        return Runtime.getRuntime();
    }
}
