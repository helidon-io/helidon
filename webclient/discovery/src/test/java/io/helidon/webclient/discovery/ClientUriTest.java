/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webclient.discovery;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriPathSegment;
import io.helidon.webclient.api.ClientUri;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.nullValue;

class ClientUriTest {

    private ClientUriTest() {
        super();
    }

    @Test
    void testResolve() {
        ClientUri c = ClientUri.create();
        assertThat(c.scheme(), is("http"));
        assertThat(c.host(), is("localhost")); // !
        assertThat(c.port(), is(80)); // !
        assertThat(c.path().path(), is("/")); // !
        c.resolve(URI.create("path"));
        assertThat(c.scheme(), is("http"));
        assertThat(c.host(), is("localhost"));
        assertThat(c.port(), is(80));
        assertThat(c.path().path(), is("/path"));
    }

    @Test
    void testPathAndQueryBehavior() {
        ClientUri c = ClientUri.create(URI.create("http://example.com:81/a/b?c=d"));
        assertThat(c.path().rawPath(), is("/a/b"));
        assertThat(c.query().rawValue(), is("c=d"));
        c.path("/e/f");
        assertThat(c.path().rawPath(), is("/e/f"));
        assertThat(c.query().rawValue(), is("c=d"));
        c.path("/g/h?i=j");
        assertThat(c.path().rawPath(), is("/g/h"));
        assertThat(c.query().rawValue(), is("c=d&i=j")); // !
    }

    @Test
    void testEmptyAndNullPathConcerns() throws URISyntaxException {
        URI uri = URI.create("http://example.com"); // note: no trailing slash
        assertThat(uri.getRawPath(), isEmptyString()); // interesting; a documented "deviation" according to URI javadocs

        ClientUri c = ClientUri.create(uri);
        assertThat(c.path().rawPath(), is("/")); // !

        c = ClientUri.create();
        c.path("");
        c.resolve(uri);
        assertThat(c.path().rawPath(), is("/")); // !

        uri = URI.create("http://example.com/a/b");
        assertThat(uri.getRawPath(), is("/a/b")); // not a/b

        c = ClientUri.create(uri);
        assertThat(c.path().rawPath(), is(uri.getRawPath()));

        uri = new URI("http",
                      null, // user info
                      "example.com",
                      -1,
                      null, // path; will become "" internally because this is a hierarchical URI
                      null, // query
                      null); // fragment
        assertThat(uri.getRawPath(), isEmptyString()); // !
        URI uri2 = new URI("http",
                           null, // user info
                           "example.com",
                           -1,
                           "", // path; could have been (equivalently) null
                           null, // query
                           null); // fragment
        assertThat(uri2.getRawPath(), isEmptyString());
        assertThat(uri, is(uri2));
    }

    @Test
    void testEmpty() {
        // ClientUri#create() documentation says it creates an "empty" ClientUri. Let's see what that means.
        ClientUri emptyClientUri = ClientUri.create();
        assertThat(emptyClientUri.scheme(), is("http")); // !
        assertThat(emptyClientUri.host(), is("localhost")); // !
        assertThat(emptyClientUri.port(), is(80)); // !
        assertThat(emptyClientUri.path().rawPath(), is("/")); // !
    }

    @Test
    void testAuthorityless() {
        ClientUri c = ClientUri.create(URI.create("frob:///boo://bash"));
        assertThat(c.scheme(), is("frob"));
        assertThat(c.host(), is("localhost")); // !
        assertThat(c.port(), is(80)); // !
        assertThat(c.path().path(), is("/boo:/bash")); // ?
        assertThat(c.path().rawPath(), is("/boo://bash"));
    }

    @Test
    void testOpaqueUris() {
        URI uri = URI.create("a:b:c");
        assertThat(uri.getScheme(), is("a"));
        assertThat(uri.getSchemeSpecificPart(), is("b:c"));
        assertThat(uri.getHost(), is(nullValue()));
        assertThat(uri.getPort(), is(-1));
        assertThat(uri.getAuthority(), is(nullValue()));
        assertThat(uri.getRawPath(), is(nullValue()));

        ClientUri c = ClientUri.create(uri);
        assertThat(c.scheme(), is("a"));
        assertThat(c.host(), is("localhost")); // !
        assertThat(c.port(), is(80)); // !
        assertThat(c.authority(), is("localhost:80")); // !
        assertThat(c.path().rawPath(), is("/")); // ! note that b:c has disappeared

        uri = c.toUri();
        assertThat(uri.getScheme(), is("a"));
        assertThat(uri.getSchemeSpecificPart(), is("//localhost:80/")); // !
    }

    @Test
    void testClientUriPathSegmentsBehavior() {
        ClientUri c = ClientUri.create(URI.create("http://example.com:80")); // no trailing slash
        UriPath up = c.path();
        assertThat(up.rawPath(), is("/")); // !
        List<UriPathSegment> segments = up.segments();
        assertThat(segments, empty());

        c = ClientUri.create(URI.create("http://example.com:80/")); // trailing slash
        up = c.path();
        segments = up.segments();
        assertThat(segments, empty());

        c = ClientUri.create(URI.create("http://example.com:80/;a=b"));
        up = c.path();
        segments = up.segments();
        assertThat(segments, hasSize(2)); // !

        assertThat(segments.get(0).rawValue(), isEmptyString()); // !
        Parameters p = segments.get(0).matrixParameters();
        assertThat(p.component(), is("uri/matrix")); // ?
        Collection<String> names = p.names();
        assertThat(names, empty());

        assertThat(segments.get(1).rawValue(), is(";a=b"));
        p = segments.get(1).matrixParameters();
        assertThat(p.component(), is("uri/matrix")); // ?
        names = p.names();
        assertThat(names, hasSize(1));
        assertThat(names.iterator().next(), is("a"));

        c = ClientUri.create(URI.create("http://example.com:80/x"));
        up = c.path();
        segments = up.segments();
        assertThat(segments, hasSize(2)); // !

        assertThat(segments.get(0).rawValue(), isEmptyString()); // !
        assertThat(segments.get(1).rawValue(), is("x"));

        c = ClientUri.create(URI.create("http://example.com:80/x;a=b"));
        up = c.path();
        segments = up.segments();
        assertThat(segments, hasSize(2)); // !

        assertThat(segments.get(0).rawValue(), isEmptyString()); // !
        p = segments.get(0).matrixParameters();
        assertThat(p.component(), is("uri/matrix")); // ?
        names = p.names();
        assertThat(names, empty());

        assertThat(segments.get(1).rawValue(), is("x;a=b")); // !
        p = segments.get(1).matrixParameters();
        assertThat(p.component(), is("uri/matrix")); // ?
        names = p.names();
        assertThat(names, hasSize(1));
        assertThat(names.iterator().next(), is("a"));
    }

    @Test
    void testClientUriResolvePathBehavior() {
        URI u = URI.create("http://example.com:80/a");
        assertThat(u.resolve("/b"), // note that the path is absolute
                   is(URI.create("http://example.com:80/b")));
        assertThat(u.resolve("b"), // note that the path is relative
                   is(URI.create("http://example.com:80/b")));

        ClientUri c = ClientUri.create(u);
        assertThat(c.resolvePath("/b").toUri(), // note that the path is absolute
                   is(URI.create("http://example.com:80/a/b"))); // ! OK?

        c = ClientUri.create(u);
        assertThat(c.resolvePath("b").toUri(), // note that the path is relative
                   is(URI.create("http://example.com:80/a/b"))); // ! OK?
    }

}
