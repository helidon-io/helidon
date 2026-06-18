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

package io.helidon.declarative.tests.graphql;

import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.graphql.GraphQl;
import io.helidon.webserver.graphql.GraphQlServer;

@GraphQlServer.Endpoint
class GraphEndpoint {
    private final AtomicBoolean enabled = new AtomicBoolean();

    @GraphQl.Query
    String hello(@GraphQl.Argument("name") String name) {
        return "Hello " + name;
    }

    @GraphQl.Query
    Book book() {
        return new Book("Dune", BookStatus.AVAILABLE, new Isbn("9780441172719"), "hidden");
    }

    @GraphQl.Query
    String titleByIsbn(@GraphQl.Argument("isbn") Isbn isbn) {
        return "Dune: " + isbn.value();
    }

    @GraphQlServer.Field
    String summary(@GraphQlServer.Source Book book, @GraphQl.Argument("prefix") String prefix) {
        return prefix + ": " + book.title();
    }

    @GraphQl.Query
    boolean enabled() {
        return enabled.get();
    }

    @GraphQl.Mutation
    boolean update(@GraphQl.Argument("enabled") boolean enabled) {
        this.enabled.set(enabled);
        return enabled;
    }
}
