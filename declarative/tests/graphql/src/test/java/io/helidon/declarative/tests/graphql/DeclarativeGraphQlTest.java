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

import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValueType;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@SuppressWarnings("helidon:api:preview")
@ServerTest
class DeclarativeGraphQlTest {
    private final Http1Client client;

    DeclarativeGraphQlTest(Http1Client client) {
        this.client = client;
    }

    @Test
    void testGeneratedSchemaEndpoint() {
        try (Http1ClientResponse response = client.get("/graphql/schema.graphql")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            String schema = response.as(String.class);

            assertThat(schema, containsString("type Query"));
            assertThat(schema, containsString("hello(name: String): String"));
            assertThat(schema, containsString("catalogName: String"));
            assertThat(schema, containsString("validatedGreeting(name: String = \"Reader\"): String"));
            assertThat(schema, containsString("book: Book"));
            assertThat(schema, containsString("titleByIsbn(isbn: ISBN): String"));
            assertThat(schema, containsString("contextAvailable: Boolean!"));
            assertThat(schema, containsString("type Mutation"));
            assertThat(schema, containsString("update(enabled: Boolean!): Boolean!"));
            assertThat(schema, containsString("scalar ISBN"));
            assertThat(schema, containsString("\"Book result\""));
            assertThat(schema, containsString("title: String!"));
            assertThat(schema, containsString("state: BookStatus"));
            assertThat(schema, containsString("isbn: ISBN"));
            assertThat(schema, containsString("summary(prefix: String): String"));
            assertThat(schema, containsString("enum BookStatus"));
            assertThat(schema, containsString("\"Currently available\""));
            assertThat(schema, containsString("OUT_OF_PRINT"));
        }
    }

    @Test
    void testQueryAndObjectResult() {
        JsonObject data = graphQl("""
                                          {
                                            "query": "query($isbn: ISBN!) { hello(name: \\"Helidon\\") catalogName validatedGreeting contextAvailable titleByIsbn(isbn: $isbn) literalTitle: titleByIsbn(isbn: \\"9780441172719\\") book { title state isbn summary(prefix: \\"Read\\") } }",
                                            "variables": { "isbn": "9780441172719" }
                                          }
                                          """);

        assertThat(data.stringValue("hello").orElseThrow(), is("Hello Helidon"));
        assertThat(data.stringValue("catalogName").orElseThrow(), is("Arrakeen Library"));
        assertThat(data.stringValue("validatedGreeting").orElseThrow(), is("Validated Reader"));
        assertThat(data.booleanValue("contextAvailable").orElseThrow(), is(true));
        assertThat(data.stringValue("titleByIsbn").orElseThrow(), is("Dune: 9780441172719"));
        assertThat(data.stringValue("literalTitle").orElseThrow(), is("Dune: 9780441172719"));
        JsonObject book = data.objectValue("book").orElseThrow();
        assertThat(book.stringValue("title").orElseThrow(), is("Dune"));
        assertThat(book.stringValue("state").orElseThrow(), is("AVAILABLE"));
        assertThat(book.stringValue("isbn").orElseThrow(), is("9780441172719"));
        assertThat(book.stringValue("summary").orElseThrow(), is("Read: Dune"));
        assertThat(book.value("internal").isEmpty(), is(true));
    }

    @Test
    void testMutation() {
        JsonObject mutationData = graphQl("""
                                                  {
                                                    "query": "mutation { update(enabled: true) }"
                                                  }
                                                  """);

        assertThat(mutationData.booleanValue("update").orElseThrow(), is(true));

        JsonObject queryData = graphQl("""
                                               {
                                                 "query": "{ enabled }"
                                               }
                                               """);
        assertThat(queryData.booleanValue("enabled").orElseThrow(), is(true));
    }

    @Test
    void testResolverValidationFailure() {
        JsonObject json = graphQlResponse("""
                                                  {
                                                    "query": "{ hello(name: \\"Helidon\\") validatedGreeting(name: \\"\\") }"
                                                  }
                                                  """);

        JsonObject data = json.objectValue("data").orElseThrow();
        assertThat(data.stringValue("hello").orElseThrow(), is("Hello Helidon"));
        assertThat(data.value("validatedGreeting").orElseThrow().type(), is(JsonValueType.NULL));
        String errors = json.arrayValue("errors").orElseThrow().toString();
        assertThat(errors, containsString("is blank"));
        assertThat(errors, containsString("validatedGreeting"));
    }

    private JsonObject graphQl(String request) {
        JsonObject json = graphQlResponse(request);
        assertThat("GraphQL errors: " + json.value("errors"), json.value("errors").isEmpty(), is(true));
        return json.objectValue("data").orElseThrow();
    }

    private JsonObject graphQlResponse(String request) {
        try (Http1ClientResponse response = client.post("/graphql")
                .submit(request)) {
            assertThat(response.status(), is(Status.OK_200));
            return response.as(JsonObject.class);
        }
    }
}
