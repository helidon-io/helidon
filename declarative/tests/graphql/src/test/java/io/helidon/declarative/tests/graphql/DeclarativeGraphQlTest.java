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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValueType;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.testing.junit5.ServerTest;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@SuppressWarnings("helidon:api:preview")
@ServerTest
class DeclarativeGraphQlTest {
    private final Http1Client client;
    private final TestSpanExporter spanExporter;

    DeclarativeGraphQlTest(Http1Client client, TestTracerFactory tracerFactory) {
        this.client = client;
        this.spanExporter = tracerFactory.exporter();
    }

    @BeforeEach
    void beforeEach() {
        GraphQlEntryPointRecorder.reset();
    }

    @Test
    void testGeneratedSchemaEndpoint() {
        try (Http1ClientResponse response = client.get("/graphql/schema.graphql")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            String schema = response.as(String.class);

            assertThat(schema, containsString("type Query"));
            assertThat(schema, containsString("hello(name: String): String"));
            assertThat(schema, containsString("tracedHello(name: String): String"));
            assertThat(schema, containsString("catalogName: String"));
            assertThat(schema, containsString("validatedGreeting(name: String = \"Reader\"): String"));
            assertThat(schema, containsString("book: Book"));
            assertThat(schema, containsString("recommendedBooks: [Book]"));
            assertThat(schema, containsString("titleByIsbn(isbn: ISBN): String"));
            assertThat(schema, containsString("filteredTitle(search: BookSearchInput!): String"));
            assertThat(schema, containsString("statusName(status: BookStatus): String"));
            assertThat(schema, containsString("renamedStatus: BookStatus"));
            assertThat(schema, containsString("statusNames(statuses: [BookStatus]): String"));
            assertThat(schema, containsString("isbnValues(isbns: [ISBN]): String"));
            assertThat(schema, containsString("structured(value: STRUCTURED): String"));
            assertThat(schema, containsString("contextAvailable: Boolean!"));
            assertThat(schema, containsString("securedMessage: String"));
            assertThat(schema, containsString("type Mutation"));
            assertThat(schema, containsString("update(enabled: Boolean!): Boolean!"));
            assertThat(schema, containsString("scalar ISBN"));
            assertThat(schema, containsString("scalar STRUCTURED"));
            assertThat(schema, containsString("\"Book result\""));
            assertThat(schema, containsString("title: String!"));
            assertThat(schema, containsString("state: BookStatus"));
            assertThat(schema, containsString("isbn: ISBN"));
            assertThat(schema, containsString("tags: [String]"));
            assertThat(schema, containsString("relatedIsbns: [ISBN]"));
            assertThat(schema, containsString("summary(prefix: String, tags: [String]): String"));
            assertThat(schema, containsString("enum BookStatus"));
            assertThat(schema, containsString("\"Currently available\""));
            assertThat(schema, containsString("OUT_OF_PRINT"));
            assertThat(schema, containsString("input BookSearchInput"));
            assertThat(schema, containsString("phrase: String!"));
            assertThat(schema, containsString("minimumScore: Int!"));
            assertThat(schema, containsString("includeUnavailable: Boolean!"));
            assertThat(schema, containsString("status: BookStatus!"));
            assertThat(schema, containsString("tags: [String]"));
            assertThat(schema, containsString("statuses: [BookStatus]"));
            assertThat(schema, containsString("isbns: [ISBN]"));
            assertThat(schema, containsString("filters: [BookFilterInput]"));
            assertThat(schema, containsString("input BookFilterInput"));
            assertThat(schema, containsString("field: String!"));
            assertThat(schema, containsString("value: String!"));
        }
    }

