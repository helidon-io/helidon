/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JerseySupportTest.
 */
public class JerseySupportTest {

    private static WebTarget webTarget;

    @BeforeAll
    public static void startServerAndClient() throws Exception {
        JerseyExampleMain.INSTANCE.webServer(true);
        webTarget = JerseyExampleMain.INSTANCE.target();

        //        JerseyGrizzlyRiMain.main(null);
        //        webTarget = JerseyExampleMain.client().target("http://localhost:8080");
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
    public void injection() throws Exception {
        Response response = get("jersey/first/injection");
        doAssert(response,
                 "request=io.helidon.webserver.RequestRouting$RoutedRequest\n"
                         + "response=io.helidon.webserver.RequestRouting$RoutedResponse\n"
                         + "spanContext=io.opentracing.noop.NoopSpanContextImpl");
    }

    @Test
    public void queryGet() throws Exception {
        Response response = webTarget.path("jersey/first/query")
                                     .queryParam("a", "a&value")
                                     .queryParam("b", "b&c=value")
                                     .request()
                                     .get();

        doAssert(response, "a='a&value';b='b&c=value'");
    }

    @Test
    public void pathGet() throws Exception {
        Response response = webTarget.path("jersey/first/path/123").request().get();

        doAssert(response, "num=123");
    }

    @Test
    public void simpleGet() throws Exception {
        Response response = get("jersey/first/hello");

        doAssert(response, "Hello!");
    }

    @Test
    public void longGet() throws Exception {
        Response response = get("jersey/first/longhello");

        doAssert(response, "Hello Long: " + longData(JerseyExampleResource.LARGE_DATA_SIZE_BYTES) + "!");
    }

    private Response get(String path) {
        return webTarget.path(path).request().get();
    }

    @Test
    public void simplePost() throws Exception {
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
    public void longPostAndResponse() throws Exception {
        StringBuilder data = longData(JerseyExampleResource.LARGE_DATA_SIZE_BYTES);

        Response response = webTarget.path("jersey/first/hello")
                                     .request()
                                     .post(Entity.entity(data.toString(), MediaType.TEXT_PLAIN_TYPE));

        doAssert(response, "Hello: " + data.toString() + "!");
    }

    @Test
    public void errorNoEntity() throws Exception {
        Response response = get("jersey/first/error/noentity");

        doAssert(response, "", 543);
    }

    @Test
    public void errorWithEntity() throws Exception {
        Response response = get("jersey/first/error/entity");

        doAssert(response, "error-entity", 543);
    }

    @Test
    public void errorThrownNoEntity() throws Exception {
        Response response = get("jersey/first/error/thrown/noentity");

        doAssert(response, "", 543);
    }

    @Test
    public void errorThrownEntity() throws Exception {
        Response response = get("jersey/first/error/thrown/entity");

        doAssert(response, "error-entity", 543);
    }

    @Test
    public void errorThrownError() throws Exception {
        Response response = get("jersey/first/error/thrown/error");

        doAssert(response, "", 500);
    }

    @Test
    public void errorThrownUnhandled() throws Exception {
        Response response = get("jersey/first/error/thrown/unhandled");

        doAssert(response, "", 500);
    }

    @Test
    public void simplePostNotFound() throws Exception {
        Response response = post("jersey/first/non-existent-resource");

        doAssert(response, "", Response.Status.NOT_FOUND);
    }

    /**
     * In this test, we need to properly end the connection because the request data won't be fully consumed.
     */
    @Test
    public void longPostNotFound() throws Exception {
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
        assertNotNull(response);
        doAssert(response, "", Response.Status.NOT_FOUND);
    }

    /**
     * Jersey doesn't close the output stream in case there is no entity. We need to close
     * the publisher by ourselves and this is the test.
     *
     * @throws Exception in case of an error
     */
    @Test
    public void noResponseEntityGet() throws Exception {
        Response response = get("jersey/first/noentity");

        doAssert(response, "", Response.Status.OK);
    }

    @Test
    public void simpleGetNotFound() throws Exception {
        Response response = get("jersey/first/non-existent-resource");

        doAssert(response, "", Response.Status.NOT_FOUND);
    }

    @Test
    public void nonJerseyGetNotFound() throws Exception {
        Response response = get("jersey/second");

        doAssert(response, "second-content: ");
    }

    @Test
    public void nonJerseyPOSTNotFound() throws Exception {
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

    private void doAssert(Response response, String expected) {
        try {
            assertEquals(Response.Status.Family.SUCCESSFUL, response.getStatusInfo().getFamily(),
                    "Unexpected error: " + response.getStatus());
            assertEquals(expected, response.readEntity(String.class));
        } finally {
            response.close();
        }
    }

    private void doAssert(Response response, String expectedContent, Response.StatusType status) {
        doAssert(response, expectedContent, status.getStatusCode());
    }

    private void doAssert(Response response, String expectedContent, int expectedStatusCode) {
        try {
            assertEquals(expectedStatusCode, response.getStatus(),
                    "Unexpected error: " + response.getStatus());
            assertEquals(expectedContent, response.readEntity(String.class));
        } finally {
            response.close();
        }
    }
}

