/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.json.JsonSupport;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.json.JsonObject;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.PUT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings({"deprecation", "helidon:api:incubating"})
@ServerTest
class RestApiTest {
    private static RestApi restApi;
    private static RestApi compatibilityRestApi;
    private static CompatibilityOutboundRestApi compatibilityOutboundRestApi;

    @SetUpServer
    static void setupServer(WebServerConfig.Builder serverBuilder) {
        serverBuilder.routing(routing -> routing.register("/api", TestApiService::new))
                .mediaContext(MediaContext.builder()
                                      .addMediaSupport(JsonSupport.create())
                                      .addMediaSupport(JsonpSupport.create())
                                      .build());
    }

    @BeforeAll
    static void setupRestApi(WebServer webServer) {
        restApi = RestApiImpl.create(webServer.port());
        compatibilityRestApi = CompatibilityRestApi.create(webServer.port());
        compatibilityOutboundRestApi = CompatibilityOutboundRestApi.create(webServer.port());
    }

    @Test
    void testQueryParam() {
        RestRequest request = RestRequest.builder().addQueryParam("query", "queryValue");
        EchoResponse response = restApi.invokeWithJsonResponse(GET, "/echo", request, EchoResponse.builder());

        assertThat(response.echoedPath, is("/api/echo"));
        assertThat(response.echoedQueryParams, is(Map.of("query", "queryValue")));
        assertThat(response.echoedEntity, is(Optional.empty()));
    }

    @Test
    void testHeaders() {
        RestRequest request = RestRequest.builder().addHeader("header", "headerValue");
        EchoResponse response = restApi.invokeWithJsonResponse(GET, "/echo", request, EchoResponse.builder());

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
        EchoResponse response = restApi.invokeWithJsonResponse(PUT, "/echo", request, EchoResponse.builder());

        assertThat(response.echoedPath, is("/api/echo"));
        assertThat(response.echoedQueryParams, is(Map.of()));
        assertThat(response.echoedEntity, not(Optional.empty()));
        assertThat(response.echoedEntity.isPresent(), is(true));
        JsonObject jsonObject = response.echoedEntity.get();

        assertThat(jsonObject.intValue("anInt").orElseThrow(), is(42));
        assertThat(jsonObject.stringValue("aString").orElseThrow(), is("value"));

    }

    @Test
    void testGetFound() {
        var responseBuilder = ApiOptionalResponse.<JsonObject, JsonObject>apiResponseBuilder()
                .entityProcessor(Function.identity());
        var response = restApi.getJson("/echo", RestRequest.builder(), responseBuilder);

        assertThat(response.entity(), not(Optional.empty()));
        assertThat(response.status(), is(Status.OK_200));
    }

    @Test
    void testGetNotFound() {
        var responseBuilder = ApiOptionalResponse.<JsonObject, JsonObject>apiResponseBuilder()
                .entityProcessor(Function.identity());
        var response = restApi.getJson("/missing", RestRequest.builder(), responseBuilder);

        assertThat(response.entity(), is(Optional.empty()));
        assertThat(response.status(), is(Status.NOT_FOUND_404));
    }

    @Test
    void testJsonpCompatibility() {
        JsonRequest request = JsonRequest.builder()
                .add("anInt", 42)
                .add("aString", "value");
        EchoResponseJsonp response = restApi.invokeWithResponse(PUT, "/echo", request, EchoResponseJsonp.builder());

        assertThat(response.echoedPath, is("/api/echo"));
        assertThat(response.echoedQueryParams, is(Map.of()));
        assertThat(response.echoedEntity.isPresent(), is(true));
        jakarta.json.JsonObject jsonObject = response.echoedEntity.orElseThrow();

        assertThat(jsonObject.getInt("anInt"), is(42));
        assertThat(jsonObject.getString("aString"), is("value"));
    }

    @Test
    void testJsonpErrorCompatibilityOverride() {
        ApiRestException exception = assertThrows(ApiRestException.class,
                                                 () -> compatibilityRestApi.invokeWithJsonResponse(GET,
                                                                                                   "/error",
                                                                                                   RestRequest.builder(),
                                                                                                   EchoResponse.builder()));

        assertThat(exception.apiSpecificError().orElseThrow(), is("compatibility error"));
    }

    @Test
    void testJsonErrorBasePathDoesNotRecurse() {
        ApiRestException exception = assertThrows(ApiRestException.class,
                                                 () -> restApi.invokeWithJsonResponse(GET,
                                                                                      "/error",
                                                                                      RestRequest.builder(),
                                                                                      EchoResponse.builder()));

        assertThat(exception.status(), is(Status.BAD_REQUEST_400));
        assertThat(exception.apiSpecificError(), is(Optional.empty()));
    }

    @Test
    void testJsonpOutboundCompatibilityHooksReachedFromJsonPath() {
        JsonRequest request = JsonRequest.builder()
                .add("anInt", 42)
                .add("aString", "value");

        EchoResponse response = compatibilityOutboundRestApi.invokeWithJsonResponse(PUT,
                                                                                    "/echo",
                                                                                    request,
                                                                                    EchoResponse.builder());

        assertThat(compatibilityOutboundRestApi.jsonpRequestPayloadCalled(), is(true));
        assertThat(compatibilityOutboundRestApi.jsonpUpdateRequestBuilderCalled(), is(true));
        assertThat(response.echoedHeaders, hasEntry("compatibility-header", "jsonp"));
        assertThat(response.echoedEntity.isPresent(), is(true));
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
            rules.get("/error", (req, res) -> res.status(Status.BAD_REQUEST_400)
                    .send(JsonObject.builder().set("message", "compatibility error").build()));
        }

