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

package io.helidon.pico;

import java.time.Instant;
import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.builder.BuilderInterceptor;

/**
 * Log entry for lifecycle related events (i.e., activation startup and deactivation shutdown).
 *
 * @see ActivationLog
 * @see Activator
 * @see DeActivator
 */
@Builder(interceptor = ActivationLogEntry.Interceptor.class)
public interface ActivationLogEntry {

    /**
     * The event.
     *
     * @return the event
     */
    Event event();

    /**
     * Optionally, any special message being logged.
     *
     * @return the message
     */
    Optional<String> message();

    /**
     * Optionally, when this log entry pertains to a service provider activation.
     *
     * @return the activation result
     */
    Optional<ActivationResult> activationResult();

    /**
     * Optionally, the managing service provider the event pertains to.
     *
     * @return the managing service provider
     */
    Optional<ServiceProvider<?>> serviceProvider();

    /**
     * Optionally, the injection point that the event pertains to.
     *
     * @return the injection point
     */
    Optional<InjectionPointInfo> injectionPoint();

    /**
     * The time this event was generated.
     *
     * @return the time of the event
     */
    Instant time();

    /**
     * Any observed error during activation.
     *
     * @return any observed error
     */
    Optional<Throwable> error();

    /**
     * The thread id that the event occurred on.
     *
     * @return the thread id
     */
    long threadId();


    /**
     * Ensures that the non-nullable fields are populated with default values.
     */
    class Interceptor implements BuilderInterceptor<DefaultActivationLogEntry.Builder> {

        /**
         * Default Constructor.
         * @deprecated
         */
        Interceptor() {
        }

        @Override
        public DefaultActivationLogEntry.Builder intercept(DefaultActivationLogEntry.Builder b) {
            if (b.time() == null) {
                b.time(Instant.now());
            }

            if (b.threadId() == 0) {
                b.threadId(Thread.currentThread().getId());
            }

            if (b.event() == null) {
                b.event(Event.FINISHED);
            }

            return b;
        }
    }

}
