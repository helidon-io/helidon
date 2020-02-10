/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.spi;

import java.time.Instant;

import io.helidon.config.ChangeEventType;

public interface PollingStrategy {
    /**
     * Start this polling strategy.
     *
     * @param polled a component receiving polling events.
     */
    void start(Polled polled);

    default void stop() {
    }

    interface Polled {
        /**
         * Poll for changes.
         * The result may be used to modify behavior of the {@link io.helidon.config.spi.PollingStrategy} triggering this
         * poll event.
         *
         * @param when instant this polling request was created
         * @return result of the polling
         */
        ChangeEventType poll(Instant when);
    }
}
