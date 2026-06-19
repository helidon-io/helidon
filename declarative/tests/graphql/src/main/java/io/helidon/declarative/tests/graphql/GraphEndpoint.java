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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.context.Context;
import io.helidon.graphql.GraphQl;
import io.helidon.graphql.server.ExecutionContext;
import io.helidon.metrics.api.Metrics;
import io.helidon.security.SecurityContext;
import io.helidon.security.abac.role.RoleValidator;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracing;
import io.helidon.validation.Validation;
import io.helidon.webserver.graphql.GraphQlServer;

import graphql.schema.DataFetchingEnvironment;

@GraphQlServer.Endpoint
class GraphEndpoint {
    private final AtomicBoolean enabled = new AtomicBoolean();

    @GraphQl.Query
    @Metrics.Counted(value = "graphql-metered-hello", absoluteName = true)
    String hello(@GraphQl.Argument("name") String name) {
        return "Hello " + name;
    }

    @GraphQl.Query
    @Tracing.Traced(value = "graphql-traced-hello", kind = Span.Kind.SERVER)
    String tracedHello(@GraphQl.Argument("name") @Tracing.ParamTag String name) {
        return "Traced " + name;
    }

    @GraphQl.Query
    String validatedGreeting(@GraphQl.Argument("name")
                             @GraphQl.DefaultValue("\"Reader\"")
                             @Validation.String.NotBlank String name) {
        return "Validated " + name;
    }

    @GraphQl.Query
    Book book() {
        return new Book("Dune",
                        BookStatus.AVAILABLE,
                        new Isbn("9780441172719"),
                        List.of("classic", "desert"),
                        List.of(new Isbn("9780441172720"), new Isbn("9780441172721")),
                        "hidden");
    }

    @GraphQl.Query
    List<Book> recommendedBooks() {
        return List.of(book());
    }

    @GraphQl.Query
    String titleByIsbn(@GraphQl.Argument("isbn") Isbn isbn) {
        return "Dune: " + isbn.value();
    }

    @GraphQl.Query
    String filteredTitle(@GraphQl.Argument("search") @GraphQl.NonNull BookSearch search) {
        return search.phrase() + ": " + search.minimumScore() + ": " + search.includeUnavailable()
                + ": " + search.status()
                + ": " + search.tags()
                + ": " + search.statuses()
                + ": " + isbnValues(search.isbns())
                + ": " + filterValues(search.filters());
    }

    @GraphQl.Query
    String statusName(@GraphQl.Argument("status") BookStatus status) {
        return status.name();
    }

    @GraphQl.Query
    String statusNames(@GraphQl.Argument("statuses") List<BookStatus> statuses) {
        return statuses.toString();
    }

    @GraphQl.Query
    String isbnValues(@GraphQl.Argument("isbns") List<Isbn> isbns) {
        return isbns.stream()
                .map(Isbn::value)
                .toList()
                .toString();
    }

    @GraphQl.Query
    String structured(@GraphQl.Argument("value") StructuredValue value) {
        return value == null ? "null" : StructuredValueScalar.describe(value.value());
    }

    @GraphQl.Query
    boolean contextAvailable(Context context,
                             ExecutionContext executionContext,
                             DataFetchingEnvironment environment) {
        boolean matchingContext = executionContext.contextValue(ExecutionContext.HELIDON_CONTEXT_KEY, Context.class)
                .filter(it -> it == context)
                .isPresent();
        boolean matchingExecutionContext = executionContext.contextValue(ExecutionContext.EXECUTION_CONTEXT_KEY,
                                                                         ExecutionContext.class)
                .filter(it -> it == executionContext)
                .isPresent();
        return matchingContext && matchingExecutionContext && "contextAvailable".equals(environment.getField().getName());
    }

    @GraphQl.Query
    @Authenticated
    @Authorized
    @RoleValidator.Roles("admin")
    String securedMessage(SecurityContext securityContext) {
        return "Secured " + securityContext.userName();
    }

    @GraphQlServer.Field
    String summary(@GraphQlServer.Source Book book,
                   @GraphQl.Argument("prefix") String prefix,
                   @GraphQl.Argument("tags") List<String> tags) {
        return prefix + ": " + book.title() + ": " + tags;
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

    private static String filterValues(List<BookFilter> filters) {
        return filters.stream()
                .map(filter -> filter.field() + "=" + filter.value())
                .toList()
                .toString();
    }
}
