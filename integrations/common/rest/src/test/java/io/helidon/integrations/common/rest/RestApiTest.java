/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.common.rest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.PUT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

@ServerTest
class RestApiTest {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    private static RestApi restApi;

    @SetUpServer
    static void setupServer(WebServerConfig.Builder serverBuilder) {
        serverBuilder.routing(routing -> routing.register("/api", TestApiService::new))
                .mediaContext(MediaContext.builder().addMediaSupport(JsonpSupport.create()).build());
    }

    @BeforeAll
    static void setupRestApi(WebServer webServer) {
        restApi = RestApiImpl.create(webServer.port());
    }

    @Test
    void testQueryParam() {
        RestRequest request = RestRequest.builder().addQueryParam("query", "queryValue");
        EchoResponse response = restApi.invokeWithResponse(GET, "/echo", request, EchoResponse.builder());

        assertThat(response.echoedPath, is("/api/echo"));
        assertThat(response.echoedQueryParams, is(Map.of("query", "queryValue")));
        assertThat(response.echoedEntity, is(Optional.empty()));
    }

    @Test
    void testHeaders() {
        RestRequest request = RestRequest.builder().addHeader("header", "headerValue");
        EchoResponse response = restApi.invokeWithResponse(GET, "/echo", request, EchoResponse.builder());

        assertThat(response.echoedPath, is("/api/echo"));
        assertThat(response.echoedQueryParams, is(Map.of()));
        assertThat(response.echoedHeaders, hasEntry("header", "headerValue"));
        assertThat(response.echoedEntity, is(Optional.empty()));
    }

    @Test
    void testEntity() {
        JsonRequest request = JsonRequest.builder()
                .add("anInt", 42)
                .add("aString", "value");
        EchoResponse response = restApi.invokeWithResponse(PUT, "/echo", request, EchoResponse.builder());

        assertThat(response.echoedPath, is("/api/echo"));
        assertThat(response.echoedQueryParams, is(Map.of()));
        assertThat(response.echoedEntity, not(Optional.empty()));
        assertThat(response.echoedEntity.isPresent(), is(true));
        JsonObject jsonObject = response.echoedEntity.get();

        assertThat(jsonObject.getInt("anInt"), is(42));
        assertThat(jsonObject.getString("aString"), is("value"));

    }

    @Test
    void testGetFound() {
        var responseBuilder = ApiOptionalResponse.<JsonObject, JsonObject>apiResponseBuilder()
                .entityProcessor(Function.identity());
        var response = restApi.get("/echo", RestRequest.builder(), responseBuilder);

        assertThat(response.entity(), not(Optional.empty()));
        assertThat(response.status(), is(Status.OK_200));
    }

    @Test
    void testGetNotFound() {
        var responseBuilder = ApiOptionalResponse.<JsonObject, JsonObject>apiResponseBuilder()
                .entityProcessor(Function.identity());
        var response = restApi.get("/missing", RestRequest.builder(), responseBuilder);

        assertThat(response.entity(), is(Optional.empty()));
        assertThat(response.status(), is(Status.NOT_FOUND_404));
    }

    private static class TestApiService implements HttpService {

        @Override
        public void routing(HttpRules rules) {
            rules.any("/echo", (req, res) -> {
                switch (req.prologue().method().text()) {
                case "GET", "DELETE", "HEAD" -> this.echoNoEntity(req, res);
                case "PUT", "POST" -> this.echo(req, res, req.content().as(JsonObject.class));
                }
            });
        }

        private void echo(ServerRequest req, ServerResponse res, JsonObject entity) {
            res.send(common(req).add("entity", entity).build());
        }

        private void echoNoEntity(ServerRequest req, ServerResponse res) {
            res.send(common(req).build());
        }

        private JsonObjectBuilder common(ServerRequest req) {
            JsonObjectBuilder objectBuilder = JSON.createObjectBuilder();
            objectBuilder.add("path", req.path().absolute().rawPath());

            Map<String, List<String>> queryParams = req.query().toMap();
            if (!queryParams.isEmpty()) {
                JsonObjectBuilder queryParamsBuilder = JSON.createObjectBuilder();
                queryParams.forEach((key, values) -> queryParamsBuilder.add(key, values.iterator().next()));
                objectBuilder.add("params", queryParamsBuilder);
            }

            ServerRequestHeaders headers = req.headers();
            if (headers.size() > 0) {
                JsonObjectBuilder headersBuilder = JSON.createObjectBuilder();
                headers.forEach(header -> headersBuilder.add(header.name(), header.get()));
                objectBuilder.add("headers", headersBuilder);
            }

            return objectBuilder;
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static class EchoResponse extends ApiEntityResponse {
        private final String echoedPath;
        private final Map<String, String> echoedQueryParams;
        private final Map<String, String> echoedHeaders;
        private final Optional<JsonObject> echoedEntity;

        private EchoResponse(Builder builder) {
            super(builder);
            JsonObject json = builder.entity();
            this.echoedPath = json.getString("path");
            this.echoedQueryParams = toMap(json, "params");
            this.echoedHeaders = toMap(json, "headers");
            this.echoedEntity = toObject(json, "entity");
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder extends ApiEntityResponse.Builder<Builder, EchoResponse, JsonObject> {
            @Override
            public EchoResponse build() {
                return new EchoResponse(this);
            }
        }
    }
}