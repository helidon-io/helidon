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
            assertThat(schema, containsString("book: Book"));
            assertThat(schema, containsString("type Mutation"));
            assertThat(schema, containsString("update(enabled: Boolean!): Boolean!"));
            assertThat(schema, containsString("\"Book result\""));
            assertThat(schema, containsString("title: String!"));
            assertThat(schema, containsString("state: BookStatus"));
            assertThat(schema, containsString("enum BookStatus"));
            assertThat(schema, containsString("\"Currently available\""));
            assertThat(schema, containsString("OUT_OF_PRINT"));
        }
    }

    @Test
    void testQueryAndObjectResult() {
        JsonObject data = graphQl("""
                                          {
                                            "query": "{ hello(name: \\"Helidon\\") book { title state } }"
                                          }
                                          """);

        assertThat(data.stringValue("hello").orElseThrow(), is("Hello Helidon"));
        JsonObject book = data.objectValue("book").orElseThrow();
        assertThat(book.stringValue("title").orElseThrow(), is("Dune"));
        assertThat(book.stringValue("state").orElseThrow(), is("AVAILABLE"));
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

    private JsonObject graphQl(String request) {
        try (Http1ClientResponse response = client.post("/graphql")
                .submit(request)) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat("GraphQL errors: " + json.value("errors"), json.value("errors").isEmpty(), is(true));
            return json.objectValue("data").orElseThrow();
        }
    }
}
