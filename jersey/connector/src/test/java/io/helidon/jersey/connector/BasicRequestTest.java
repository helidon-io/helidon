/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.jersey.connector;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import org.glassfish.jersey.client.JerseyCompletionStageRxInvoker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;

public class BasicRequestTest extends AbstractTest {
    private static BasicResource basicResource = new BasicResource();

    @BeforeAll
    public static void setup() {
        AbstractTest.extensions.set(new Extension[] {
                new HeadersSetter(), new ContentLengthSetter()
        });

        AbstractTest.rules.set(() -> {
            wireMock.stubFor(
                    WireMock.get(WireMock.urlEqualTo("/basic/get")).willReturn(
                            WireMock.ok(basicResource.get())
                    )
            );

            wireMock.stubFor(
                    WireMock.get(WireMock.urlEqualTo("/basic/getquery?first=hello&second=world"))
                            .willReturn(WireMock.ok(basicResource.getQuery("hello", "world")))
            );

            wireMock.stubFor(
                    WireMock.post(WireMock.urlEqualTo("/basic/post"))
                            .withRequestBody(new EqualToPattern("ok"))
                            .willReturn(WireMock.ok(basicResource.post("ok")))
            );

            wireMock.stubFor(
                    WireMock.post(WireMock.urlEqualTo("/basic/post"))
                            .withRequestBody(new EqualToPattern("ok"))
                            .willReturn(WireMock.ok(basicResource.post("ok")))
            );

            wireMock.stubFor(
                    WireMock.get(WireMock.urlEqualTo("/basic/headers")).willReturn(
                            WireMock.ok().withTransformers("response-headers-setter")
                    )
            );

            wireMock.stubFor(
                    WireMock.put(WireMock.urlEqualTo("/basic/produces/consumes"))
                            .withHeader(HttpHeaders.ACCEPT, new EqualToPattern("test/z-test"))
                            .willReturn(WireMock.status(406))
            );

            wireMock.stubFor(
                    WireMock.put(WireMock.urlEqualTo("/basic/produces/consumes"))
                            .withHeader(HttpHeaders.CONTENT_TYPE, new EqualToPattern("test/z-test"))
                            .willReturn(WireMock.status(415))
            );

            wireMock.stubFor(
                    WireMock.put(WireMock.urlEqualTo("/basic/produces/consumes"))
                            .withHeader(HttpHeaders.CONTENT_TYPE, new EqualToPattern("test/x-test"))
                            .withHeader(HttpHeaders.ACCEPT, new EqualToPattern("test/y-test"))
                            .willReturn(WireMock.ok(basicResource.putConsumesProduces("ok"))
                                    .withHeader(HttpHeaders.CONTENT_TYPE, "test/y-test"))
            );
        });

        AbstractTest.setup();
    }

    @Path("basic")
    public static class BasicResource {
        @Path("get")
        @GET
        public String get() {
            return "ok";
        }

        @Path("getquery")
        @GET
        public String getQuery(@QueryParam("first") String first, @QueryParam("second") String second) {
            return first + second;
        }

        @POST
        @Path("post")
        public String post(String entity) {
            return entity + entity;
        }

        @GET
        @Path("headers")
        public Response headers(@Context HttpHeaders headers) {
            final Response.ResponseBuilder response = Response.ok("ok");
            for (Map.Entry<String, List<String>> set : headers.getRequestHeaders().entrySet()) {
                if (set.getKey().toUpperCase(Locale.ROOT).startsWith("X-TEST")) {
                    response.header(set.getKey(), set.getValue().iterator().next());
                }
            }
            return response.build();
        }

        @PUT
        @Consumes("test/x-test")
        @Produces("test/y-test")
        @Path("produces/consumes")
        public String putConsumesProduces(String content) {
            return content + content;
        }
    }

    @ParamTest
    public void testBasicGet(String entityType) {
        try (Response response = target("basic", entityType).path("get").request().get()) {
            Assertions.assertEquals(200, response.getStatus());
            Assertions.assertEquals("ok", response.readEntity(String.class));
        }
    }

    @ParamTest
    public void testBasicPost(String entityType) {
        try (Response response = target("basic", entityType).path("post").request()
                .buildPost(Entity.entity("ok", MediaType.TEXT_PLAIN_TYPE)).invoke()) {
            Assertions.assertEquals(200, response.getStatus());
            Assertions.assertEquals("okok", response.readEntity(String.class));
        }
    }

    @ParamTest
    public void queryGetTest(String entityType) {
        try (Response response = target("basic", entityType).path("getquery")
                .queryParam("first", "hello")
                .queryParam("second", "world")
                .request().get()) {
            Assertions.assertEquals(200, response.getStatus());
            Assertions.assertEquals("helloworld", response.readEntity(String.class));
        }
    }

    @ParamTest
    public void testHeaders(String entityType) {
        String[][] headers = new String[][]{{"X-TEST-ONE", "ONE"}, {"X-TEST-TWO", "TWO"}, {"X-TEST-THREE", "THREE"}};
        MultivaluedHashMap<String, Object> map = new MultivaluedHashMap<>();
        Arrays.stream(headers).forEach(a -> map.add(a[0], a[1]));
        try (Response response = target("basic", entityType).path("headers").request().headers(map).get()) {
            Assertions.assertEquals(200, response.getStatus());
            Assertions.assertEquals("ok", response.readEntity(String.class));
            for (int i = 0; i != headers.length; i++) {
                Assertions.assertTrue(response.getHeaders().containsKey(headers[i][0]));
                Assertions.assertEquals(headers[i][1], response.getStringHeaders().getFirst(headers[i][0]));
            }
        }
    }

