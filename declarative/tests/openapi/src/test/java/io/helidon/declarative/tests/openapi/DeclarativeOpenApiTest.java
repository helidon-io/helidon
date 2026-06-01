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

package io.helidon.declarative.tests.openapi;

import java.util.List;
import java.util.Map;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

@ServerTest
class DeclarativeOpenApiTest {
    private final Http1Client client;

    DeclarativeOpenApiTest(Http1Client client) {
        this.client = client;
    }

    @Test
    void generatedDocumentUsesEndpointNamesForTagsAndOperationIds() {
        Map<String, Object> document = document();

        assertThat(document.get("openapi"), is("3.0.3"));
        assertThat(object(document, "info").get("title"), is("Declarative OpenAPI Test"));
        assertThat(object(document, "info").get("version"), is("1.0.0"));

        Map<String, Object> greetingFind = operation(document, "/greetings/{name}", "get");
        assertThat(greetingFind.get("operationId"), is("greetingGetFind"));
        assertThat(list(greetingFind, "tags"), contains("greeting"));

        Map<String, Object> farewellFind = operation(document, "/farewells/{name}", "get");
        assertThat(farewellFind.get("operationId"), is("farewellGetFind"));
        assertThat(list(farewellFind, "tags"), contains("farewell"));

        Map<String, Object> greetingCreate = operation(document, "/greetings", "post");
        assertThat(greetingCreate.get("operationId"), is("greetingPostCreate"));
        assertThat(list(greetingCreate, "tags"), contains("greeting"));

        Map<String, Object> farewellCreate = operation(document, "/farewells", "post");
        assertThat(farewellCreate.get("operationId"), is("farewellPostCreate"));
        assertThat(list(farewellCreate, "tags"), contains("farewell"));
    }

    @Test
    void generatedDocumentIncludesRoundTripAlignmentEdges() {
        Map<String, Object> document = document();

        Map<String, Object> noContent = operation(document, "/greetings/{name}", "delete");
        assertThat(noContent.get("operationId"), is("greetingDeleteRemove"));
        Map<String, Object> removeResponse = response(noContent, "204");
        assertThat(removeResponse.get("description"), is("No Content"));
        assertThat(removeResponse, not(hasKey("content")));

        Map<String, Object> plain = operation(document, "/farewells/plain", "post");
        assertThat(plain.get("operationId"), is("farewellPostCreatePlain"));
        Map<String, Object> plainBody = object(plain, "requestBody");
        assertThat(object(content(plainBody, MediaTypes.TEXT_PLAIN_VALUE)).get("type"), is("string"));
        Map<String, Object> plainResponse = response(plain, "200");
        assertThat(plainResponse.get("description"), is("OK"));
        assertThat(object(content(plainResponse, MediaTypes.TEXT_PLAIN_VALUE)).get("type"), is("string"));
    }

    @Test
    void generatedDocumentIncludesApplicationMetadata() {
        Map<String, Object> document = document();
        Map<String, Object> info = object(document, "info");

        Map<String, Object> contact = object(info, "contact");
        assertThat(contact.get("name"), is("Helidon Team"));
        assertThat(contact.get("url"), is("https://helidon.io"));
        assertThat(contact.get("email"), is("helidon@example.com"));

        Map<String, Object> license = object(info, "license");
        assertThat(license.get("name"), is("Apache License 2.0"));
        assertThat(license.get("identifier"), is("Apache-2.0"));
        assertThat(license.get("url"), is("https://www.apache.org/licenses/LICENSE-2.0"));

        Map<String, Object> externalDocs = object(document, "externalDocs");
        assertThat(externalDocs.get("url"), is("https://helidon.io/docs"));
        assertThat(externalDocs.get("description"), is("Helidon documentation"));
        assertThat(document.get("x-test-document"), is("declarative-openapi"));

        Map<String, Object> securitySchemes = object(object(document, "components"), "securitySchemes");
        Map<String, Object> bearerAuth = object(securitySchemes, "bearerAuth");
        assertThat(bearerAuth.get("type"), is("http"));
        assertThat(bearerAuth.get("description"), is("Bearer token authentication"));
        assertThat(bearerAuth.get("scheme"), is("bearer"));
        assertThat(bearerAuth.get("bearerFormat"), is("JWT"));

        Map<String, Object> oauth2 = object(securitySchemes, "oauth2");
        assertThat(oauth2.get("type"), is("oauth2"));
        Map<String, Object> clientCredentials = object(object(oauth2, "flows"), "clientCredentials");
        assertThat(clientCredentials.get("tokenUrl"), is("https://id.example.com/oauth2/token"));
        assertThat(object(clientCredentials, "scopes").get("greeting:read"), is("Read greetings"));

        List<Object> security = list(document, "security");
        assertThat(list(object(security.getFirst()), "bearerAuth"), is(List.of()));
        assertThat(list(object(security.get(1)), "oauth2"), contains("greeting:read"));
    }

