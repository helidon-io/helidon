/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Api;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;

/**
 * Messaging entry-point interception contracts used by generated handler registrations.
 */
@Api.Preview
public final class MessagingEntryPoint {
    private MessagingEntryPoint() {
    }

    /**
     * Invocation of a declarative messaging handler.
     *
     * @param <T> service type
     */
    @FunctionalInterface
    public interface Handler<T> {
        /**
         * Invoke the handler for one incoming message.
         *
         * @param serviceInstance intercepted service instance
         * @param message incoming message
         * @return produced message for a processor, or empty for a terminal consumer
         * @throws Exception if invocation fails
         */
        Optional<Message<?>> handle(T serviceInstance, Message<?> message) throws Exception;
    }

    /**
     * Invocation of an explicit declarative messaging batch handler.
     *
     * @param <T> service type
     */
    @FunctionalInterface
    public interface BatchHandler<T> {
        /**
         * Invoke the handler once for the complete immutable batch.
         *
         * @param serviceInstance intercepted service instance
         * @param messages immutable message batch
         * @throws Exception if invocation fails
         */
        void handle(T serviceInstance, MessageBatch<?> messages) throws Exception;
    }

    /**
     * Factory used by generated code to wrap messaging handlers with entry-point interceptors.
     */
    @Service.Contract
    public interface EntryPoints {
        /**
         * Create an intercepted handler for an original service method.
         *
         * @param descriptor original service descriptor
         * @param typeQualifiers original service qualifiers
         * @param typeAnnotations annotations on the original service type
         * @param methodInfo original service method metadata
         * @param actualHandler handler that invokes the original service method
         * @param <T> service type
         * @return intercepted handler
         */
        <T> Handler<T> handler(ServiceDescriptor<?> descriptor,
                               Set<Qualifier> typeQualifiers,
                               List<Annotation> typeAnnotations,
                               TypedElementInfo methodInfo,
                               Handler<T> actualHandler);

        /**
         * Create an intercepted batch handler for an original service method.
         *
         * @param descriptor original service descriptor
         * @param typeQualifiers original service qualifiers
         * @param typeAnnotations annotations on the original service type
         * @param methodInfo original service method metadata
         * @param actualHandler handler that invokes the original service method
         * @param <T> service type
         * @return intercepted batch handler
         */
        <T> BatchHandler<T> batchHandler(ServiceDescriptor<?> descriptor,
                                         Set<Qualifier> typeQualifiers,
                                         List<Annotation> typeAnnotations,
                                         TypedElementInfo methodInfo,
                                         BatchHandler<T> actualHandler);
    }
}
