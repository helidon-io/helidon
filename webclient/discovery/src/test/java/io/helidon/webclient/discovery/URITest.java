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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class URITest {

    private URITest() {
        super();
    }

    @Test
    void testRelativize() {
        // Two absolute (non-schemeless) hierarchical URIs that differ only in their paths.
        URI u0 = URI.create("http://example.com");
        assertThat(u0.getPath(), isEmptyString());
        URI u1 = URI.create("http://example.com/");
        assertThat(u1.getPath(), is("/"));

        // Relativize http://example.com/ against http://example.com.
        //
        // "...a new relative [schemeless] hierarchical URI is constructed...with a path component computed by removing
        // this URI's [u0] path [""] from the beginning of the given URI's [u1] path ["/"]."
        URI u2 = u0.relativize(u1);

        // See
        // https://stackoverflow.com/questions/79787723/why-does-relativizing-a-java-net-uri-with-as-its-path-against-one-with-as
        //
        // See (maybe) https://bugs.openjdk.org/browse/JDK-6523089
        assertThat(u2.getPath(), isEmptyString()); // !

        // (For completeness)
        assertThat(u2.getScheme(), nullValue());
        assertThat(u2.getHost(), nullValue());

        // "if the path ["/"] of this URI [u1] is not a prefix of the path [""] of the given URI [u0], then the given
        // URI [u0] is returned."
        u2 = u1.relativize(u0);
        assertThat(u2, sameInstance(u0));
    }

    @Test
    void testRelativizeEquals() {
        URI u0 = URI.create("http://example.com");
        assertThat(u0.getPath(), isEmptyString());
        URI u1 = URI.create("http://example.com");
        assertThat(u1.getPath(), isEmptyString());
        assertThat(u0, is(u1));
        assertThat(u0.relativize(u1).getPath(), isEmptyString());
        assertThat(u1.relativize(u0).getPath(), isEmptyString());
    }

    @Test
    void testResolveAbsolutes() {
        // Two absolute (non-schemeless) hierarchical URIs that differ only in their paths, so neither can be resolved
        // against the other.
        URI u0 = URI.create("http://example.com");
        URI u1 = URI.create("http://example.com/");
        assertThat(u0.resolve(u1), sameInstance(u1));
        assertThat(u1.resolve(u0), sameInstance(u0));
    }

    @Test
    void testResolveAgainstEmptyPath() {
        URI u0 = URI.create("http://example.com");
        assertThat(u0.getPath(), isEmptyString());
        URI u1 = URI.create("a");
        URI u2 = u0.resolve(u1);
        assertThat(u2.getPath(), is("/a")); // note the added /
        u1 = URI.create("/a");
        u2 = u0.resolve(u1);
        assertThat(u2.getPath(), is("/a"));
        u0 = URI.create("http://example.com/");
        assertThat(u0.getPath(), is("/"));
        u1 = URI.create("a");
        u2 = u0.resolve(u1);
        assertThat(u2.getPath(), is("/a"));

        // (Can't resolve an absolute (non-schemeless) hierarchical URI (u0) against a relative URI (u1).)
        u2 = u1.resolve(u0);
        assertThat(u2, sameInstance(u0));
    }

}
