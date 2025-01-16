/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.util.List;

/**
 * Router for server.
 * Router contains routings of various types, so the server can handle each protocol as fast as possible.
 */
public interface Router {
    /**
     * Builder for router.
     *
     * @return a new builder
     */
    static Builder builder() {
        return RouterImpl.builder();
    }

    /**
     * Empty router.
     *
     * @return new empty router
     */
    static Router empty() {
        return RouterImpl.empty();
    }

    /**
     * Get routing of a specific type.
     *
     * @param routingType  type of the routing
     * @param defaultValue default value to use if the routing is not defined in this router
     * @param <T>          type of routing
     * @return routing defined or default value if not found
     */
    <T extends Routing> T routing(Class<T> routingType, T defaultValue);

    /**
     * This is called after server closes ports.
     */
    void afterStop();

    /**
     * This is called before server opens ports.
     */
    void beforeStart();

    /**
     * This is called after the server's listeners have successfully started.
     *
     * @param webServer the {@link WebServer} that has successfully started
     */
    default void afterStart(WebServer webServer) {
    }

    /**
     * List of all conifgured routings.
     *
     * @return all routings
     */
    List<? extends Routing> routings();

    /**
     * Builder for a standalone router.
     */
    interface Builder extends RouterBuilder<Builder>, io.helidon.common.Builder<Builder, Router> {
    }

    /**
     * Generic builder interface used by both {@link Router.Builder} and {@code io.helidon.webserver.WebServer.Builder}.
     *
     * @param <B> type of the builder
     */
    interface RouterBuilder<B extends RouterBuilder<B>> {
        /**
         * Add a new routing to this router.
         *
         * @param routing routing to add, such as {@code io.helidon.webserver.http.HttpRouting}
         * @return updated builder
         */
        B addRouting(io.helidon.common.Builder<?, ? extends Routing> routing);

        /**
         * List of all routing builders registered with this router builder.
         *
         * @return routing builder list
         */
        List<io.helidon.common.Builder<?, ? extends Routing>> routings();
    }
}