        private void echo(ServerRequest req, ServerResponse res, JsonObject entity) {
            res.send(common(req).set("entity", entity).build());
        }

        private void echoNoEntity(ServerRequest req, ServerResponse res) {
            res.send(common(req).build());
        }

        private JsonObject.Builder common(ServerRequest req) {
            JsonObject.Builder objectBuilder = JsonObject.builder();
            objectBuilder.set("path", req.path().absolute().rawPath());

            Map<String, List<String>> queryParams = req.query().toMap();
            if (!queryParams.isEmpty()) {
                JsonObject.Builder queryParamsBuilder = JsonObject.builder();
                queryParams.forEach((key, values) -> queryParamsBuilder.set(key, values.iterator().next()));
                objectBuilder.set("params", queryParamsBuilder.build());
            }

            ServerRequestHeaders headers = req.headers();
            if (headers.size() > 0) {
                JsonObject.Builder headersBuilder = JsonObject.builder();
                headers.forEach(header -> headersBuilder.set(header.name(), header.get()));
                objectBuilder.set("headers", headersBuilder.build());
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
            this.echoedPath = json.stringValue("path").orElseThrow();
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

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static class EchoResponseJsonp extends ApiEntityResponse {
        private final String echoedPath;
        private final Map<String, String> echoedQueryParams;
        private final Map<String, String> echoedHeaders;
        private final Optional<jakarta.json.JsonObject> echoedEntity;

        private EchoResponseJsonp(Builder builder) {
            super(builder);
            jakarta.json.JsonObject json = builder.entity();
            this.echoedPath = json.getString("path");
            this.echoedQueryParams = toMap(json, "params");
            this.echoedHeaders = toMap(json, "headers");
            this.echoedEntity = toObject(json, "entity");
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder extends ApiEntityResponse.Builder<Builder, EchoResponseJsonp, jakarta.json.JsonObject> {
            @Override
            public EchoResponseJsonp build() {
                return new EchoResponseJsonp(this);
            }
        }
    }

    private static class CompatibilityRestApi extends RestApiBase {
        private CompatibilityRestApi(Builder builder) {
            super(builder);
        }

        static CompatibilityRestApi create(int port) {
            return new Builder()
                    .webClientBuilder(it -> it.baseUri("http://localhost:" + port + "/api"))
                    .build();
        }

        @Override
        protected ApiRestException readError(String path,
                                             ApiRequest<?> request,
                                             io.helidon.http.Method method,
                                             String requestId,
                                             io.helidon.webclient.api.HttpClientResponse response,
                                             jakarta.json.JsonObject entity) {
            return RestException.builder()
                    .requestId(requestId)
                    .status(response.status())
                    .headers(response.headers())
                    .apiSpecificError(entity.getString("message"))
                    .message("Compatibility error")
                    .build();
        }

        static class Builder extends RestApi.Builder<Builder, CompatibilityRestApi> {
            private Builder() {
            }

            @Override
            protected CompatibilityRestApi doBuild() {
                return new CompatibilityRestApi(this);
            }
        }
    }

    private static class CompatibilityOutboundRestApi extends RestApiBase {
        private boolean jsonpRequestPayloadCalled;
        private boolean jsonpUpdateRequestBuilderCalled;

        private CompatibilityOutboundRestApi(Builder builder) {
            super(builder);
        }

        static CompatibilityOutboundRestApi create(int port) {
            return new Builder()
                    .webClientBuilder(it -> it.baseUri("http://localhost:" + port + "/api"))
                    .build();
        }

        boolean jsonpRequestPayloadCalled() {
            return jsonpRequestPayloadCalled;
        }

        boolean jsonpUpdateRequestBuilderCalled() {
            return jsonpUpdateRequestBuilderCalled;
        }

        @Override
        protected Supplier<io.helidon.webclient.api.HttpClientResponse> requestJsonPayload(
                String path,
                ApiRequest<?> request,
                io.helidon.http.Method method,
                String requestId,
                io.helidon.webclient.api.HttpClientRequest requestBuilder,
                jakarta.json.JsonObject jsonObject) {
            jsonpRequestPayloadCalled = true;
            return super.requestJsonPayload(path, request, method, requestId, requestBuilder, jsonObject);
        }

        @Override
        protected io.helidon.webclient.api.HttpClientRequest updateRequestBuilder(
                io.helidon.webclient.api.HttpClientRequest requestBuilder,
                String path,
                ApiRequest<?> request,
                io.helidon.http.Method method,
                String requestId,
                jakarta.json.JsonObject jsonObject) {
            jsonpUpdateRequestBuilderCalled = true;
            requestBuilder.headers(headers -> headers.set(io.helidon.http.HeaderNames.create("compatibility-header"),
                                                          "jsonp"));
            return super.updateRequestBuilder(requestBuilder, path, request, method, requestId, jsonObject);
        }

        static class Builder extends RestApi.Builder<Builder, CompatibilityOutboundRestApi> {
            private Builder() {
            }

            @Override
            protected CompatibilityOutboundRestApi doBuild() {
                return new CompatibilityOutboundRestApi(this);
            }
        }
    }
}