    @Test
    void generatedDocumentInfersMinimalOperationShapeFromDeclarativeHttp() {
        Map<String, Object> document = document();
        Map<String, Object> operation = operation(document, "/greetings/{name}", "get");

        assertThat(list(operation, "tags"), contains("greeting"));
        assertThat(operation, not(hasKey("security")));

        Map<String, Object> name = parameter(operation, "name", "path");
        assertThat(name.get("required"), is(true));
        assertThat(object(name, "schema").get("type"), is("string"));

        Map<String, Object> language = parameter(operation, "language", "query");
        assertThat(language.get("required"), is(false));
        assertThat(object(language, "schema").get("type"), is("string"));

        Map<String, Object> include = parameter(operation, "include", "query");
        assertThat(include.get("required"), is(true));
        assertThat(include.get("style"), is("form"));
        assertThat(include.get("explode"), is(true));
        assertThat(object(object(include, "schema"), "items").get("type"), is("string"));

        Map<String, Object> response = response(operation, "200");
        assertThat(response.get("description"), is("OK"));
        assertThat(ref(content(response, MediaTypes.APPLICATION_JSON_VALUE)), is("#/components/schemas/Message"));
    }

    @Test
    void generatedDocumentUsesExplicitAnnotationsWhenPresent() {
        Map<String, Object> operation = operation(document(), "/greetings/documented/{name}", "get");

        assertThat(operation.get("summary"), is("Find a greeting"));
        assertThat(operation.get("description"), is("Returns a documented greeting."));
        assertThat(operation.get("operationId"), is("findDocumentedGreeting"));
        assertThat(list(operation, "tags"), contains("greeting", "documented"));
        assertThat(operation.get("deprecated"), is(true));

        Map<String, Object> parameter = parameter(operation, "name", "path");
        assertThat(parameter.get("description"), is("Greeting recipient"));
        assertThat(parameter.get("example"), is("Tomas"));

        Map<String, Object> response = response(operation, "200");
        assertThat(response.get("description"), is("Greeting found"));
        assertThat(ref(content(response, MediaTypes.APPLICATION_JSON_VALUE)), is("#/components/schemas/Message"));
    }

    @Test
    void generatedDocumentIncludesRichResponseMetadata() {
        Map<String, Object> operation = operation(document(), "/greetings/responses", "get");

        Map<String, Object> response = response(operation, "202");
        assertThat(response.get("description"), is("Accepted greeting"));
        assertThat(ref(content(response, "application/vnd.greeting+json")), is("#/components/schemas/Message"));
        Map<String, Object> responseExample = example(mediaTypeObject(response, "application/vnd.greeting+json"),
                                                      "accepted-response");
        assertThat(responseExample.get("summary"), is("Accepted example"));
        assertThat(responseExample.get("value"), is("{\"message\":\"Accepted\"}"));

        Map<String, Object> headers = object(response, "headers");
        Map<String, Object> staticHeader = object(headers, "X-Static");
        assertThat(staticHeader.get("required"), is(true));
        Map<String, Object> staticHeaderSchema = object(staticHeader, "schema");
        assertThat(staticHeaderSchema.get("type"), is("string"));
        assertThat(staticHeaderSchema.get("default"), is("static"));
        Map<String, Object> computedHeader = object(headers, "X-Computed");
        assertThat(computedHeader, not(hasKey("required")));
        Map<String, Object> computedHeaderSchema = object(computedHeader, "schema");
        assertThat(computedHeaderSchema.get("type"), is("string"));
        assertThat(computedHeaderSchema, not(hasKey("default")));
        Map<String, Object> documentedHeader = object(headers, "X-Documented");
        assertThat(documentedHeader.get("description"), is("Documented response header"));
        assertThat(documentedHeader.get("required"), is(true));
        assertThat(documentedHeader.get("deprecated"), is(true));
        assertThat(object(documentedHeader, "schema").get("type"), is("string"));
    }

