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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.config.Config;
import io.helidon.messaging.IncomingConnectorContext;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.MessagingException;
import io.helidon.messaging.spi.IncomingChannel;
import io.helidon.messaging.spi.MessagingConnector;
import io.helidon.messaging.spi.OutgoingChannel;

final class CustomConnector implements MessagingConnector, RuntimeType.Api<CustomConnectorConfig> {
    private final CustomConnectorConfig config;

    private CustomConnector(CustomConnectorConfig config) {
        this.config = config;
    }

    static CustomConnector create(CustomConnectorConfig config) {
        return new CustomConnector(config);
    }

    static CustomConnectorConfig.Builder builder() {
        return CustomConnectorConfig.builder();
    }

    static CustomConnector create(Consumer<CustomConnectorConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    @Override
    public CustomConnectorConfig prototype() {
        return config;
    }

    @Override
    public String type() {
        return CustomConnectorProvider.CONNECTOR_TYPE;
    }

    String endpoint() {
        return config.endpoint();
    }

    String prefix() {
        return config.prefix();
    }

    @Override
    public Optional<IncomingChannel> incoming(Config channelConfig) {
        Objects.requireNonNull(channelConfig);
        return Optional.of(incoming(CustomChannelConfig.create(channelConfig)));
    }

    IncomingChannel incoming(CustomChannelConfig channelConfig) {
        Objects.requireNonNull(channelConfig);
        String endpoint = channelConfig.endpoint().orElse(config.endpoint());
        String prefix = channelConfig.prefix().orElse(config.prefix());
        config.probe().configured("incoming", channelConfig.channelName(), name(), endpoint, prefix);
        return new Incoming(config, channelConfig.channelName(), endpoint);
    }

    @Override
    public Optional<OutgoingChannel> outgoing(Config channelConfig) {
        Objects.requireNonNull(channelConfig);
        return Optional.of(outgoing(CustomChannelConfig.create(channelConfig)));
    }

    OutgoingChannel outgoing(CustomChannelConfig channelConfig) {
        Objects.requireNonNull(channelConfig);
        String endpoint = channelConfig.endpoint().orElse(config.endpoint());
        String prefix = channelConfig.prefix().orElse(config.prefix());
        config.probe().configured("outgoing", channelConfig.channelName(), name(), endpoint, prefix);
        return new Outgoing(config, channelConfig.channelName(), endpoint, prefix);
    }

    private static Message<String> stringMessage(Message<?> message) {
        if (!(message.entity() instanceof String)) {
            throw new IllegalArgumentException("Custom connector supports only String messages");
        }
        return castMessage(message);
    }

    @SuppressWarnings("unchecked")
    private static Message<String> castMessage(Message<?> message) {
        return (Message<String>) message;
    }

    private static final class Incoming implements IncomingChannel {
        private final CustomConnectorConfig config;
        private final String channelName;
        private final String endpoint;
        private final AtomicReference<Thread> owner = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean();

        private Incoming(CustomConnectorConfig config, String channelName, String endpoint) {
            this.config = config;
            this.channelName = channelName;
            this.endpoint = endpoint;
        }

        @Override
        public void run(IncomingConnectorContext context) {
            Thread current = Thread.currentThread();
            if (!owner.compareAndSet(null, current)) {
                throw new IllegalStateException("Custom incoming connector can only be run once");
            }
            try {
                if (closed.get() || !context.awaitRunning()) {
                    return;
                }
                config.probe().started(channelName);
                while (!closed.get()) {
                    try (var reservation = context.reserveDelivery()) {
                        CustomConnectorBroker.Item item = config.broker().receive(endpoint);
                        if (item == CustomConnectorBroker.StopItem.INSTANCE) {
                            return;
                        }
                        var message = ((CustomConnectorBroker.MessageItem) item).message();
                        try (var delivery = reservation.start(MessageBatch.create(message))) {
                            delivery.await();
                        }
                        config.probe().settled();
                    }
                }
            } catch (InterruptedException e) {
                if (!closed.get()) {
                    Thread.currentThread().interrupt();
                    throw new MessagingException("Custom incoming connector was interrupted", e);
                }
            } finally {
                owner.compareAndSet(current, null);
            }
        }

        @Override
        public void drain() {
            stop(false);
        }

        @Override
        public void forceClose() {
            stop(true);
        }

        @Override
        public void close() {
            stop(true);
            config.probe().closed(channelName);
        }

        private void stop(boolean interrupt) {
            if (closed.compareAndSet(false, true)) {
                config.broker().stop(endpoint);
            }
            if (interrupt) {
                Thread thread = owner.get();
                if (thread != null) {
                    thread.interrupt();
                }
            }
        }
    }

    private static final class Outgoing implements OutgoingChannel {
        private final CustomConnectorConfig config;
        private final String channelName;
        private final String endpoint;
        private final String prefix;
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private Outgoing(CustomConnectorConfig config, String channelName, String endpoint, String prefix) {
            this.config = config;
            this.channelName = channelName;
            this.endpoint = endpoint;
            this.prefix = prefix;
        }

        @Override
        public void start() {
            if (!started.compareAndSet(false, true)) {
                throw new IllegalStateException("Custom outgoing connector already started");
            }
            config.probe().started(channelName);
        }

        @Override
        public void sendBatch(MessageBatch<?> batch) {
            if (!started.get() || closed.get()) {
                throw new IllegalStateException("Custom outgoing connector is not running");
            }
            for (Message<?> message : batch) {
                Message<String> stringMessage = stringMessage(message);
                config.probe().sent(channelName, prefix, stringMessage);
                config.broker().send(endpoint, stringMessage);
            }
        }

        @Override
        public void forceClose() {
            close();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                config.probe().closed(channelName);
            }
        }
    }
}
