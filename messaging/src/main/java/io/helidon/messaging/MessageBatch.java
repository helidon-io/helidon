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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.helidon.common.Api;

/**
 * Immutable ordered delivery batch.
 * <p>
 * Every messaging delivery is a batch. Single-message APIs create a singleton batch at their boundary, while the runtime,
 * routes, processors, failure policies, and connectors retain the batch abstraction. A batch is a delivery and performance
 * boundary, not a transaction.
 * <p>
 * Framework-derived batches preserve the opaque delivery identity and stable item lineage. This lets retry and dead-letter
 * outcomes be mapped without relying on message-envelope object identity.
 *
 * @param <T> payload type
 */
@Api.Preview
public final class MessageBatch<T> implements Iterable<Message<T>> {
    /**
     * Maximum opaque identity length.
     */
    public static final int MAX_ID_LENGTH = 256;

    private final Object deliveryToken;
    private final String id;
    private final List<Message<T>> messages;
    private final List<Integer> lineage;
    private volatile List<T> payloads;

    private MessageBatch(Object deliveryToken,
                         String id,
                         List<Message<T>> messages,
                         List<Integer> lineage) {
        this.deliveryToken = Objects.requireNonNull(deliveryToken);
        this.id = validateId(id);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Message batch must contain at least one message");
        }
        if (messages.size() != lineage.size()) {
            throw new IllegalArgumentException("Message batch lineage must contain one entry per message");
        }

