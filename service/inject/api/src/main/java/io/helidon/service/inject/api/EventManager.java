/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.api;

import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import io.helidon.common.types.ResolvedType;
import io.helidon.service.registry.Service;

/**
 * Event manager is used by generated code to manage events and listeners.
 */
@Service.Contract
public interface EventManager {
    /**
     * Register an event consumer.
     *
     * @param eventType     type of event
     * @param eventConsumer consumer accepting the event
     * @param qualifiers    qualifiers the consumer is interested in
     * @param <T>           type of the event object
     */
    <T> void register(ResolvedType eventType, Consumer<T> eventConsumer, Set<Qualifier> qualifiers);

    /**
     * Register an asynchronous event consumer.
     *
     * @param eventType     type of event
     * @param eventConsumer consumer accepting the event
     * @param qualifiers    qualifiers the consumer is interested in
     * @param <T>           type of the event object
     */
    <T> void registerAsync(ResolvedType eventType, Consumer<T> eventConsumer, Set<Qualifier> qualifiers);

    /**
     * Emit an event.
     *
     * @param eventObjectType type of event
     * @param eventObject     event object instance
     * @param qualifiers      qualifiers of the producer of the event
     */
    void emit(ResolvedType eventObjectType,
              Object eventObject,
              Set<Qualifier> qualifiers);

    /**
     * Emit an asynchronous event.
     *
     * @param eventObjectType type of event
     * @param eventObject     event object instance
     * @param qualifiers      qualifiers of the producer of the event
     * @param <T>             type of the event object
     * @return completion stage that completes when all synchronous event observers are notified; it may end exceptionally,
     *         if any of these throws an exception - in such a case all exceptions are available through the cause, or suppressed
     *         exceptions of {@link io.helidon.service.inject.api.EventDispatchException}
     */
    <T> CompletionStage<T> emitAsync(ResolvedType eventObjectType,
                                     T eventObject,
                                     Set<Qualifier> qualifiers);
}
