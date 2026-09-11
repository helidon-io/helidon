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

package io.helidon.messaging.tests.custom.connector;

import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import io.helidon.messaging.Message;
import io.helidon.service.registry.Service;

@Service.Singleton
final class CustomConnectorBroker {
    private final Map<String, BlockingDeque<Item>> endpoints = new ConcurrentHashMap<>();

    void send(String endpoint, Message<String> message) {
        queue(endpoint).addLast(new MessageItem(message));
    }

    Item receive(String endpoint) throws InterruptedException {
        return queue(endpoint).takeFirst();
    }

    void stop(String endpoint) {
        queue(endpoint).addFirst(StopItem.INSTANCE);
    }

    private BlockingDeque<Item> queue(String endpoint) {
        return endpoints.computeIfAbsent(endpoint, _ -> new LinkedBlockingDeque<>());
    }

    enum StopItem implements Item {
        INSTANCE
    }

    sealed interface Item permits MessageItem, StopItem {
    }

    record MessageItem(Message<String> message) implements Item {
    }
}
