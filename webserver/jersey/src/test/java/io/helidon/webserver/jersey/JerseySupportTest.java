/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.jersey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;

import io.helidon.common.http.HttpRequest;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.CommonProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.jersey.JerseySupport.IGNORE_EXCEPTION_RESPONSE;
import static io.helidon.webserver.jersey.JerseySupport.basePath;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/**
 * The JerseySupportTest.
 */
public class JerseySupportTest {

    private static WebTarget webTarget;

    @BeforeAll
    public static void startServerAndClient() throws Exception {
        JerseyExampleMain.INSTANCE.webServer(true);
        webTarget = JerseyExampleMain.INSTANCE.target();
    }

    @Test
    public void headers() throws Exception {
        Response response = webTarget.path("jersey/first/headers")
                                     .queryParam("header", "test-header")
                                     .request()
                                     .header("Test-Header", "test-header-value")
                                     .header("TEST-HEADER", "test-header-value2")
                                     .get();
        doAssert(response, "headers=test-header-value,test-header-value2");
    }

    @Test
    public void injection() {
        Response response = get("jersey/first/injection");
        doAssert(response,
                """
                        request=io.helidon.webserver.RequestRouting$RoutedRequest
                        response=io.helidon.webserver.RequestRouting$RoutedResponse
                        spanContext=null""");
    }

    @Test
    public void queryGet() {
        Response response = webTarget.path("jersey/first/query")
                                     .queryParam("a", "a&value")
                                     .queryParam("b", "b&c=value")
                                     .request()
                                     .get();

        doAssert(response, "a='a&value';b='b&c=value'");
    }

    @Test
    public void pathGet() {
        Response response = webTarget.path("jersey/first/path/123").request().get();

        doAssert(response, "num=123");
    }

    @Test
    public void simpleGet() {
        Response response = get("jersey/first/hello");

        doAssert(response, "Hello!");
    }

    @Test
    public void longGet() {
        Response response = get("jersey/first/longhello");

        doAssert(response, "Hello Long: " + longData(JerseyExampleResource.LARGE_DATA_SIZE_BYTES) + "!");
    }

    private Response get(String path) {
        return webTarget.path(path).request().get();
    }

    @Test
    public void simplePost() {
        Response response = post("jersey/first/hello");

        doAssert(response, "Hello: my-entity!");
    }

    @Test
    public void longPost() throws Throwable {
        StringBuilder data = longData(JerseyExampleResource.LARGE_DATA_SIZE_BYTES);

        synchronized (JerseyExampleResource.class) {
            JerseyExampleResource.streamException = null;
            Response response = webTarget.path("jersey/first/stream")
                                         .queryParam("length", JerseyExampleResource.LARGE_DATA_SIZE_BYTES)
                                         .request()
                                         .post(Entity.entity(data.toString(), MediaType.TEXT_PLAIN_TYPE));

            if (JerseyExampleResource.streamException != null) {
                throw JerseyExampleResource.streamException;
            }
            doAssert(response, "OK");
        }

    }

    @Test
    public void longPostAndResponse() {
        StringBuilder data = longData(JerseyExampleResource.LARGE_DATA_SIZE_BYTES);

        Response response = webTarget.path("jersey/first/hello")
                                     .request()
                                     .post(Entity.entity(data.toString(), MediaType.TEXT_PLAIN_TYPE));

        doAssert(response, "Hello: " + data.toString() + "!");
    }

    @Test
    public void errorNoEntity() {
        Response response = get("jersey/first/error/noentity");

        doAssert(response, "", 543);
    }

    @Test
    public void errorWithEntity() {
        Response response = get("jersey/first/error/entity");

        doAssert(response, "error-entity", 543);
    }

    @Test
    public void errorThrownNoEntity() {
        Response response = get("jersey/first/error/thrown/noentity");

        doAssert(response, "", 543);
    }

    @Test
    public void errorThrownEntity() {
        Response response = get("jersey/first/error/thrown/entity");

        doAssert(response, "error-entity", 543);
    }

    @Test
    public void errorThrownError() {
        Response response = get("jersey/first/error/thrown/error");

        doAssert(response, null, 500);
    }

    @Test
    public void errorThrownUnhandled() {
        Response response = get("jersey/first/error/thrown/unhandled");

        doAssert(response, null, 500);
    }

    @Test
    public void simplePostNotFound() {
        Response response = post("jersey/first/non-existent-resource");

        doAssert(response, null, Response.Status.NOT_FOUND);
    }

    @Test
    public void notFoundResponse() {
        Response response = delete("jersey/first/notfound");

        doAssert(response, "Not Found", Response.Status.NOT_FOUND);
    }

    /**
     * In this test, we need to properly end the connection because the request data won't be fully consumed.
     */
    @Test
    public void longPostNotFound() {
        Response response = null;
        try {
             response = webTarget.path("jersey/first/non-existent-resource")
                                .request()
                                .post(Entity.entity(longData(JerseyExampleResource.LARGE_DATA_SIZE_BYTES).toString(),
                                                    MediaType.TEXT_PLAIN_TYPE));
        } catch (ProcessingException e) {
            // some clients are unable to receive an error while sending an entity
            // in this case the test is a no-op.
            return;
        }
        assertThat(response, notNullValue());
        doAssert(response, null, Response.Status.NOT_FOUND);
    }

    /**
     * Jersey doesn't close the output stream in case there is no entity. We need to close
     * the publisher by ourselves and this is the test.
     *
     */
    @Test
    public void noResponseEntityGet() {
        Response response = get("jersey/first/noentity");

        doAssert(response, "", Response.Status.OK);
    }

