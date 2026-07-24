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

package io.helidon.webserver.http;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.common.LazyValue;
import io.helidon.common.context.Context;

/**
 * Support for requesting and providing the matching route path for an HTTP request.
 * <p>
 * A component early in request processing (such as an SE filter) can register a request for the route path by invoking
 * {@link #requestRoute(io.helidon.common.context.Context, java.util.function.Consumer)}. An intervening component
 * (such as a request handler) participating in the contract invokes
 * {@link #provideRoute(io.helidon.common.context.Context, java.util.function.Supplier)} to register its provider for the
 * route path; this call acts as a no-op if there are no requesters.
 * <p>
 * Then later, the requesting component uses the {@code Consumer<Supplier<String>>} it passed earlier to retrieve the route,
 * if any was reported.
 */
@Api.Internal
public final class RoutePathSupport {
    private static final String ROUTE_PATH_CONSUMERS = RoutePathSupport.class.getName() + ".consumers";

    private RoutePathSupport() {
    }

    /**
     * Requests the route path for the current request.
     *
     * @param context request context
     * @param consumer consumer of the route path supplier
     */
    public static void requestRoute(Context context, Consumer<Supplier<String>> consumer) {
        Objects.requireNonNull(context, "Parameter 'context' is null!");
        Objects.requireNonNull(consumer, "Parameter 'consumer' is null!");

        routePathConsumers(context).add(consumer);
    }

    /**
     * Provides the route path supplier for the current request if any route path consumers requested it.
     *
     * @param context request context
     * @param routeSupplier route path supplier
     */
    public static void provideRoute(Context context, Supplier<String> routeSupplier) {
        Objects.requireNonNull(context, "Parameter 'context' is null!");
        Objects.requireNonNull(routeSupplier, "Parameter 'routeSupplier' is null!");

        context.get(ROUTE_PATH_CONSUMERS, RoutePathConsumers.class)
                .ifPresent(consumers -> consumers.provide(memoize(routeSupplier)));
    }

    private static RoutePathConsumers routePathConsumers(Context context) {
        return context.get(ROUTE_PATH_CONSUMERS, RoutePathConsumers.class)
                .orElseGet(() -> {
                    RoutePathConsumers consumers = new RoutePathConsumers();
                    context.register(ROUTE_PATH_CONSUMERS, consumers);
                    return consumers;
                });
    }

    private static Supplier<String> memoize(Supplier<String> supplier) {
        return LazyValue.create(supplier);
    }

    private static final class RoutePathConsumers {
        private final List<Consumer<Supplier<String>>> consumers = new CopyOnWriteArrayList<>();

        private void add(Consumer<Supplier<String>> consumer) {
            consumers.add(consumer);
        }

        private void provide(Supplier<String> routeSupplier) {
            consumers.forEach(consumer -> consumer.accept(routeSupplier));
        }
    }

}