    @Test
    void generatedDocumentMergesExplicitParameterMetadata() {
        Map<String, Object> operation = operation(document(), "/greetings/parameters/{id}", "get");

        Map<String, Object> id = parameter(operation, "id", "path");
        assertThat(id.get("required"), is(true));
        assertThat(id.get("description"), is("Greeting identifier"));
        assertThat(id.get("example"), is("42"));

        Map<String, Object> search = parameter(operation, "search", "query");
        assertThat(search.get("required"), is(false));
        assertThat(search.get("description"), is("Search text"));
        assertThat(search.get("style"), is("form"));
        assertThat(search.get("explode"), is(false));
        assertThat(search.get("allowReserved"), is(true));
        assertThat(search.get("deprecated"), is(true));
        assertThat(search.get("example"), is("hello/world"));
        Map<String, Object> searchExample = example(search, "search-example");
        assertThat(searchExample.get("summary"), is("Search example"));
        assertThat(searchExample.get("value"), is("hi"));

        Map<String, Object> filter = parameter(operation, "filter", "query");
        assertThat(filter.get("style"), is("pipeDelimited"));
        assertThat(filter.get("explode"), is(false));
        assertThat(object(object(filter, "schema"), "items").get("type"), is("string"));

        Map<String, Object> packed = parameter(operation, "packed", "query");
        assertThat(packed.get("description"), is("Packed JSON filter"));
        assertThat(packed, not(hasKey("schema")));
        assertThat(ref(content(packed, MediaTypes.APPLICATION_JSON_VALUE)), is("#/components/schemas/MessageRequest"));
        assertThat(example(mediaTypeObject(packed, MediaTypes.APPLICATION_JSON_VALUE), "packed-example").get("value"),
                   is("{\"prefix\":\"Hello\",\"name\":\"Ada\"}"));

        Map<String, Object> trace = parameter(operation, "X-Trace", "header");
        assertThat(trace.get("required"), is(false));
        assertThat(trace.get("description"), is("Trace header"));
        assertThat(trace.get("style"), is("simple"));
        assertThat(trace.get("explode"), is(false));
        assertThat(example(trace, "trace-example").get("value"), is("abc-123"));

        Map<String, Object> modes = parameter(operation, "X-Modes", "header");
        assertThat(modes.get("required"), is(true));
        assertThat(modes.get("style"), is("simple"));
        assertThat(modes.get("explode"), is(false));
        assertThat(object(object(modes, "schema"), "items").get("type"), is("string"));
    }

    @Test
    void generatedDocumentIncludesOperationLevelMetadata() {
        Map<String, Object> document = document();
        Map<String, Object> operation = operation(document, "/greetings/documented/{name}", "get");

        Map<String, Object> server = object(list(operation, "servers").getFirst());
        assertThat(server.get("url"), is("https://api.example.com/greetings"));
        assertThat(server.get("description"), is("Operation server"));

        Map<String, Object> externalDocs = object(operation, "externalDocs");
        assertThat(externalDocs.get("url"), is("https://helidon.io/docs/openapi"));
        assertThat(externalDocs.get("description"), is("Operation documentation"));
        assertThat(operation.get("x-test-operation"), is("documented-greeting"));

        List<Object> security = list(operation, "security");
        Map<String, Object> allRequired = object(security.getFirst());
        assertThat(list(allRequired, "bearerAuth"), is(List.of()));
        assertThat(list(allRequired, "oauth2"), is(List.of()));
        assertThat(list(object(security.get(1)), "oauth2"), contains("greeting:read"));
    }

    @Test
    void generatedDocumentCanClearOperationSecurity() {
        Map<String, Object> operation = operation(document(), "/greetings/public", "get");

        assertThat(list(operation, "security"), is(List.of()));
    }