    @ParamTest
    public void testProduces(String entityType) {
        try (Response response = target("basic", entityType).path("produces/consumes").request("test/z-test")
                .put(Entity.entity("ok", new MediaType("test", "x-test")))) {
            Assertions.assertEquals(406, response.getStatus());
        }

        try (Response response = target("basic", entityType).path("produces/consumes").request("test/y-test")
                .put(Entity.entity("ok", new MediaType("test", "x-test")))) {
            Assertions.assertEquals(200, response.getStatus());
            Assertions.assertEquals("okok", response.readEntity(String.class));
            Assertions.assertEquals("test/y-test", response.getStringHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        }
    }

    @ParamTest
    public void testAsyncGet(String entityType) throws ExecutionException, InterruptedException {
        Future<Response> futureResponse = target("basic", entityType).path("get").request().async().get();
        try (Response response = futureResponse.get()) {
            Assertions.assertEquals(200, response.getStatus());
            Assertions.assertEquals("ok", response.readEntity(String.class));
        }
    }

    @ParamTest
    public void testConsumes(String entityType) {
        try (Response response = target("basic", entityType).path("produces/consumes").request("test/y-test")
                .put(Entity.entity("ok", new MediaType("test", "z-test")))) {
            Assertions.assertEquals(415, response.getStatus());
        }

        try (Response response = target("basic", entityType).path("produces/consumes").request("test/y-test")
                .put(Entity.entity("ok", new MediaType("test", "x-test")))) {
            Assertions.assertEquals(200, response.getStatus());
            Assertions.assertEquals("okok", response.readEntity(String.class));
            Assertions.assertEquals("test/y-test", response.getStringHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        }
    }

    @ParamTest
    public void testRxGet(String entityType) throws ExecutionException, InterruptedException {
        @SuppressWarnings("unchecked")
        final CompletableFuture<Response> futureResponse =
                target("basic", entityType).path("get").request().rx(JerseyCompletionStageRxInvoker.class).get();

        try (Response response = futureResponse.get()) {
            Assertions.assertEquals(200, response.getStatus());
            Assertions.assertEquals("ok", response.readEntity(String.class));
        }
    }

    @ParamTest
    public void testInputStreamEntity(String entityType) throws IOException {
        try (Response response = target("basic", entityType).path("get").request().get()) {
            Assertions.assertEquals(200, response.getStatus());
            final InputStream is = response.readEntity(InputStream.class);
            Assertions.assertEquals('o', is.read());
            Assertions.assertEquals('k', is.read());
            is.close();
        }
    }

    private static class HeadersSetter extends ResponseTransformer {

        @Override
        public com.github.tomakehurst.wiremock.http.Response transform(
                Request request,
                com.github.tomakehurst.wiremock.http.Response response,
                FileSource files,
                Parameters parameters) {

            final com.github.tomakehurst.wiremock.http.HttpHeaders requestHeaders = request.getHeaders();

            final HttpHeaders rsRequestHeaders = new HttpHeaders() {
                @Override
                public List<String> getRequestHeader(String name) {
                    return requestHeaders.getHeader(name).values();
                }

                @Override
                public String getHeaderString(String name) {
                    return requestHeaders.getHeader(name).firstValue();
                }

                @Override
                public MultivaluedMap<String, String> getRequestHeaders() {
                    MultivaluedMap<String, String> mapHeaders = new MultivaluedHashMap<>(requestHeaders.size());
                    for (String key : requestHeaders.keys()) {
                        mapHeaders.addAll(key, requestHeaders.getHeader(key).values());
                    }
                    return mapHeaders;
                }

                @Override
                public List<MediaType> getAcceptableMediaTypes() {
                    String accept = request.getHeader(HttpHeaders.ACCEPT);
                    String [] splitAccept = accept.split("/");
                    return Collections.singletonList(new MediaType(splitAccept[0], splitAccept[1]));
                }

                @Override
                @SuppressWarnings("unchecked")
                public List<Locale> getAcceptableLanguages() {
                    return Collections.EMPTY_LIST;
                }

                @Override
                public MediaType getMediaType() {
                    String content = request.getHeader(HttpHeaders.CONTENT_TYPE);
                    String [] splitContent = content.split("/");
                    return new MediaType(splitContent[0], splitContent[1]);
                }

                @Override
                public Locale getLanguage() {
                    return Locale.ROOT;
                }

                @Override
                @SuppressWarnings("unchecked")
                public Map<String, Cookie> getCookies() {
                    return Collections.EMPTY_MAP;
                }

                @Override
                public Date getDate() {
                    return new Date();
                }

                @Override
                public int getLength() {
                    return 0; //no entity
                }
            };

            final Response rsResponse = basicResource.headers(rsRequestHeaders);
            com.github.tomakehurst.wiremock.http.HttpHeaders responseHeaders =
                    com.github.tomakehurst.wiremock.http.HttpHeaders.noHeaders();
            for (Map.Entry<String, List<Object>> entry : rsResponse.getHeaders().entrySet()) {
                responseHeaders = responseHeaders.plus(new HttpHeader(entry.getKey(), entry.getValue().get(0).toString()));
            }

            return com.github.tomakehurst.wiremock.http.Response.response()
                    .headers(responseHeaders).body(rsResponse.getEntity().toString()).build();
        }

        @Override
        public String getName() {
            return "response-headers-setter";
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }
}
