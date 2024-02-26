/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Map;

class RouterImpl implements Router {
    private static final RouterImpl EMPTY = RouterImpl.builder().build();

    private final Map<Class<?>, Routing> routings;

    private RouterImpl(Builder builder) {
        routings = new IdentityHashMap<>();
        builder.routings.values()
                .forEach(it -> {
                    Routing routing = it.build();
                    routings.put(routing.routingType(), routing);
                });
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

    @Override
    public List<? extends Routing> routings() {
        return List.copyOf(routings.values());
    }

    static class Builder implements Router.Builder {
        private static final System.Logger LOGGER = System.getLogger(Builder.class.getName());

        private final Map<Class<?>, io.helidon.common.Builder<?, ? extends Routing>> routings = new IdentityHashMap<>();

        private Builder() {
        }

        @Override
        public RouterImpl build() {
            return new RouterImpl(this);
        }

        @Override
        public Router.Builder addRouting(io.helidon.common.Builder<?, ? extends Routing> routing) {
            var previous = this.routings.put(routing.getClass(), routing);
            if (previous != null) {
                Thread.dumpStack();
                LOGGER.log(System.Logger.Level.WARNING, "Second routing of the same type is registered. "
                        + "The first instance will be ignored. Type: " + routing.getClass().getName());
            }
            return this;
        }

        @Override
        public List<io.helidon.common.Builder<?, ? extends Routing>> routings() {
            return List.copyOf(routings.values());
        }
    }
}
