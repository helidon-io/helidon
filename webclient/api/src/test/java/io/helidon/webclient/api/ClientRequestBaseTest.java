/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.List;

import io.helidon.common.uri.UriPath;
import io.helidon.http.Method;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientRequestBaseTest {

    /**
     * Verify that query parameters are preserved when resolving URI templates (cf. issue #8566).
     * Make sure to test both absolute and relative URIs as they are handled differently when resolving.
     */
    @Test
    void resolvedUriTest() {
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
    }

    @Test
    void requestQueryOverridesTemplateQuery() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}?k=template")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("k", "request")
                .resolvedUri();

        assertThat(uri.path(), is(UriPath.create("/p")));
        assertThat(uri.query().all("k"), is(List.of("request")));
    }

    @Test
    void requestQueryOverridesRelativeTemplateQuery() {
        ClientUri uri = new FakeClientRequest()
                .uri("example/{path}?k=template")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("k", "request")
                .resolvedUri();

        assertThat(uri.path(), is(UriPath.create("/example/p")));
        assertThat(uri.query().all("k"), is(List.of("request")));
    }

    @Test
    void requestQueryOverridesEncodedTemplateQueryName() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}?a%20b=template")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("a b", "request")
                .resolvedUri();

        assertThat(uri.query().all("a b"), is(List.of("request")));
        assertThat(uri.query().rawValue(), is("a%20b=request"));
    }

    @Test
    void replacingTemplateDoesNotCarryEarlierQueryParam() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .queryParam("old", "value")
                .uri("https://www.example.com/{replacement}")
                .pathParam("replacement", "p")
                .queryParam("new", "value")
                .resolvedUri();

        assertThat(uri.query().contains("old"), is(false));
        assertThat(uri.query().all("new"), is(List.of("value")));
    }

    @Test
    void inheritedQueryIsNotCopiedToAnotherAuthority() {
        ClientUri baseUri = ClientUri.create(URI.create("https://trusted.example/base?access_token=secret"));
        ClientUri uri = new FakeClientRequest(baseUri)
                .uri("https://{host}/path")
                .pathParam("host", "other.example")
                .queryParam("request", "value")
                .resolvedUri();

        assertThat(uri.authority(), is("other.example:443"));
        assertThat(uri.query().contains("access_token"), is(false));
        assertThat(uri.query().all("request"), is(List.of("value")));
    }

    @Test
    void templateQueryResolutionIsRepeatableAndAliasSafe() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("request", "value");

        assertThat(request.resolvedUri().query().rawValue(), is("request=value"));
        assertThat(request.resolvedUri().query().rawValue(), is("request=value"));
        assertThat(request.resolveUri(request.uri()).query().rawValue(), is("request=value"));
        assertThat(request.resolveUri(request.uri()).query().rawValue(), is("request=value"));
    }

    @Test
    void mutableRequestQueryIsAuthoritative() {
        FakeClientRequest removed = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("access_token", "secret");
        removed.uri().writeableQuery().remove("access_token");

        assertThat(removed.resolvedUri().query().contains("access_token"), is(false));

        FakeClientRequest replaced = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("access_token", "secret");
        replaced.uri().writeableQuery().set("access_token", "replacement");

        assertThat(replaced.resolvedUri().query().all("access_token"), is(List.of("replacement")));
    }

    @Test
    void removedRequestQueryParamIsNoLongerTracked() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("https://www.example.com/{path}?access_token=template")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("access_token", "secret");
        request.uri().writeableQuery().remove("access_token");

        assertThat(request.resolvedUri().query().all("access_token"), is(List.of("template")));

        request.uri().writeableQuery().set("access_token", "replacement");

        assertThat(request.resolvedUri().query().all("access_token"), is(List.of("template")));
    }

    @Test
    void failedTemplateResolutionPrunesRemovedRequestQueryParam() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("https://www.example.com/{path}?access_token=template")
                .pathParam("path", "{invalid")
                .skipUriEncoding(true)
                .queryParam("access_token", "secret");
        request.uri().writeableQuery().remove("access_token");

        assertThrows(IllegalArgumentException.class, request::resolvedUri);

        request.uri().writeableQuery().set("access_token", "replacement");
        request.pathParam("path", "p");

        assertThat(request.resolvedUri().query().all("access_token"), is(List.of("template")));
    }

    @Test
    void subsequentQueryParamPrunesRemovedRequestQueryParam() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("https://www.example.com/{path}?access_token=template")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("access_token", "secret");
        request.uri().writeableQuery().remove("access_token");
        request.queryParam("request", "value");
        request.uri().writeableQuery().set("access_token", "replacement");

        ClientUri resolved = request.resolvedUri();
        assertThat(resolved.query().all("access_token"), is(List.of("template")));
        assertThat(resolved.query().all("request"), is(List.of("value")));
    }

    private static final class FakeClientRequest extends ClientRequestBase<FakeClientRequest, HttpClientResponse> {
        private FakeClientRequest() {
            this(ClientUri.create());
        }

        private FakeClientRequest(ClientUri clientUri) {
            super(WebClientConfig.create(), null, "fake", Method.GET, clientUri, Collections.emptyMap());
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
