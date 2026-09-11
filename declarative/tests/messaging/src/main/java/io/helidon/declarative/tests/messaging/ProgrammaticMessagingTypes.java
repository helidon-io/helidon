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

package io.helidon.declarative.tests.messaging;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.helidon.messaging.Message;
import io.helidon.messaging.Messaging;
import io.helidon.service.registry.Service;

class ProgrammaticMessagingTypes {
    static final String STRING_CHANNEL = "programmatic-generated-string";
    static final String NESTED_CHANNEL = "programmatic-generated-nested";
    static final String CONNECTOR_CHANNEL = "programmatic-generated-connector";

    private ProgrammaticMessagingTypes() {
    }

    interface ConnectorMessage<T> extends Message<T> {
    }

    @Service.Singleton
    static class StringConsumer {
        private final CompletableFuture<Message<String>> received = new CompletableFuture<>();

        @Messaging.ReceiveFrom(STRING_CHANNEL)
        void consume(Message<String> message) {
            received.complete(message);
        }

        CompletableFuture<Message<String>> received() {
            return received;
        }
    }

    @Service.Singleton
    static class NestedConsumer {
        private final CompletableFuture<Message<List<String>>> received = new CompletableFuture<>();

        @Messaging.ReceiveFrom(NESTED_CHANNEL)
        void consume(Message<List<String>> message) {
            received.complete(message);
        }

        CompletableFuture<Message<List<String>>> received() {
            return received;
        }
    }

    @Service.Singleton
    static class ConnectorConsumer {
        @Messaging.ReceiveFrom(CONNECTOR_CHANNEL)
        void consume(ConnectorMessage<String> message) {
            throw new AssertionError("Incompatible graph must not dispatch to a connector-only consumer");
        }
    }
}
