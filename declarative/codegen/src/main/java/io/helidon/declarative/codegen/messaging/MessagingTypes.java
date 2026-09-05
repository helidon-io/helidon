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

package io.helidon.declarative.codegen.messaging;

import java.util.Set;

import io.helidon.common.types.TypeName;

final class MessagingTypes {
    static final TypeName ARRAY_LIST = TypeName.create("java.util.ArrayList");
    static final Set<String> ASYNC_RETURN_TYPES = Set.of(
            "java.util.concurrent.CompletableFuture",
            "java.util.concurrent.CompletionStage",
            "java.util.concurrent.Flow.Publisher",
            "java.util.concurrent.Future",
            "java.util.stream.Stream",
            "org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder",
            "org.reactivestreams.Publisher");
    static final TypeName CONSUMER_REGISTRATION =
            TypeName.create("io.helidon.messaging.ConsumerRegistration");
    static final TypeName BATCH_DELIVERY_EXCEPTION =
            TypeName.create("io.helidon.messaging.BatchDeliveryException");
    static final TypeName BATCH_ITEM_OUTCOME =
            TypeName.create("io.helidon.messaging.BatchItemOutcome");
    static final TypeName EMITTER = TypeName.create("io.helidon.messaging.Emitter");
    static final TypeName EMITTER_REGISTRATION =
            TypeName.create("io.helidon.messaging.EmitterRegistration");
    static final TypeName ENTITY = TypeName.create("io.helidon.messaging.Messaging.Entity");
    static final TypeName FAILURE_DISPOSITION =
            TypeName.create("io.helidon.messaging.FailureDisposition");
    static final TypeName FAILURE_POLICY = TypeName.create("io.helidon.messaging.FailurePolicy");
    static final TypeName HEADER_PARAM =
            TypeName.create("io.helidon.messaging.Messaging.HeaderParam");
    static final TypeName MESSAGE_HEADER_VALUE = TypeName.create("io.helidon.messaging.MessageHeaderValue");
    static final TypeName MESSAGE = TypeName.create("io.helidon.messaging.Message");
    static final TypeName MESSAGE_BATCH = TypeName.create("io.helidon.messaging.MessageBatch");
    static final TypeName MESSAGING_ENTRY_POINT_BATCH_HANDLER =
            TypeName.create("io.helidon.messaging.MessagingEntryPoint.BatchHandler");
    static final TypeName MESSAGING_ENTRY_POINT_HANDLER =
            TypeName.create("io.helidon.messaging.MessagingEntryPoint.Handler");
    static final TypeName MESSAGING_ENTRY_POINTS =
            TypeName.create("io.helidon.messaging.MessagingEntryPoint.EntryPoints");
    static final TypeName MESSAGING_EXCEPTION =
            TypeName.create("io.helidon.messaging.MessagingException");
    static final TypeName MESSAGING_RUNTIME = TypeName.create("io.helidon.messaging.MessagingRuntime");
    static final TypeName OBJECTS = TypeName.create("java.util.Objects");
    static final TypeName ON_FAILURE = TypeName.create("io.helidon.messaging.Messaging.OnFailure");
    static final TypeName RECEIVE_FROM = TypeName.create("io.helidon.messaging.Messaging.ReceiveFrom");
    static final TypeName SEND_TO = TypeName.create("io.helidon.messaging.Messaging.SendTo");
    static final TypeName PROCESSOR_REGISTRATION =
            TypeName.create("io.helidon.messaging.ProcessorRegistration");

    private MessagingTypes() {
    }
}
