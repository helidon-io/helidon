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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

@Disabled("Enable after declarative OpenAPI code generation is implemented.")
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
        assertThat(greetingFind.get("operationId"), is("greeting.get.find"));
        assertThat(list(greetingFind, "tags"), contains("greeting"));

        Map<String, Object> farewellFind = operation(document, "/farewells/{name}", "get");
        assertThat(farewellFind.get("operationId"), is("farewell.get.find"));
        assertThat(list(farewellFind, "tags"), contains("farewell"));
    }

    @Test
    void generatedDocumentInfersMinimalOperationShapeFromDeclarativeHttp() {
        Map<String, Object> document = document();
        Map<String, Object> operation = operation(document, "/greetings/{name}", "get");

        assertThat(list(operation, "tags"), contains("greeting"));

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
        assertThat(operation.get("operationId"), is("greeting.get.documented"));

        Map<String, Object> parameter = parameter(operation, "name", "path");
        assertThat(parameter.get("description"), is("Greeting recipient"));
        assertThat(parameter.get("example"), is("Tomas"));

        Map<String, Object> response = response(operation, "200");
        assertThat(response.get("description"), is("Greeting found"));
        assertThat(ref(content(response, MediaTypes.APPLICATION_JSON_VALUE)), is("#/components/schemas/Message"));
    }

    @Test
    void generatedDocumentInfersRequestBodyStatusAndOptionalResponses() {
        Map<String, Object> document = document();

        Map<String, Object> create = operation(document, "/greetings", "post");
        assertThat(create.get("operationId"), is("greeting.post.create"));
        assertThat(ref(content(object(create, "requestBody"), MediaTypes.APPLICATION_JSON_VALUE)),
                   is("#/components/schemas/MessageRequest"));
        assertThat(response(create, "201").get("description"), is("Created"));
        assertThat(ref(content(response(create, "201"), MediaTypes.APPLICATION_JSON_VALUE)),
                   is("#/components/schemas/Message"));

        Map<String, Object> optional = operation(document, "/greetings/optional/{name}", "get");
        assertThat(optional.get("operationId"), is("greeting.get.maybeFind"));
        assertThat(response(optional, "200").get("description"), is("OK"));
        assertThat(response(optional, "404").get("description"), is("Not Found"));
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

        assertThat(operation(document, "/static/status", "get").get("operationId"), is("static.get.status"));
        assertThat(operation(document, "/greetings/{name}", "get").get("operationId"), is("greeting.get.find"));
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
        return object(object(object(owner, "content"), mediaType), "schema");
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