    @Test
    void generatedDocumentInfersRequestBodyStatusAndOptionalResponses() {
        Map<String, Object> document = document();

        Map<String, Object> create = operation(document, "/greetings", "post");
        assertThat(create.get("operationId"), is("greetingPostCreate"));
        Map<String, Object> createBody = object(create, "requestBody");
        assertThat(createBody.get("description"), is("Greeting payload"));
        assertThat(createBody.get("required"), is(true));
        assertThat(ref(content(createBody, MediaTypes.APPLICATION_JSON_VALUE)), is("#/components/schemas/MessageRequest"));
        assertThat(example(mediaTypeObject(createBody, MediaTypes.APPLICATION_JSON_VALUE), "create-request").get("value"),
                   is("{\"prefix\":\"Hello\",\"name\":\"Ada\"}"));
        assertThat(response(create, "201").get("description"), is("Created"));
        assertThat(ref(content(response(create, "201"), MediaTypes.APPLICATION_JSON_VALUE)),
                   is("#/components/schemas/Message"));

        Map<String, Object> inferredBody = object(operation(document, "/greetings/inferred-body", "put"), "requestBody");
        assertThat(inferredBody.get("description"), is("Inferred greeting payload"));
        assertThat(inferredBody.get("required"), is(false));
        assertThat(ref(content(inferredBody, MediaTypes.APPLICATION_JSON_VALUE)), is("#/components/schemas/MessageRequest"));

        Map<String, Object> optional = operation(document, "/greetings/optional/{name}", "get");
        assertThat(optional.get("operationId"), is("greetingGetMaybeFind"));
        Map<String, Object> optionalFound = response(optional, "200");
        assertThat(optionalFound.get("description"), is("OK"));
        assertThat(ref(content(optionalFound, MediaTypes.APPLICATION_JSON_VALUE)), is("#/components/schemas/Message"));
        Map<String, Object> optionalMissing = response(optional, "404");
        assertThat(optionalMissing.get("description"), is("Not Found"));
        assertThat(optionalMissing, not(hasKey("content")));
    }

    @Test
    void generatedDocumentUsesJsonSchemaComponentsForEntityTypes() {
        Map<String, Object> schemas = object(object(document(), "components"), "schemas");

        Map<String, Object> message = object(schemas, "Message");
        assertThat(message.get("type"), is("object"));
        assertThat(message.get("description"), is("Message response entity"));
        assertThat(list(message, "required"), contains("message"));
        Map<String, Object> messageText = object(object(message, "properties"), "message");
        assertThat(messageText.get("type"), is("string"));
        assertThat(messageText.get("description"), is("Message text"));

        Map<String, Object> request = object(schemas, "MessageRequest");
        assertThat(request.get("type"), is("object"));
        assertThat(request.get("description"), is("Message request entity"));
        assertThat(list(request, "required"), contains("prefix", "name"));
        Map<String, Object> prefix = object(object(request, "properties"), "prefix");
        assertThat(prefix.get("type"), is("string"));
        assertThat(prefix.get("description"), is("Greeting prefix"));
        Map<String, Object> name = object(object(request, "properties"), "name");
        assertThat(name.get("type"), is("string"));
        assertThat(name.get("description"), is("Recipient name"));
    }

    @Test
    void generatedDocumentExcludesHiddenOperations() {
        assertThat(object(document(), "paths"), not(hasKey("/greetings/internal")));
    }

    @Test
    void staticDocumentIsMergedWithGeneratedDocument() {
        Map<String, Object> document = document();

        assertThat(operation(document, "/static/status", "get").get("operationId"), is("staticGetStatus"));
        assertThat(operation(document, "/greetings/{name}", "get").get("operationId"), is("greetingGetFind"));
    }

    private Map<String, Object> document() {
        try (Http1ClientResponse response = client.get("/openapi")
                .accept(MediaTypes.APPLICATION_OPENAPI_YAML)
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            return object(new Yaml().load(response.as(String.class)));
        }
    }

    private static Map<String, Object> operation(Map<String, Object> document, String path, String method) {
        return object(object(object(document, "paths"), path), method);
    }

    private static Map<String, Object> response(Map<String, Object> operation, String status) {
        return object(object(operation, "responses"), status);
    }

    private static Map<String, Object> content(Map<String, Object> owner, String mediaType) {
        return object(mediaTypeObject(owner, mediaType), "schema");
    }

    private static Map<String, Object> mediaTypeObject(Map<String, Object> owner, String mediaType) {
        return object(object(owner, "content"), mediaType);
    }

    private static Map<String, Object> example(Map<String, Object> owner, String name) {
        return object(object(owner, "examples"), name);
    }

    private static String ref(Map<String, Object> schema) {
        return (String) schema.get("$ref");
    }

    private static Map<String, Object> parameter(Map<String, Object> operation, String name, String in) {
        for (Object parameter : list(operation, "parameters")) {
            Map<String, Object> parameterObject = object(parameter);
            if (name.equals(parameterObject.get("name")) && in.equals(parameterObject.get("in"))) {
                return parameterObject;
            }
        }
        throw new AssertionError("Parameter not found: " + in + " " + name);
    }

    private static Map<String, Object> object(Map<String, Object> owner, String key) {
        return object(owner.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        assertThat(value, instanceOf(Map.class));
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> owner, String key) {
        Object value = owner.get(key);
        assertThat(value, instanceOf(List.class));
        return (List<Object>) value;
    }
}
