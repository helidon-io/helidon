/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.sse.webserver;

import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;

/**
 * An SSE event.
 */
public class SseEvent {

    private final String id;
    private final String name;
    private final Object data;
    private final String comment;
    private final MediaType mediaType;
    private final long reconnectMillis;

    private SseEvent(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.data = builder.data;
        this.comment = builder.comment;
        this.mediaType = builder.mediaType;
        this.reconnectMillis = builder.reconnectMillis;
    }

    /**
     * Creates a new SSE event with data.
     *
     * @param data data for the event
     * @return newly created SSE event
     */
    public static SseEvent create(Object data) {
        return builder().data(data).build();
    }

    /**
     * Creates builder for this class.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get data for this event.
     *
     * @return the data
     */
    public Object data() {
        return data;
    }

    /**
     * Get media type for this event.
     *
     * @return the media type
     */
    public MediaType mediaType() {
        return mediaType;
    }

    /**
     * Get optional ID for this event.
     *
     * @return optional ID
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Get optional name for this event.
     *
     * @return optional name
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Get optional comment for this event.
     *
     * @return optional comment
     */
    public Optional<String> comment() {
        return Optional.ofNullable(comment);
    }

    /**
     * Get optional reconnect for this event.
     *
     * @return optional reconnect
     */
    public Optional<Long> reconnectMillis() {
        return Optional.ofNullable(reconnectMillis);
    }

    /**
     * Fluent API builder for {@link SseEvent}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, SseEvent> {

        private String id;
        private String name;
        private Object data;
        private String comment;
        private MediaType mediaType = MediaTypes.TEXT_PLAIN;
        private long reconnectMillis;

        private Builder() {
        }

        @Override
        public SseEvent build() {
            return new SseEvent(this);
        }

        /**
         * Set the event id. Optional.
         *
         * @param id the id
         * @return updated builder instance
         */
        Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Set event name. Optional.
         *
         * @param name event name
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set comment string associated with the event. Optional. The comment will be
         * serialized with the event, before event data is serialized.
         *
         * @param comment comment string
         * @return updated builder instance
         */
        public Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        /**
         * Set the media type for the event data. Default value is {@link MediaTypes#TEXT_PLAIN}.
         *
         * @param mediaType media type of event data
         * @return updated builder instance
         */
        Builder mediaType(MediaType mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        /**
         * Set event data.
         *
         * @param data event data
         * @return updated builder instance
         */
        Builder data(Object data) {
            this.data = data;
            return this;
        }

        /**
         * Set reconnection delay (in milliseconds) that indicates how long the event receiver
         * should wait before attempting to reconnect in case a connection is lost. Optional.
         *
         * @param reconnectMillis reconnection delay in milliseconds
         * @return updated builder instance
         */
        Builder reconnectDelay(long reconnectMillis) {
            this.reconnectMillis = reconnectMillis;
            return this;
        }
    }
}