    @Test
    void testGeneratedHttpEntryPointInterception() {
        try (Http1ClientResponse response = client.get("/graphql/schema.graphql")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        graphQl("""
                        {
                          "query": "{ hello(name: \\"Helidon\\") }"
                        }
                        """);

        try (Http1ClientResponse response = client.get("/graphql")
                .queryParam("query", "{ catalogName }")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(GraphQlEntryPointRecorder.executions(),
                   hasItems(CatalogEndpoint.class.getName() + ".graphQlSchema()",
                            CatalogEndpoint.class.getName() + ".graphQlPost()",
                            CatalogEndpoint.class.getName() + ".graphQlGet()"));
    }

    @Test
    void testResolverMetrics() {
        int before = metricCount("graphql-metered-hello");

        JsonObject data = graphQl("""
                                          {
                                            "query": "{ hello(name: \\"Metrics\\") }"
                                          }
                                          """);

        assertThat(data.stringValue("hello").orElseThrow(), is("Hello Metrics"));
        assertThat(metricCount("graphql-metered-hello"), is(before + 1));
    }

    private int metricCount(String name) {
        try (Http1ClientResponse response = client.get("/observe/metrics")
                .header(HeaderValues.ACCEPT_JSON)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            return response.as(JsonObject.class)
                    .objectValue("application")
                    .flatMap(it -> it.numberValue(name))
                    .map(BigDecimal::intValue)
                    .orElse(0);
        }
    }

    @Test
    void testResolverTracing() {
        spanExporter.clear();

        JsonObject data = graphQl("""
                                          {
                                            "query": "{ tracedHello(name: \\"Trace\\") }"
                                          }
                                          """);

        assertThat(data.stringValue("tracedHello").orElseThrow(), is("Traced Trace"));

        var spans = spanExporter.spanData("graphql-traced-hello");
        spanExporter.clear();
        SpanData httpRequest = null;
        SpanData contentWrite = null;
        SpanData tracedResolver = null;

        for (SpanData span : spans) {
            switch (span.getName()) {
            case "HTTP Request":
                httpRequest = span;
                break;
            case "content-write":
                contentWrite = span;
                break;
            case "graphql-traced-hello":
                tracedResolver = span;
                break;
            default:
                break;
            }
        }

        Set<String> names = spans.stream()
                .map(SpanData::getName)
                .collect(Collectors.toSet());
        assertThat("Found names: " + names + ", missing HTTP request span", httpRequest, notNullValue());
        assertThat("Found names: " + names + ", missing content write span", contentWrite, notNullValue());
        assertThat("Found names: " + names + ", missing GraphQL resolver span", tracedResolver, notNullValue());

        String traceId = httpRequest.getTraceId();
        String parentSpanId = httpRequest.getSpanId();
        assertThat(contentWrite.getTraceId(), is(traceId));
        assertThat(contentWrite.getParentSpanId(), is(parentSpanId));
        assertThat(tracedResolver.getTraceId(), is(traceId));
        assertThat(tracedResolver.getParentSpanId(), is(parentSpanId));
        assertThat(tracedResolver.getKind(), is(SpanKind.SERVER));
        assertAttribute(tracedResolver.getAttributes(), "name", "Trace");
    }

    @Test
    void testQueryAndObjectResult() {
        JsonObject data = graphQl("""
                                          {
                                            "query": "query($isbn: ISBN!) { hello(name: \\"Helidon\\") catalogName validatedGreeting contextAvailable titleByIsbn(isbn: $isbn) literalTitle: titleByIsbn(isbn: \\"9780441172719\\") renamedStatus book { title state isbn tags relatedIsbns summary(prefix: \\"Read\\", tags: [\\"classic\\"]) } recommendedBooks { title } }",
                                            "variables": { "isbn": "9780441172719" }
                                          }
                                          """);

        assertThat(data.stringValue("hello").orElseThrow(), is("Hello Helidon"));
        assertThat(data.stringValue("catalogName").orElseThrow(), is("Arrakeen Library"));
        assertThat(data.stringValue("validatedGreeting").orElseThrow(), is("Validated Reader"));
        assertThat(data.booleanValue("contextAvailable").orElseThrow(), is(true));
        assertThat(data.stringValue("titleByIsbn").orElseThrow(), is("Dune: 9780441172719"));
        assertThat(data.stringValue("literalTitle").orElseThrow(), is("Dune: 9780441172719"));
        assertThat(data.stringValue("renamedStatus").orElseThrow(), is("OUT_OF_PRINT"));
        JsonObject book = data.objectValue("book").orElseThrow();
        assertThat(book.stringValue("title").orElseThrow(), is("Dune"));
        assertThat(book.stringValue("state").orElseThrow(), is("OUT_OF_PRINT"));
        assertThat(book.stringValue("isbn").orElseThrow(), is("9780441172719"));
        assertThat(book.arrayValue("tags").orElseThrow().get(0).orElseThrow().asString().value(), is("classic"));
        assertThat(book.arrayValue("tags").orElseThrow().get(1).orElseThrow().asString().value(), is("desert"));
        assertThat(book.arrayValue("relatedIsbns").orElseThrow().get(0).orElseThrow().asString().value(),
                   is("9780441172720"));
        assertThat(book.arrayValue("relatedIsbns").orElseThrow().get(1).orElseThrow().asString().value(),
                   is("9780441172721"));
        assertThat(book.stringValue("summary").orElseThrow(), is("Read: Dune: [classic]"));
        assertThat(book.value("internal").isEmpty(), is(true));
        assertThat(data.arrayValue("recommendedBooks").orElseThrow()
                           .get(0)
                           .orElseThrow()
                           .asObject()
                           .stringValue("title")
                           .orElseThrow(), is("Dune"));
    }

    @Test
    void testInputObjectArgument() {
        JsonObject data = graphQl("""
                                          {
                                            "query": "query($search: BookSearchInput!, $status: BookStatus!, $statuses: [BookStatus], $isbns: [ISBN]) { filteredTitle(search: $search) statusName(status: $status) statusNames(statuses: $statuses) isbnValues(isbns: $isbns) literalStatus: statusName(status: OUT_OF_PRINT) literalStatuses: statusNames(statuses: [AVAILABLE, OUT_OF_PRINT]) literalIsbns: isbnValues(isbns: [\\"9780441172719\\"]) }",
                                            "variables": {
                                              "status": "OUT_OF_PRINT",
                                              "statuses": ["AVAILABLE", "OUT_OF_PRINT"],
                                              "isbns": ["9780441172719", "9780441172720"],
                                              "search": {
                                                "phrase": "Dune",
                                                "minimumScore": 5,
                                                "includeUnavailable": false,
                                                "status": "OUT_OF_PRINT",
                                                "tags": ["classic", "desert"],
                                                "statuses": ["AVAILABLE", "OUT_OF_PRINT"],
                                                "isbns": ["9780441172719", "9780441172720"],
                                                "filters": [
                                                  {
                                                    "field": "planet",
                                                    "value": "Arrakis"
                                                  }
                                                ]
                                              }
                                            }
                                          }
                                          """);

        assertThat(data.stringValue("filteredTitle").orElseThrow(),
                   is("Dune: 5: false: OUT: [classic, desert]: [AVAILABLE, OUT]: "
                              + "[9780441172719, 9780441172720]: [planet=Arrakis]"));
        assertThat(data.stringValue("statusName").orElseThrow(), is("OUT"));
        assertThat(data.stringValue("statusNames").orElseThrow(), is("[AVAILABLE, OUT]"));
        assertThat(data.stringValue("isbnValues").orElseThrow(), is("[9780441172719, 9780441172720]"));
        assertThat(data.stringValue("literalStatus").orElseThrow(), is("OUT"));
        assertThat(data.stringValue("literalStatuses").orElseThrow(), is("[AVAILABLE, OUT]"));
        assertThat(data.stringValue("literalIsbns").orElseThrow(), is("[9780441172719]"));
    }

    @Test
    void testStructuredScalarLiterals() {
        JsonObject data = graphQl("""
                                          {
                                            "query": "query($value: STRUCTURED) { literalStructured: structured(value: {title: \\"Dune\\", tags: [\\"classic\\", null], details: {score: 5}}) variableStructured: structured(value: $value) nullStructured: structured(value: null) }",
                                            "variables": {
                                              "value": {
                                                "title": "Dune",
                                                "tags": ["classic", null],
                                                "details": {
                                                  "score": 5
                                                }
                                              }
                                            }
                                          }
                                          """);

        String expected = "{details={score=5}, tags=[classic, null], title=Dune}";
        assertThat(data.stringValue("literalStructured").orElseThrow(), is(expected));
        assertThat(data.stringValue("variableStructured").orElseThrow(), is(expected));
        assertThat(data.stringValue("nullStructured").orElseThrow(), is("null"));
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

    @Test
    void testScalarArgumentWrongProviderTypeFailure() {
        JsonObject json = graphQlResponse("""
                                                  {
                                                    "query": "query($isbn: ISBN!) { hello(name: \\"Helidon\\") titleByIsbn(isbn: $isbn) }",
                                                    "variables": {
                                                      "isbn": "wrong-provider-type"
                                                    }
                                                  }
                                                  """);

        JsonObject data = json.objectValue("data").orElseThrow();
        assertThat(data.stringValue("hello").orElseThrow(), is("Hello Helidon"));
        assertThat(data.value("titleByIsbn").orElseThrow().type(), is(JsonValueType.NULL));
        String errors = json.arrayValue("errors").orElseThrow().toString();
        assertThat(errors, containsString("Expected GraphQL ISBN value."));
        assertThat(errors, not(containsString(Isbn.class.getName())));
        assertThat(errors, not(containsString(String.class.getName())));
        assertThat(errors, containsString("titleByIsbn"));
    }

    @Test
    void testRequestLevelSecurityFailure() {
        try (Http1ClientResponse response = client.get("/secured-graphql/schema.graphql")
                .request()) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }

        try (Http1ClientResponse response = client.get("/secured-graphql")
                .queryParam("query", "{ securedRequest }")
                .request()) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }

        try (Http1ClientResponse response = client.post("/secured-graphql")
                .submit("""
                                {
                                  "query": "{ securedRequest }"
                                }
                                """)) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }

        try (Http1ClientResponse response = client.post("/secured-graphql")
                .header(HeaderNames.AUTHORIZATION, basic("jill", "password"))
                .submit("""
                                {
                                  "query": "{ securedRequest }"
                                }
                                """)) {
            assertThat(response.status(), is(Status.FORBIDDEN_403));
        }
    }

