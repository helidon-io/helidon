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

package io.helidon.webserver;

import java.util.IdentityHashMap;
import java.util.Map;

class RouterImpl implements Router {
    private static final RouterImpl EMPTY = RouterImpl.builder().build();

    private final Map<Class<?>, Routing> routings;
    private final Map<Class<? extends Routing>, Routing.Builder> routingBuilders;

    private RouterImpl(Builder builder) {
        this.routings = new IdentityHashMap<>(builder.routings);
        this.routingBuilders = new IdentityHashMap<>(builder.routingBuilders);
    }

    static Builder builder() {
        return new Builder();
    }

    static RouterImpl empty() {
        return EMPTY;
    }

    @Override
    public <T extends Routing> T routing(Class<T> routingType, T defaultValue) {
        T routing = routingType.cast(routings.get(routingType));
        if (routing == null) {
            Routing.Builder builder = routingBuilders.get(routingType);
            if (builder != null) {
                return routingType.cast(builder.build());
            }
        }
        return routing == null ? defaultValue : routing;
    }

    @Override
    public void afterStop() {
        for (Routing value : routings.values()) {
            value.afterStop();
        }
    }

    @Override
    public void beforeStart() {
        for (Routing value : routings.values()) {
            value.beforeStart();
        }
    }

    <T extends Routing> Object routingBuilder(Class<T> routingType) {
        return routingType.cast(routingBuilders.get(routingType).build());
    }

    static class Builder implements Router.Builder {
        private static final System.Logger LOGGER = System.getLogger(Builder.class.getName());

        private final Map<Class<?>, Routing> routings = new IdentityHashMap<>();
        private final Map<Class<? extends Routing>, Routing.Builder> routingBuilders = new IdentityHashMap<>();

        private Builder() {
        }

        @Override
        public RouterImpl build() {
            return new RouterImpl(this);
        }

        @Override
        public Builder addRouting(Routing routing) {
            Routing previous = this.routings.put(routing.getClass(), routing);
            if (previous != null) {
                LOGGER.log(System.Logger.Level.WARNING, "Second routing of the same type is registered. "
                        + "The first instance will be ignored. Type: " + routing.getClass().getName());
            }
            return this;
        }

        Builder addRoutingBuilder(Class<? extends Routing> routingType, Routing.Builder routingBuilder){
            this.routingBuilders.put(routingType, routingBuilder);
            return this;
        }
    }
}
