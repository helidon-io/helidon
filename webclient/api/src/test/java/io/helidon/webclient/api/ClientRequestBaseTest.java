/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.common.uri.UriPath;
import io.helidon.http.Method;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ClientRequestBaseTest {

    /**
     * Verify that query parameters are preserved when resolving URI templates (cf. issue #8566).
     * Make sure to test both absolute and relative URIs as they are handled differently when resolving.
     */
    @Test
    public void resolvedUriTest() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/")
                .queryParam("k", "v").resolvedUri();
        assertThat(uri.authority(), is("www.example.com:443"));
        assertThat(uri.host(), is("www.example.com"));
        assertThat(uri.path(), is(UriPath.root()));
        assertThat(uri.port(), is(443));
        assertThat(uri.scheme(), is("https"));
        assertThat(uri.query().get("k"), is("v"));

        uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("k", "v").resolvedUri();
        assertThat(uri.authority(), is("www.example.com:443"));
        assertThat(uri.host(), is("www.example.com"));
        assertThat(uri.path(), is(UriPath.create("/p")));
        assertThat(uri.port(), is(443));
        assertThat(uri.scheme(), is("https"));
        assertThat(uri.query().get("k"), is("v"));

        uri = new FakeClientRequest()
                .uri("example/{path}")
                .pathParam("path", "p")
                .queryParam("k", "v").resolvedUri();
        assertThat(uri.authority(), is("localhost:80"));
        assertThat(uri.host(), is("localhost"));
        assertThat(uri.path(), is(UriPath.create("/example/p")));
        assertThat(uri.port(), is(80));
        assertThat(uri.scheme(), is("http"));
        assertThat(uri.query().get("k"), is("v"));

        uri = new FakeClientRequest()
                .uri("https://www.example.com/p?k={k}")
                .pathParam("k", "v")
                .queryParam("k", "v").resolvedUri();
        assertThat(uri.authority(), is("www.example.com:443"));
        assertThat(uri.host(), is("www.example.com"));
        assertThat(uri.path(), is(UriPath.create("/p%3Fk=v")));
        assertThat(uri.port(), is(443));
        assertThat(uri.scheme(), is("https"));
        assertThat(uri.query().get("k"), is("v"));
    }


    private static final class FakeClientRequest extends ClientRequestBase<FakeClientRequest, HttpClientResponse> {
        private FakeClientRequest() {
            super(WebClientConfig.create(), null, "fake", Method.GET, ClientUri.create(), Collections.emptyMap());
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            return null;
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamHandler) {
            return null;
        }
    }
}