        ArrayList<Message<T>> messageSnapshot = new ArrayList<>(messages.size());
        ArrayList<Integer> lineageSnapshot = new ArrayList<>(lineage.size());
        int previous = -1;
        for (int i = 0; i < messages.size(); i++) {
            Message<T> message = Objects.requireNonNull(messages.get(i));
            int item = Objects.requireNonNull(lineage.get(i));
            if (item < 0 || item <= previous) {
                throw new IllegalArgumentException("Message batch lineage must be non-negative and strictly increasing");
            }
            messageSnapshot.add(message);
            lineageSnapshot.add(item);
            previous = item;
        }
        this.messages = List.copyOf(messageSnapshot);
        this.lineage = List.copyOf(lineageSnapshot);
    }

    /**
     * Create an immutable batch by copying the supplied message list.
     *
     * @param messages ordered, non-empty messages
     * @param <T> payload type
     * @return immutable batch
     */
    public static <T> MessageBatch<T> create(List<? extends Message<? extends T>> messages) {
        return MessageBatch.<T>builder().messages(messages).build();
    }

    /**
     * Create an immutable singleton batch.
     *
     * @param message message
     * @param <T> payload type
     * @return singleton batch
     */
    public static <T> MessageBatch<T> create(Message<? extends T> message) {
        return MessageBatch.<T>builder().add(message).build();
    }

    /**
     * Create a batch builder.
     *
     * @param <T> payload type
     * @return builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Opaque delivery correlation ID shared by framework-derived batches.
     * <p>
     * ID equality alone does not establish common delivery lineage.
     *
     * @return stable, non-blank identity no longer than {@link #MAX_ID_LENGTH}
     */
    public String id() {
        return id;
    }

    /**
     * Ordered immutable message snapshot.
     *
     * @return messages
     */
    public List<Message<T>> messages() {
        return messages;
    }

    /**
     * Ordered immutable payload snapshot, materialized on first access.
     *
     * @return payloads
     * @throws MessagingException if any message cannot expose its payload
     * @throws NullPointerException if any message returns a null payload
     */
    public List<T> payloads() {
        List<T> result = payloads;
        if (result == null) {
            synchronized (this) {
                result = payloads;
                if (result == null) {
                    ArrayList<T> snapshot = new ArrayList<>(messages.size());
                    for (Message<T> message : messages) {
                        snapshot.add(Objects.requireNonNull(message.entity(), "Message entity"));
                    }
                    result = List.copyOf(snapshot);
                    payloads = result;
                }
            }
        }
        return result;
    }

    /**
     * Number of messages.
     *
     * @return message count
     */
    public int size() {
        return messages.size();
    }

    /**
     * Message at an index.
     *
     * @param index message index
     * @return message
     */
    public Message<T> get(int index) {
        return messages.get(index);
    }

    /**
     * Whether another framework-derived batch represents the exact same ordered delivery items.
     * <p>
     * Envelope instances may differ after a processor or dead-letter transformation. Exact lineage compatibility makes
     * local outcome indexes safe to translate between the two batches.
     *
     * @param other other batch
     * @return whether delivery identity and ordered item lineage are equal
     */
    boolean sameDelivery(MessageBatch<?> other) {
        return other != null && deliveryToken == other.deliveryToken && lineage.equals(other.lineage);
    }

    boolean isRetainedSubsetOf(MessageBatch<?> retained) {
        if (retained == null || deliveryToken != retained.deliveryToken || size() > retained.size()) {
            return false;
        }
        int retainedIndex = 0;
        for (int i = 0; i < size(); i++) {
            int itemLineage = lineage.get(i);
            while (retainedIndex < retained.size() && retained.lineage.get(retainedIndex) < itemLineage) {
                retainedIndex++;
            }
            if (retainedIndex >= retained.size()
                    || retained.lineage.get(retainedIndex) != itemLineage
                    || retained.messages.get(retainedIndex) != messages.get(i)) {
                return false;
            }
            retainedIndex++;
        }
        return true;
    }

    int lineageIndexIn(MessageBatch<?> ancestor, int localIndex) {
        int itemLineage = lineage.get(localIndex);
        if (ancestor == null || deliveryToken != ancestor.deliveryToken) {
            return -1;
        }
        return Collections.binarySearch(ancestor.lineage, itemLineage);
    }

    /**
     * Create an ordered retry or routing subset while preserving delivery identity and item lineage.
     *
     * @param indexes strictly increasing local indexes
     * @return derived subset
     */
    public MessageBatch<T> subset(List<Integer> indexes) {
        List<Integer> actualIndexes = List.copyOf(Objects.requireNonNull(indexes));
        if (actualIndexes.isEmpty()) {
            throw new IllegalArgumentException("Message batch subset must contain at least one item");
        }
        ArrayList<Message<T>> selectedMessages = new ArrayList<>(actualIndexes.size());
        ArrayList<Integer> selectedLineage = new ArrayList<>(actualIndexes.size());
        int previous = -1;
        for (int index : actualIndexes) {
            if (index < 0 || index >= size()) {
                throw new IndexOutOfBoundsException(index);
            }
            if (index <= previous) {
                throw new IllegalArgumentException("Message batch subset indexes must be strictly increasing");
            }
            selectedMessages.add(messages.get(index));
            selectedLineage.add(lineage.get(index));
            previous = index;
        }
        return new MessageBatch<>(deliveryToken, id, selectedMessages, selectedLineage);
    }

    /**
     * Create a one-to-one transformed batch while preserving delivery identity and item lineage.
     *
     * @param derivedMessages one derived message for every current item
     * @param <R> derived payload type
     * @return derived batch
     */
    @SuppressWarnings("unchecked")
    @Api.Internal
    public <R> MessageBatch<R> derive(List<? extends Message<? extends R>> derivedMessages) {
        List<? extends Message<? extends R>> actualMessages = List.copyOf(Objects.requireNonNull(derivedMessages));
        if (actualMessages.size() != size()) {
            throw new IllegalArgumentException("Derived message batch must contain one message per source item");
        }
        ArrayList<Message<R>> typedMessages = new ArrayList<>(actualMessages.size());
        for (Message<? extends R> message : actualMessages) {
            typedMessages.add((Message<R>) Objects.requireNonNull(message));
        }
        return new MessageBatch<>(deliveryToken, id, typedMessages, lineage);
    }

    @Override
    public Iterator<Message<T>> iterator() {
        return messages.iterator();
    }

    private static String validateId(String id) {
        String actualId = Objects.requireNonNull(id);
        if (actualId.isBlank()) {
            throw new IllegalArgumentException("Message batch identity must not be blank");
        }
        if (actualId.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Message batch identity must not exceed " + MAX_ID_LENGTH + " characters");
        }
        return actualId;
    }

    /**
     * Message batch builder.
     *
     * @param <T> payload type
     */
    public static final class Builder<T> {
        private final List<Message<T>> messages = new ArrayList<>();
        private String id = UUID.randomUUID().toString();

        private Builder() {
        }

        /**
         * Set the opaque delivery correlation ID.
         * <p>
         * Reusing an ID does not make independently built batches part of the same delivery lineage.
         *
         * @param id identity
         * @return updated builder
         */
        public Builder<T> id(String id) {
            this.id = validateId(id);
            return this;
        }

        /**
         * Add a message while preserving its exact envelope instance.
         *
         * @param message message
         * @return updated builder
         */
        @SuppressWarnings("unchecked")
        public Builder<T> add(Message<? extends T> message) {
            messages.add((Message<T>) Objects.requireNonNull(message));
            return this;
        }

        /**
         * Add messages by copying the supplied list.
         *
         * @param messages messages
         * @return updated builder
         */
        public Builder<T> messages(List<? extends Message<? extends T>> messages) {
            Objects.requireNonNull(messages).forEach(this::add);
            return this;
        }

        /**
         * Build an immutable batch.
         *
         * @return batch
         */
        public MessageBatch<T> build() {
            ArrayList<Integer> lineage = new ArrayList<>(messages.size());
            for (int i = 0; i < messages.size(); i++) {
                lineage.add(i);
            }
            return new MessageBatch<>(new Object(), id, messages, lineage);
        }
    }
}
