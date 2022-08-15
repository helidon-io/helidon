/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver;

import java.util.EnumMap;
import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.nima.webserver.SimpleHandler.EventType;

/**
 * Configured handlers for expected (and internal) exceptions.
 */
public class SimpleHandlers {
    private final Map<EventType, SimpleHandler> handlers;

    private SimpleHandlers(Map<EventType, SimpleHandler> handlers) {
        this.handlers = new EnumMap<>(handlers);
    }

    /**
     * New builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get handler for the event type.
     * If no custom handler is defined, the default handler will be returned.
     *
     * @param eventType event type
     * @return handler to use
     */
    public SimpleHandler handler(EventType eventType) {
        return handlers.get(eventType);
    }

    /**
     * Fluent API builder for {@link SimpleHandlers}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, SimpleHandlers> {
        private final Map<EventType, SimpleHandler> handlers = new EnumMap<>(EventType.class);
        private final SimpleHandler defaultHandler = new DefaultHandler();

        private Builder() {
        }

        @Override
        public SimpleHandlers build() {
            for (EventType value : EventType.values()) {
                handlers.putIfAbsent(value, defaultHandler);
            }
            return new SimpleHandlers(handlers);
        }

        /**
         * Add a handler.
         *
         * @param eventType event type to handle
         * @param handler   handler to handle that type
         * @return updated builder
         */
        public Builder addHandler(EventType eventType, SimpleHandler handler) {
            handlers.put(eventType, handler);
            return this;
        }
    }

    private static class DefaultHandler implements SimpleHandler {
        @Override
        public SimpleResponse handle(SimpleRequest request, EventType eventType, Http.Status defaultStatus, String message) {
            return SimpleResponse.builder()
                    .status(defaultStatus)
                    .update(it -> {
                        if (!message.isEmpty()) {
                            it.message(HtmlEncoder.encode(message));
                        }
                    })
                    .build();
        }
    }
}
