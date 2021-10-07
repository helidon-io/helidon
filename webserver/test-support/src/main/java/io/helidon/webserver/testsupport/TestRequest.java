/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.testsupport;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.Http;

/**
 * An API to compose a test request.
 */
public class TestRequest {

    private final TestClient testClient;
    private final String path;
    private final StringBuilder query = new StringBuilder();
    private final Map<String, List<String>> headers = new HashMap<>();
    private volatile Http.Version version = Http.Version.V1_1;

    /**
     * Creates new instance.
     *
     * @param testClient a client.
     * @param path an URI path.
     */
    TestRequest(TestClient testClient, String path) {
        this.testClient = testClient;
        if (path == null) {
            path = "/";
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }
        int ind = path.indexOf('?');
        if (ind > 0) {
            query.append(path.substring(ind + 1));
            path = path.substring(0, ind);
        }
        this.path = path;
    }

    /**
     * Add a query parameter.
     *
     * @param name a name.
     * @param value a value.
     * @return Updated instance.
     * @throws NullPointerException If {@code name} or {@code value} parameter is {@code null}.
     */
    public TestRequest queryParameter(String name, String value) {
        Objects.requireNonNull(name, "Parameter 'name' is null!");
        Objects.requireNonNull(name, "Parameter 'value' is null!");
        synchronized (query) {
            if (query.length() == 0) {
                query.append('?');
            } else {
                query.append('&');
            }
            query.append(encode(name))
                    .append('=')
                    .append(encode(value));
        }
        return this;
    }

    /**
     * Add a header.
     *
     * @param name a name.
     * @param value a value.
     * @return Updated instance.
     * @throws NullPointerException If {@code name} or {@code value} parameter is {@code null}.
     */
    public TestRequest header(String name, String value) {
        Objects.requireNonNull(name, "Parameter 'name' is null!");
        Objects.requireNonNull(name, "Parameter 'value' is null!");
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        return this;
    }

    /**
     * Define an HTTP version. Default is {@link Http.Version#V1_1 HTTP/1.1}.
     *
     * @param version a version to set.
     * @return Updated instance.
     * @throws NullPointerException If {@code name} or {@code value} parameter is {@code null}.
     */
    public TestRequest version(Http.Version version) {
        Objects.requireNonNull(version, "Parameter 'version' is null!");
        this.version = version;
        return this;
    }

    private URI uri() {
        return URI.create(path + query);
    }

    // todo Add support for standard headers.

    /**
     * Calls HTTP GET method with body.
     *
     * @param mediaPublisher a request body publisher.
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse get(MediaPublisher mediaPublisher) throws InterruptedException, TimeoutException {
        return call(Http.Method.GET, mediaPublisher);
    }

    /**
     * Calls HTTP GET method with body.
     *
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse get() throws InterruptedException, TimeoutException {
        return get(null);
    }

    /**
     * Calls HTTP POST method with body.
     *
     * @param mediaPublisher a request body publisher.
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse post(MediaPublisher mediaPublisher) throws InterruptedException, TimeoutException {
        return call(Http.Method.POST, mediaPublisher);
    }

    /**
     * Calls HTTP POST method with body.
     *
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse post() throws InterruptedException, TimeoutException {
        return post(null);
    }

    /**
     * Calls HTTP PUT method with body.
     *
     * @param mediaPublisher a request body publisher.
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse put(MediaPublisher mediaPublisher) throws InterruptedException, TimeoutException {
        return call(Http.Method.PUT, mediaPublisher);
    }

    /**
     * Calls HTTP PUT method with body.
     *
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse put() throws InterruptedException, TimeoutException {
        return put(null);
    }

    /**
     * Calls HTTP DELETE method with body.
     *
     * @param mediaPublisher a request body publisher.
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse delete(MediaPublisher mediaPublisher) throws InterruptedException, TimeoutException {
        return call(Http.Method.DELETE, mediaPublisher);
    }

    /**
     * Calls HTTP DELETE method with body.
     *
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse delete() throws InterruptedException, TimeoutException {
        return delete(null);
    }

    /**
     * Calls HTTP OPTIONS method with body.
     *
     * @param mediaPublisher a request body publisher.
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse options(MediaPublisher mediaPublisher) throws InterruptedException, TimeoutException {
        return call(Http.Method.OPTIONS, mediaPublisher);
    }

    /**
     * Calls HTTP OPTIONS method with body.
     *
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse options() throws InterruptedException, TimeoutException {
        return options(null);
    }

    /**
     * Calls HTTP HEAD method with body.
     *
     * @param mediaPublisher a request body publisher.
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse head(MediaPublisher mediaPublisher) throws InterruptedException, TimeoutException {
        return call(Http.Method.HEAD, mediaPublisher);
    }

    /**
     * Calls HTTP HEAD method with body.
     *
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse head() throws InterruptedException, TimeoutException {
        return head(null);
    }

    /**
     * Calls HTTP TRACE method with body.
     *
     * @param mediaPublisher a request body publisher.
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse trace(MediaPublisher mediaPublisher) throws InterruptedException, TimeoutException {
        return call(Http.Method.TRACE, mediaPublisher);
    }

    /**
     * Calls HTTP TRACE method with body.
     *
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse trace() throws InterruptedException, TimeoutException {
        return trace(null);
    }

    /**
     * Calls using specified HTTP method with body.
     *
     * @param method an HTTP method.
     * @param mediaPublisher a request body publisher.
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse call(Http.RequestMethod method, MediaPublisher mediaPublisher)
            throws InterruptedException, TimeoutException {
        if (mediaPublisher != null && !headers.containsKey(Http.Header.CONTENT_TYPE) && mediaPublisher.mediaType() != null) {
            header(Http.Header.CONTENT_TYPE, mediaPublisher.mediaType().toString());
        }
        return testClient.call(method, version, uri(), headers, mediaPublisher);
    }

    /**
     * Calls using specified HTTP method with body.
     *
     * @param method an HTTP method.
     * @return a response to read.
     * @throws InterruptedException if thread is interrupted.
     * @throws TimeoutException if request timeout is reached.
     */
    public TestResponse call(Http.RequestMethod method) throws InterruptedException, TimeoutException {
        return testClient.call(method, version, uri(), headers, null);
    }

    private String encode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }
}
