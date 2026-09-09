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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.messaging.Message;
import io.helidon.service.registry.Service;

@Service.Singleton
final class CustomConnectorProbe {
    private final List<String> configured = new CopyOnWriteArrayList<>();
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final List<String> lifecycle = new CopyOnWriteArrayList<>();
    private final CountDownLatch settled = new CountDownLatch(1);

    void configured(String direction,
                    String channel,
                    String connector,
                    String endpoint,
                    String prefix) {
        configured.add(direction + ":" + channel + ":" + connector + ":" + endpoint + ":" + prefix);
    }

    List<String> configured() {
        return List.copyOf(configured);
    }

    void started(String channel) {
        lifecycle.add("started:" + channel);
    }

    void closed(String channel) {
        lifecycle.add("closed:" + channel);
    }

    List<String> lifecycle() {
        return List.copyOf(lifecycle);
    }

    void sent(String channel, String prefix, Message<String> message) {
        sent.add(channel + ":" + prefix + ":" + describe(message));
    }

    List<String> sent() {
        return List.copyOf(sent);
    }

    void received(String channel, Message<String> message) {
        received.add(channel + ":" + describe(message));
    }

    List<String> received() {
        return List.copyOf(received);
    }

    void settled() {
        settled.countDown();
    }

    boolean awaitSettled(Duration timeout) throws InterruptedException {
        return settled.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static String describe(Message<String> message) {
        return message.entity() + ":" + message.header("trace").orElseThrow();
    }
}
