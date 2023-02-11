/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.maven.plugin;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.pico.tools.ToolsException;

/**
 * maven-plugins will be called on the same JVM on different mojo instances when maven is run in parallel mode. If you didn't know
 * this then you know it now. ;-)
 * <p>
 * Since pico is designed in traditional microprofile patterns - the JVM is the container for one app - this is a problem. So to
 * compensate for this, we apply a traffic cop to effectively serialize access to the mojo execute() method so that only one can
 * run at a time. The code is negligible in terms of performance, and still allows for parallel builds to occur.
 */
class TrafficCop {
    private final Semaphore semaphore;

    TrafficCop() {
        this.semaphore = new Semaphore(1);
    }

    GreenLight waitForGreenLight() {
        try {
            return new GreenLight(semaphore);
        } catch (InterruptedException e) {
            throw new ToolsException("interrupted", e);
        }
    }

    static class GreenLight implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private GreenLight(
                Semaphore semaphore) throws InterruptedException {
            this.semaphore = semaphore;
            semaphore.acquire();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }

}
