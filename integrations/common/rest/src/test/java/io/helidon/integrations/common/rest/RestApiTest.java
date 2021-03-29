/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import io.helidon.common.http.Http;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Method.DELETE;
import static io.helidon.common.http.Http.Method.GET;
import static io.helidon.common.http.Http.Method.HEAD;
import static io.helidon.common.http.Http.Method.POST;
import static io.helidon.common.http.Http.Method.PUT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

class RestApiTest {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    private static WebServer webServer;
    private static RestApi restApi;

    @BeforeAll
    static void initClass() {
        webServer = WebServer.builder()
                .routing(Routing.builder().register("/api", new TestApiService()))
                .addMediaSupport(JsonpSupport.create())
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

        restApi = RestApiImpl.create(webServer.port());
    }

    @AfterAll
    static void destroyClass() {
        if (webServer != null) {
            webServer.shutdown().await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void testQueryParam() {
        EchoResponse response = restApi.invokeWithResponse(GET,
                                                           "/echo",
                                                           RestRequest.builder()
                                                                   .addQueryParam("query", "queryValue"),
                                                           EchoResponse.builder())
                .await();

        assertThat(response.echoedPath, is("/api/echo"));
        assertThat(response.echoedQueryParams, is(Map.of("query", "queryValue")));

        assertThat(response.echoedEntity, is(Optional.empty()));
    }

    @Test
    void testHeaders() {
        EchoResponse response = restApi.invokeWithResponse(GET,
                                                           "/echo",
                                                           RestRequest.builder()
                                                                   .addHeader("header", "headerValue"),
                                                           EchoResponse.builder())
                .await();

        assertThat(response.echoedPath, is("/api/echo"));
        assertThat(response.echoedQueryParams, is(Map.of()));
        assertThat(response.echoedHeaders, hasEntry("header", "headerValue"));
        assertThat(response.echoedEntity, is(Optional.empty()));
    }

    @Test
    void testEntity() {
        EchoResponse response = restApi.invokeWithResponse(PUT,
                                                           "/echo",
                                                           JsonRequest.builder()
                                                                   .add("anInt", 42)
                                                                   .add("aString", "value"),
                                                           EchoResponse.builder())
                .await();

        assertThat(response.echoedPath, is("/api/echo"));
        assertThat(response.echoedQueryParams, is(Map.of()));
        assertThat(response.echoedEntity, not(Optional.empty()));
        JsonObject jsonObject = response.echoedEntity.get();

        assertThat(jsonObject.getInt("anInt"), is(42));
        assertThat(jsonObject.getString("aString"), is("value"));

    }

    @Test
    void testGetFound() {
        var response = restApi.get("/echo",
                                   RestRequest.builder(),
                                   ApiOptionalResponse
                                           .<JsonObject, JsonObject>apiResponseBuilder()
                                           .entityProcessor(Function.identity()))
                .await();

        assertThat(response.entity(), not(Optional.empty()));
        assertThat(response.status(), is(Http.Status.OK_200));
    }

    @Test
    void testGetNotFound() {
        var response = restApi.get("/missing",
                                   RestRequest.builder(),
                                   ApiOptionalResponse
                                           .<JsonObject, JsonObject>apiResponseBuilder()
                                           .entityProcessor(Function.identity()))
                .await();

        assertThat(response.entity(), is(Optional.empty()));
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
    }

    private static class TestApiService implements Service {

        @Override
        public void update(Routing.Rules rules) {
            rules.anyOf(Set.of(GET, DELETE, HEAD), "/echo", this::echoNoEntity)
                    .anyOf(Set.of(PUT, POST), "/echo", Handler.create(JsonObject.class, this::echo));
        }

        private void echo(ServerRequest req, ServerResponse res, JsonObject entity) {
            res.send(common(req).add("entity", entity).build());
        }

        private void echoNoEntity(ServerRequest req, ServerResponse res) {
            res.send(common(req).build());
        }

        private JsonObjectBuilder common(ServerRequest req) {
            JsonObjectBuilder objectBuilder = JSON.createObjectBuilder();
            objectBuilder.add("path", req.path().absolute().toRawString());

            Map<String, List<String>> queryParams = req.queryParams().toMap();
            if (!queryParams.isEmpty()) {
                JsonObjectBuilder queryParamsBuilder = JSON.createObjectBuilder();
                queryParams.forEach((key, values) -> queryParamsBuilder.add(key, values.iterator().next()));
                objectBuilder.add("params", queryParamsBuilder);
            }

            Map<String, List<String>> headers = req.headers().toMap();
            if (!headers.isEmpty()) {
                JsonObjectBuilder headersBuilder = JSON.createObjectBuilder();
                headers.forEach((key, values) -> headersBuilder.add(key, values.iterator().next()));
                objectBuilder.add("headers", headersBuilder);
            }

            return objectBuilder;
        }
    }

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