    @Test
    void testRequestLevelSecuritySuccess() {
        try (Http1ClientResponse response = client.get("/secured-graphql/schema.graphql")
                .header(HeaderNames.AUTHORIZATION, basic("jack", "jackIsGreat"))
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), containsString("securedRequest: String"));
        }

        try (Http1ClientResponse response = client.get("/secured-graphql")
                .queryParam("query", "{ securedRequest }")
                .header(HeaderNames.AUTHORIZATION, basic("jack", "jackIsGreat"))
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.value("errors").isEmpty(), is(true));
            assertThat(json.objectValue("data").orElseThrow()
                               .stringValue("securedRequest").orElseThrow(), is("Secured request jack"));
        }

        JsonObject data = graphQl("""
                                          {
                                            "query": "{ securedRequest }"
                                          }
                                          """,
                                  "/secured-graphql",
                                  "jack",
                                  "jackIsGreat");
        assertThat(data.stringValue("securedRequest").orElseThrow(), is("Secured request jack"));
    }

    @Test
    void testResolverAuthenticationFailure() {
        JsonObject json = graphQlResponse("""
                                                  {
                                                    "query": "{ hello(name: \\"Helidon\\") securedMessage }"
                                                  }
                                                  """);

        JsonObject data = json.objectValue("data").orElseThrow();
        assertThat(data.stringValue("hello").orElseThrow(), is("Hello Helidon"));
        assertThat(data.value("securedMessage").orElseThrow().type(), is(JsonValueType.NULL));
        String errors = json.arrayValue("errors").orElseThrow().toString();
        assertThat(errors, containsString("Security did not allow this request to proceed"));
        assertThat(errors, containsString("securedMessage"));
    }

    @Test
    void testResolverAuthorizationFailure() {
        JsonObject json = graphQlResponse("""
                                                  {
                                                    "query": "{ hello(name: \\"Helidon\\") securedMessage }"
                                                  }
                                                  """,
                                          "jill",
                                          "password");

        JsonObject data = json.objectValue("data").orElseThrow();
        assertThat(data.stringValue("hello").orElseThrow(), is("Hello Helidon"));
        assertThat(data.value("securedMessage").orElseThrow().type(), is(JsonValueType.NULL));
        String errors = json.arrayValue("errors").orElseThrow().toString();
        assertThat(errors, containsString("Security did not allow this request to proceed"));
        assertThat(errors, containsString("securedMessage"));
    }

    @Test
    void testResolverSecuritySuccess() {
        JsonObject data = graphQl("""
                                          {
                                            "query": "{ hello(name: \\"Helidon\\") securedMessage }"
                                          }
                                          """,
                                  "jack",
                                  "jackIsGreat");

        assertThat(data.stringValue("hello").orElseThrow(), is("Hello Helidon"));
        assertThat(data.stringValue("securedMessage").orElseThrow(), is("Secured jack"));
    }

    private JsonObject graphQl(String request) {
        return graphQl(request, null, null);
    }

    private JsonObject graphQl(String request, String username, String password) {
        return graphQl(request, "/graphql", username, password);
    }

    private JsonObject graphQl(String request, String path, String username, String password) {
        JsonObject json = graphQlResponse(request, path, username, password);
        assertThat("GraphQL errors: " + json.value("errors"), json.value("errors").isEmpty(), is(true));
        return json.objectValue("data").orElseThrow();
    }

    private JsonObject graphQlResponse(String request) {
        return graphQlResponse(request, null, null);
    }

    private JsonObject graphQlResponse(String request, String username, String password) {
        return graphQlResponse(request, "/graphql", username, password);
    }

    private JsonObject graphQlResponse(String request, String path, String username, String password) {
        var requestBuilder = client.post(path);
        if (username != null) {
            requestBuilder.header(HeaderNames.AUTHORIZATION, basic(username, password));
        }
        try (Http1ClientResponse response = requestBuilder.submit(request)) {
            assertThat(response.status(), is(Status.OK_200));
            return response.as(JsonObject.class);
        }
    }

    private static String basic(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertAttribute(Attributes attributes, String key, String value) {
        var found = attributes.asMap().entrySet()
                .stream()
                .filter(entry -> entry.getKey().getKey().equals(key))
                .filter(entry -> entry.getValue().equals(value))
                .findAny();
        assertThat("Expected to find " + key + "=" + value + ", but got: " + attributes,
                   found.isPresent(),
                   is(true));
    }
}
