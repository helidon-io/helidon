/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriFragment;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.Headers;
import io.helidon.http.Method;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class HttpClientTest {

    // Validate that the Http Shortcut methods are using their corresponding HTTP Method
    @Test
    void testHttpMethodShortcuts() {
        Map<Method, FakeHttpClientRequest> map = Map.of(Method.GET, new FakeHttpClient().get(),
                                                        Method.POST, new FakeHttpClient().post(),
                                                        Method.PUT, new FakeHttpClient().put(),
                                                        Method.DELETE, new FakeHttpClient().delete(),
                                                        Method.HEAD, new FakeHttpClient().head(),
                                                        Method.OPTIONS, new FakeHttpClient().options(),
                                                        Method.TRACE, new FakeHttpClient().trace(),
                                                        Method.PATCH, new FakeHttpClient().patch());

        for (Map.Entry<Method, FakeHttpClientRequest> entry : map.entrySet()) {
            assertThat(entry.getValue().getMethod(), is(entry.getKey()));
        }
    }

    // Validate that the correct HTTP Method is used and URI is passed correctly to the shortcut method
    @Test
    void testHttpMethodShortcutsWithUri() {
        String baseURI = "http://localhost:1234";
        Map<Method, FakeHttpClientRequest> map = Map.of(
                // use the method name as the path of the URL passed as argument to the http method shortcut
                // ex. http://localhost:1234/GET
                Method.GET, new FakeHttpClient().get(baseURI + "/" + Method.GET.text()),
                Method.POST, new FakeHttpClient().post(baseURI + "/" + Method.POST.text()),
                Method.PUT, new FakeHttpClient().put(baseURI + "/" + Method.PUT.text()),
                Method.DELETE, new FakeHttpClient().delete(baseURI + "/" + Method.DELETE.text()),
                Method.HEAD, new FakeHttpClient().head(baseURI + "/" + Method.HEAD.text()),
                Method.OPTIONS, new FakeHttpClient().options(baseURI + "/" + Method.OPTIONS.text()),
                Method.TRACE, new FakeHttpClient().trace(baseURI + "/" + Method.TRACE.text()),
                Method.PATCH, new FakeHttpClient().patch(baseURI + "/" + Method.PATCH.text())
        );

        for (Map.Entry<Method, FakeHttpClientRequest> entry : map.entrySet()) {
            assertThat(entry.getValue().getMethod(), is(entry.getKey()));
            // validate that the URL path is the method name as passed on to the shortcut method during map init above
            assertThat(entry.getValue().getUri().getPath(), is("/" + entry.getKey().text()));
        }
    }

    static class FakeHttpClient implements HttpClient<FakeHttpClientRequest> {
        @Override
        public FakeHttpClientRequest method(Method method) {
            return new FakeHttpClientRequest(method);
        }
    }

    static class FakeHttpClientRequest implements ClientRequest<FakeHttpClientRequest> {
        private final Method method;
        private URI uri;

        FakeHttpClientRequest(Method method) {
            this.method = method;
        }

        public Method getMethod() {
            return this.method;
        }

        public URI getUri() {
            return this.uri;
        }

        @Override
        public FakeHttpClientRequest skipUriEncoding(boolean skip) {
            return this;
        }

        @Override
        public FakeHttpClientRequest readTimeout(Duration readTimeout) {
            return this;
        }

        @Override
        public FakeHttpClientRequest readContinueTimeout(Duration readContinueTimeout) {
            return this;
        }

        @Override
        public FakeHttpClientRequest sendExpectContinue(boolean sendExpectContinue) {
            return null;
        }

        @Override
        public FakeHttpClientRequest tls(Tls tls) {
            return this;
        }

        @Override
        public FakeHttpClientRequest uri(URI uri) {
            this.uri = uri;
            return this;
        }

        @Override
        public FakeHttpClientRequest fragment(UriFragment fragment) {
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
        public FakeHttpClientRequest header(Header header) {
            return this;
        }

        @Override
        public FakeHttpClientRequest headers(Headers headers) {
            return this;
        }

        @Override
        public FakeHttpClientRequest headers(Consumer<ClientRequestHeaders> headersConsumer) {
            return this;
        }

        @Override
        public FakeHttpClientRequest pathParam(String name, String value) {
            return this;
        }

        @Override
        public FakeHttpClientRequest queryParam(String name, String... values) {
            return this;
        }

        @Override
        public HttpClientResponse request() {
            return null;
        }

        @Override
        public ClientRequestHeaders headers() {
            return null;
        }

        @Override
        public HttpClientResponse submit(Object entity) {
            return null;
        }

        @Override
        public HttpClientResponse outputStream(OutputStreamHandler outputStreamConsumer) {
            return null;
        }

        @Override
        public ClientUri resolvedUri() {
            return null;
        }

        @Override
        public FakeHttpClientRequest connection(ClientConnection connection) {
            return this;
        }

        @Override
        public FakeHttpClientRequest proxy(Proxy proxy) {
            return this;
        }

        @Override
        public FakeHttpClientRequest uri(ClientUri uri) {
            return this;
        }

        @Override
        public FakeHttpClientRequest property(String propertyName, String propertyValue) {
            return this;
        }

        @Override
        public FakeHttpClientRequest keepAlive(boolean keepAlive) {
            return this;
        }

        @Override
        public boolean followRedirects() {
            return true;
        }

        @Override
        public int maxRedirects() {
            return 5;
        }
    }
}
