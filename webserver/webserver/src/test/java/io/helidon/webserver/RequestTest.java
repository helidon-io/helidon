/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.net.URI;
import java.util.Map;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link Request} and {@link Request.Path}.
 */
public class RequestTest {

    @Test
    public void createPathTest() throws Exception {
        Request.Path path = Request.Path.create(null,
                                                "/foo/bar/baz",
                                                Map.of("a", "va", "b", "vb", "var", "1"));
        assertThat(path.toString(), is("/foo/bar/baz"));
        assertThat(path.absolute().toString(), is("/foo/bar/baz"));
        assertThat("va", is(path.param("a")));
        assertThat("vb", is(path.param("b")));
        assertThat("1", is(path.param("var")));
        assertThat(path.segments(), contains("foo", "bar", "baz"));
        // Sub path
        path = Request.Path.create(path,
                                   "/bar/baz",
                                   Map.of("c", "vc", "var", "2"));
        assertThat(path.toString(), is("/bar/baz"));
        assertThat(path.absolute().toString(), is("/foo/bar/baz"));
        assertThat("vc", is(path.param("c")));
        assertThat("2", is(path.param("var")));
        assertThat(path.param("a"), nullValue());
        assertThat("va", is(path.absolute().param("a")));
        assertThat("vb", is(path.absolute().param("b")));
        assertThat("2", is(path.absolute().param("var")));
        // Sub Sub Path
        path = Request.Path.create(path,
                                   "/baz",
                                   Map.of("d", "vd", "a", "a2"));
        assertThat(path.toString(), is("/baz"));
        assertThat(path.absolute().toString(), is("/foo/bar/baz"));
        assertThat("vd", is(path.param("d")));
        assertThat("a2", is(path.param("a")));
        assertThat(path.param("c"), nullValue());
        assertThat("a2", is(path.absolute().param("a")));
        assertThat("vb", is(path.absolute().param("b")));
        assertThat("vc", is(path.absolute().param("c")));
        assertThat("2", is(path.absolute().param("var")));
    }

    @Test
    public void queryEncodingTest() throws Exception {
        BareRequest mock = mock(BareRequest.class);
        when(mock.uri()).thenReturn(new URI("http://localhost:123/one/two?a=b%26c=d&e=f&e=g&h=x%63%23e%3c#a%20frag%23ment"));
        when(mock.bodyPublisher()).thenReturn(Single.empty());
        WebServer webServer = mock(WebServer.class);
        Request request =Contexts.runInContext(Context.create(), () -> new RequestTestStub(mock, webServer));
        assertThat("The query string must remain encoded otherwise no-one could tell whether a '&' was really a '&' or '%26'",
                          request.query(),
                          is("a=b%26c=d&e=f&e=g&h=x%63%23e%3c"));
        assertThat(request.fragment(), is("a frag#ment"));

        assertThat(request.queryParams().toMap(), hasEntry(is("e"), hasItems("f", "g")));
        assertThat(request.queryParams().toMap(), hasEntry(is("h"), hasItem("xc#e<")));
        assertThat(request.queryParams().toMap(), hasEntry(is("a"), hasItem("b&c=d")));
    }
}
