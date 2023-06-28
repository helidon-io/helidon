/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.http1.Http1ClientResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class HttpClientTest {

    // Validate that the Http Shortcut methods are using their corresponding HTTP Method
    @Test
    void testHttpMethodShortcuts() {
        Map<Http.Method, FakeHttpClientRequest> map = Map.of(Http.Method.GET, new FakeHttpClient().get(),
                                                             Http.Method.POST, new FakeHttpClient().post(),
                                                             Http.Method.PUT, new FakeHttpClient().put(),
                                                             Http.Method.DELETE, new FakeHttpClient().delete(),
                                                             Http.Method.HEAD, new FakeHttpClient().head(),
                                                             Http.Method.OPTIONS, new FakeHttpClient().options(),
                                                             Http.Method.TRACE, new FakeHttpClient().trace(),
                                                             Http.Method.PATCH, new FakeHttpClient().patch());

        for (Map.Entry<Http.Method, FakeHttpClientRequest> entry : map.entrySet()) {
            assertThat(entry.getValue().getMethod(), is(entry.getKey()));
        }
    }

    // Validate that the correct HTTP Method is used and URI is passed correctly to the shortcut method
    @Test
    void testHttpMethodShortcutsWithUri() {
        String baseURI = "http://localhost:1234";
        Map<Http.Method, FakeHttpClientRequest> map = Map.of(
                // use the method name as the path of the URL passed as argument to the http method shortcut
                // ex. http://localhost:1234/GET
                Http.Method.GET, new FakeHttpClient().get(baseURI + "/" + Http.Method.GET.text()),
                Http.Method.POST, new FakeHttpClient().post(baseURI + "/" + Http.Method.POST.text()),
                Http.Method.PUT, new FakeHttpClient().put(baseURI + "/" + Http.Method.PUT.text()),
                Http.Method.DELETE, new FakeHttpClient().delete(baseURI + "/" + Http.Method.DELETE.text()),
                Http.Method.HEAD, new FakeHttpClient().head(baseURI + "/" + Http.Method.HEAD.text()),
                Http.Method.OPTIONS, new FakeHttpClient().options(baseURI + "/" + Http.Method.OPTIONS.text()),
                Http.Method.TRACE, new FakeHttpClient().trace(baseURI + "/" + Http.Method.TRACE.text()),
                Http.Method.PATCH, new FakeHttpClient().patch(baseURI + "/" + Http.Method.PATCH.text())
        );

        for (Map.Entry<Http.Method, FakeHttpClientRequest> entry : map.entrySet()) {
            assertThat(entry.getValue().getMethod(), is(entry.getKey()));
            // validate that the URL path is the method name as passed on to the shortcut method during map init above
            assertThat(entry.getValue().getUri().getPath(), is("/" + entry.getKey().text()));
        }
    }

    static class FakeHttpClient implements HttpClient<FakeHttpClientRequest, Http1ClientResponse> {
        @Override
        public FakeHttpClientRequest method(Http.Method method) {
            return new FakeHttpClientRequest(method);
        }
    }

    static class FakeHttpClientRequest implements ClientRequest<FakeHttpClientRequest, Http1ClientResponse> {
        private Http.Method method;
        private URI uri;

        FakeHttpClientRequest(Http.Method method) {
            this.method = method;
        }

        public Http.Method getMethod() {
            return this.method;
        }

        public URI getUri() {
            return this.uri;
        }

        @Override
        public FakeHttpClientRequest tls(Tls tls) {
            return null;
        }

        @Override
        public FakeHttpClientRequest uri(URI uri) {
            this.uri = uri;
            return this;
        }

        @Override
        public FakeHttpClientRequest fragment(String fragment) {
            return this;
        }

        @Override
        public FakeHttpClientRequest followRedirects(boolean followRedirects) {
            return this;
        }

        @Override
        public FakeHttpClientRequest maxRedirects(int maxRedirects) {
            return this;
        }

        @Override
        public FakeHttpClientRequest header(Http.HeaderValue header) {
            return null;
        }

        @Override
        public FakeHttpClientRequest headers(Headers headers) {
            return null;
        }

        @Override
        public FakeHttpClientRequest headers(Function<ClientRequestHeaders, WritableHeaders<?>> headersConsumer) {
            return null;
        }

        @Override
        public FakeHttpClientRequest pathParam(String name, String value) {
            return null;
        }

        @Override
        public FakeHttpClientRequest queryParam(String name, String... values) {
            return null;
        }

        @Override
        public Http1ClientResponse request() {
            return null;
        }

        @Override
        public ClientRequestHeaders headers() {
            return null;
        }

        @Override
        public Http1ClientResponse submit(Object entity) {
            return null;
        }

        @Override
        public Http1ClientResponse outputStream(OutputStreamHandler outputStreamConsumer) {
            return null;
        }

        @Override
        public URI resolvedUri() {
            return null;
        }

        @Override
        public FakeHttpClientRequest connection(ClientConnection connection) {
            return null;
        }

        @Override
        public FakeHttpClientRequest skipUriEncoding() {
            return null;
        }

        @Override
        public FakeHttpClientRequest property(String propertyName, String propertyValue) {
            return null;
        }

        @Override
        public FakeHttpClientRequest keepAlive(boolean keepAlive) {
            return this;
        }
    }
}