    @Test
    public void simpleGetNotFound() {
        Response response = get("jersey/first/non-existent-resource");

        doAssert(response, null, Response.Status.NOT_FOUND);
    }

    @Test
    public void nonJerseyGetNotFound() {
        Response response = get("jersey/second");

        doAssert(response, "second-content: ");
    }

    @Test
    public void nonJerseyPOSTNotFound() {
        Response response = webTarget.path("jersey/second").request().post(Entity.entity("my-entity", MediaType.TEXT_PLAIN_TYPE));

        doAssert(response, "second-content: my-entity");
    }

    @Test
    public void requestUriEndingSlash() throws Exception {
        URI uri = URI.create(webTarget.getUri() + "/jersey/first/requestUri/");
        URLConnection urlConnection = uri.toURL().openConnection();
        urlConnection.connect();
        InputStream inputStream = urlConnection.getInputStream();
        String s = new BufferedReader(new InputStreamReader(inputStream)).readLine();
        inputStream.close();

        assertThat(s, endsWith("/requestUri/"));
    }

    @Test
    public void requestUriNotEndingSlash() throws Exception {
        URI uri = URI.create(webTarget.getUri() + "/jersey/first/requestUri");
        URLConnection urlConnection = uri.toURL().openConnection();
        urlConnection.connect();
        InputStream inputStream = urlConnection.getInputStream();
        String s = new BufferedReader(new InputStreamReader(inputStream)).readLine();
        inputStream.close();

        assertThat(s, endsWith("/requestUri"));
    }

    @Test
    public void pathEncoding1() {
        Response response = webTarget.path("jersey/first/encoding/abc%3F")
                .request()
                .get();

        doAssert(response, "abc?");
    }

    @Test
    public void pathEncoding2() {
        Response response = webTarget.path("jersey/first/encoding/abc%3B/done")
                .request()
                .get();

        doAssert(response, "abc;");
    }

    @Test
    public void streamingOutput() throws IOException {
        Response response = webTarget.path("jersey/first/streamingOutput")
                .request()
                .get();

        assertThat(response.getStatusInfo().getFamily(), is(Response.Status.Family.SUCCESSFUL));

        try (InputStream is = response.readEntity(InputStream.class)) {
            byte[] buffer = new byte[32];
            int n = is.read(buffer);        // should read only first chunk
            assertThat(new String(buffer, 0, n), is("{ value: \"first\" }\n"));
            while (is.read(buffer) > 0) {
                // consume rest of stream
            }
        }
    }

    @Test
    public void testBasePath() {
        assertThat(basePath(new PathMockup(null, "/")),
                is("/"));
        assertThat(basePath(new PathMockup("/my/application/path", "/")),
                is("/my/application/path/"));
        assertThat(basePath(new PathMockup("/my/application/path", "/path")),
                is("/my/application/"));
        assertThat(basePath(new PathMockup("/my/application/path", "/application/path")),
                is("/my/"));
        assertThat(basePath(new PathMockup("/my/application/path", "/my/application/path")),
                is("/"));
    }

    @Test
    public void testJerseyProperties() {
        assertThat(System.getProperty(CommonProperties.ALLOW_SYSTEM_PROPERTIES_PROVIDER), is("true"));
        assertThat(System.getProperty(IGNORE_EXCEPTION_RESPONSE), is("true"));
    }

    @Test
    public void testInternalErrorMapper() {
        JerseySupport.Builder builder = JerseySupport.builder();
        JerseySupport jerseySupport = builder.build();
        assertThat(jerseySupport, is(notNullValue()));
        assertThat(builder.resourceConfig().getClasses(), contains(JerseySupport.InternalErrorMapper.class));
    }

    static class PathMockup implements HttpRequest.Path {
        private final String absolutePath;
        private final String path;

        PathMockup(String absolutePath, String path) {
            this.absolutePath = absolutePath;
            this.path = path;
        }

        @Override
        public String param(String name) {
            return "";
        }

        @Override
        public List<String> segments() {
            return Collections.emptyList();
        }

        @Override
        public String toRawString() {
            return toString();
        }

        @Override
        public HttpRequest.Path absolute() {
            return absolutePath == null ? this : new PathMockup(null, absolutePath);
        }

        @Override
        public String toString() {
            return path;
        }
    }

    static StringBuilder longData(int bytes) {
        StringBuilder data = new StringBuilder(bytes);
        int i = 0;
        for (; data.length() < bytes; ++i) {
            data.append(i)
                .append("\n");
        }
        return data;
    }

    private Response post(String path) {
        return webTarget.path(path)
                        .request()
                        .post(Entity.entity("my-entity", MediaType.TEXT_PLAIN_TYPE));
    }

    private Response delete(String path) {
        return webTarget.path(path)
                        .request()
                        .delete();
    }

    private void doAssert(Response response, String expected) {
        try (response) {
            assertThat(response.getStatusInfo().getFamily(), is(Response.Status.Family.SUCCESSFUL));
            assertThat(response.readEntity(String.class), is(expected));
        }
    }

    private void doAssert(Response response, String expectedContent, Response.StatusType status) {
        doAssert(response, expectedContent, status.getStatusCode());
    }

    private void doAssert(Response response, String expectedContent, int expectedStatusCode) {
        try (response) {
            assertThat(response.getStatus(), is(expectedStatusCode));
            if (expectedContent != null) {
                assertThat(response.readEntity(String.class), is(expectedContent));
            }
        }
    }
}

