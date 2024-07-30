/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import java.time.Instant;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Log entry for lifecycle related events (i.e., activation startup and deactivation shutdown).
 *
 * @see ActivationLog
 * @see Activator
 * @see DeActivator
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint(decorator = ActivationLogEntryBlueprint.BuilderDecorator.class)
interface ActivationLogEntryBlueprint {

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
    @ConfiguredOption("0")
    long threadId();

    /**
     * Ensures that the non-nullable fields are populated with default values.
     */
    class BuilderDecorator implements Prototype.BuilderDecorator<ActivationLogEntry.BuilderBase<?, ?>> {

        BuilderDecorator() {
        }

        @Override
        public void decorate(ActivationLogEntry.BuilderBase<?, ?> b) {
            if (b.time().isEmpty()) {
                b.time(Instant.now());
            }

            if (b.threadId() == 0) {
                b.threadId(Thread.currentThread().threadId());
            }

            if (b.event().isEmpty()) {
                b.event(Event.FINISHED);
            }
        }
    }

}
