/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
     * The activation event.
     */
    enum Event {
        /**
         * Starting.
         */
        STARTING,

        /**
         * Finished.
         */
        FINISHED
    }

    /**
     * The managing service provider the event pertains to.
     *
     * @return the managing service provider
     */
    Optional<ServiceProvider<?>> serviceProvider();

    /**
     * The event.
     *
     * @return the event
     */
    Event event();

    /**
     * The starting activation phase.
     *
     * @return the starting activation phase
     */
    ActivationPhase startingActivationPhase();

    /**
     * The eventual/desired/target activation phase.
     *
     * @return the eventual/desired/target activation phase
     */
    ActivationPhase targetActivationPhase();

    /**
     * The finishing phase at the time of this event's log entry.
     *
     * @return the actual finishing phase
     */
    ActivationPhase finishingActivationPhase();

    /**
     * The finishing activation status at the time of this event's log entry.
     *
     * @return the activation status
     */
    ActivationStatus finishingStatus();

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
        @Override
        public DefaultActivationLogEntry.Builder intercept(DefaultActivationLogEntry.Builder b) {
            if (b.time() == null) {
                b.time(Instant.now());
            }

            if (b.threadId() == 0) {
                b.threadId(Thread.currentThread().getId());
            }

            if (b.startingActivationPhase() == null) {
                b.startingActivationPhase(ActivationPhase.INIT);
            }

            if (b.finishingStatus() == null) {
                b.finishingActivationPhase(b.startingActivationPhase());
            }

            if (b.event() == null) {
                b.event(Event.STARTING);
            }

            if (b.finishingStatus() == null) {
                b.finishingStatus(ActivationStatus.SUCCESS);
            }

            return b;
        }
    }

}
