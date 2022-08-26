/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver;

import java.util.EnumMap;
import java.util.Map;

import io.helidon.common.http.DirectHandler;

class DirectHandlers {
    private final Map<DirectHandler.EventType, DirectHandler> handlers;

    private DirectHandlers(Map<DirectHandler.EventType, DirectHandler> handlers) {
        this.handlers = new EnumMap<>(handlers);
    }

    static Builder builder() {
        return new Builder();
    }

    DirectHandler handler(DirectHandler.EventType eventType) {
        return handlers.get(eventType);
    }

    static class Builder implements io.helidon.common.Builder<Builder, DirectHandlers> {
        private final Map<DirectHandler.EventType, DirectHandler> handlers = new EnumMap<>(DirectHandler.EventType.class);
        private final DirectHandler defaultHandler = DirectHandler.defaultHandler();

        private Builder() {
        }

        @Override
        public DirectHandlers build() {
            for (DirectHandler.EventType value : DirectHandler.EventType.values()) {
                handlers.putIfAbsent(value, defaultHandler);
            }
            return new DirectHandlers(handlers);
        }

        Builder addHandler(DirectHandler.EventType eventType, DirectHandler handler) {
            handlers.put(eventType, handler);
            return this;
        }
    }
}
