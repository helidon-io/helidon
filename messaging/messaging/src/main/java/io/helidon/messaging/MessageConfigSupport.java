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

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;

final class MessageConfigSupport {
    private MessageConfigSupport() {
    }

    @Prototype.RuntimeTypeFactoryMethod("message")
    static <T> Message<T> create(MessageConfig<T> config) {
        return new DefaultMessage<>(config.entity(),
                                    MessageHeaders.create(config.headers()),
                                    MessageMetadataImpl.create(config.localMetadata()));
    }

    /**
     * Set a portable text header, replacing all values with the same exact name.
     *
     * @param builder target builder
     * @param name header name
     * @param value header value
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    @Prototype.BuilderMethod
    static void header(MessageConfig.BuilderBase<?, ?, ?> builder, String name, String value) {
        header(builder, name, MessageHeaderValue.TextValue.create(Objects.requireNonNull(value, "value")));
    }

    /**
     * Set a portable typed header, replacing all values with the same exact name.
     *
     * @param builder target builder
     * @param name header name
     * @param value header value
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    @Prototype.BuilderMethod
    static void header(MessageConfig.BuilderBase<?, ?, ?> builder, String name, MessageHeaderValue value) {
        MessageHeader header = MessageHeader.create(name, value);
        builder.headers().removeIf(existing -> existing.name().equals(header.name()));
        builder.addHeader(header);
    }

    /**
     * Append a portable text header, retaining values with the same exact name.
     *
     * @param builder target builder
     * @param name header name
     * @param value header value
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    @Prototype.BuilderMethod
    static void addHeader(MessageConfig.BuilderBase<?, ?, ?> builder, String name, String value) {
        builder.addHeader(MessageHeader.create(name, value));
    }

    /**
     * Append a portable typed header, retaining values with the same exact name.
     *
     * @param builder target builder
     * @param name header name
     * @param value header value
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    @Prototype.BuilderMethod
    static void addHeader(MessageConfig.BuilderBase<?, ?, ?> builder, String name, MessageHeaderValue value) {
        builder.addHeader(MessageHeader.create(name, value));
    }

    /**
     * Replace all portable headers with the supplied immutable snapshot.
     *
     * @param builder target builder
     * @param headers portable headers
     * @throws NullPointerException if {@code headers} is {@code null}
     */
    @Prototype.BuilderMethod
    static void headers(MessageConfig.BuilderBase<?, ?, ?> builder, MessageHeaders headers) {
        builder.headers(Objects.requireNonNull(headers, "headers").entries());
    }

    /**
     * Replace all portable headers using an initially empty headers builder.
     *
     * @param builder target builder
     * @param consumer headers builder consumer
     * @throws NullPointerException if {@code consumer} is {@code null}
     */
    @Prototype.BuilderMethod
    static void headers(MessageConfig.BuilderBase<?, ?, ?> builder, Consumer<MessageHeaders.Builder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        MessageHeaders.Builder headers = MessageHeaders.builder();
        consumer.accept(headers);
        headers(builder, headers.build());
    }

    /**
     * Replace all portable headers using a supplier evaluated immediately.
     *
     * @param builder target builder
     * @param supplier portable headers supplier
     * @throws NullPointerException if {@code supplier} or its result is {@code null}
     */
    @Prototype.BuilderMethod
    static void headers(MessageConfig.BuilderBase<?, ?, ?> builder, Supplier<? extends MessageHeaders> supplier) {
        headers(builder, Objects.requireNonNull(supplier, "supplier").get());
    }

    /**
     * Set a local text metadata value, replacing the value with the same exact name.
     *
     * @param builder target builder
     * @param name exact metadata name
     * @param value text value
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    @Prototype.BuilderMethod
    static void localMetadata(MessageConfig.BuilderBase<?, ?, ?> builder, String name, String value) {
        builder.localMetadata(Objects.requireNonNull(name, "name"),
                              MessageHeaderValue.TextValue.create(Objects.requireNonNull(value, "value")));
    }

    /**
     * Replace all local metadata with the supplied immutable snapshot.
     *
     * @param builder target builder
     * @param localMetadata local metadata
     * @throws NullPointerException if {@code localMetadata} is {@code null}
     */
    @Prototype.BuilderMethod
    static void localMetadata(MessageConfig.BuilderBase<?, ?, ?> builder, MessageMetadata localMetadata) {
        builder.localMetadata(Objects.requireNonNull(localMetadata, "localMetadata").values());
    }

    /**
     * Replace all local metadata using an initially empty metadata builder.
     *
     * @param builder target builder
     * @param consumer metadata builder consumer
     * @throws NullPointerException if {@code consumer} is {@code null}
     */
    @Prototype.BuilderMethod
    static void localMetadata(MessageConfig.BuilderBase<?, ?, ?> builder, Consumer<MessageMetadata.Builder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        MessageMetadata.Builder metadata = MessageMetadata.builder();
        consumer.accept(metadata);
        localMetadata(builder, metadata.build());
    }

    /**
     * Replace all local metadata using a supplier evaluated immediately.
     *
     * @param builder target builder
     * @param supplier local metadata supplier
     * @throws NullPointerException if {@code supplier} or its result is {@code null}
     */
    @Prototype.BuilderMethod
    static void localMetadata(MessageConfig.BuilderBase<?, ?, ?> builder, Supplier<? extends MessageMetadata> supplier) {
        localMetadata(builder, Objects.requireNonNull(supplier, "supplier").get());
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<MessageConfig.BuilderBase<?, ?, ?>> {
        @Override
        public void decorate(MessageConfig.BuilderBase<?, ?, ?> target) {
            Objects.requireNonNull(target.entity().orElse(null), "entity");
            target.headers().forEach(header -> Objects.requireNonNull(header, "header"));
            target.localMetadata().forEach((name, value) -> {
                Objects.requireNonNull(name, "metadata name");
                Objects.requireNonNull(value, "metadata value");
            });
        }
    }
}
