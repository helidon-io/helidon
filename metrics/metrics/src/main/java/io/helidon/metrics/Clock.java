/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.metrics;

import java.time.Duration;

/**
 * Clock interface to allow replacing system clock with
 * a custom one (e.g. for unit testing).
 */
interface Clock {
    /**
     * System clock. Please do not use directly, use {@link #system()}.
     * This is only visible as we cannot do private modifier in interfaces yet.
     */
    Clock SYSTEM = new Clock() {
        @Override
        public long nanoTick() {
            return System.nanoTime();
        }

        @Override
        public long milliTime() {
            return System.currentTimeMillis();
        }
    };

    /**
     * Create a clock based on system time.
     *
     * @return clock based on system time
     */
    static Clock system() {
        return SYSTEM;
    }

    /**
     * A nanosecond precision tick to use for time
     * measurements. The value is meaningless, it just must
     * increase correctly during a JVM runtime.
     *
     * @return nanosecond value
     */
    long nanoTick();

    /**
     * A millisecond precision current time, such as provided
     * by {@link System#currentTimeMillis()}.
     *
     * @return current time in milliseconds
     */
    long milliTime();
}
