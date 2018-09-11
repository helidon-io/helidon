/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.CollectionsHelper;
import io.helidon.webserver.spi.BareRequest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                                                CollectionsHelper.mapOf("a", "va", "b", "vb", "var", "1"));
        assertEquals("/foo/bar/baz", path.toString());
        assertEquals("/foo/bar/baz", path.absolute().toString());
        assertEquals(path.param("a"), "va");
        assertEquals(path.param("b"), "vb");
        assertEquals(path.param("var"), "1");
        assertThat(path.segments(), contains("foo", "bar", "baz"));
        // Sub path
        path = Request.Path.create(path,
                                   "/bar/baz",
                                   CollectionsHelper.mapOf("c", "vc", "var", "2"));
        assertEquals("/bar/baz", path.toString());
        assertEquals("/foo/bar/baz", path.absolute().toString());
        assertEquals(path.param("c"), "vc");
        assertEquals(path.param("var"), "2");
        assertNull(path.param("a"));
        assertEquals(path.absolute().param("a"), "va");
        assertEquals(path.absolute().param("b"), "vb");
        assertEquals(path.absolute().param("var"), "2");
        // Sub Sub Path
        path = Request.Path.create(path,
                                   "/baz",
                                   CollectionsHelper.mapOf("d", "vd", "a", "a2"));
        assertEquals("/baz", path.toString());
        assertEquals("/foo/bar/baz", path.absolute().toString());
        assertEquals(path.param("d"), "vd");
        assertEquals(path.param("a"), "a2");
        assertNull(path.param("c"));
        assertEquals(path.absolute().param("a"), "a2");
        assertEquals(path.absolute().param("b"), "vb");
        assertEquals(path.absolute().param("c"), "vc");
        assertEquals(path.absolute().param("var"), "2");
    }

    @Test
    public void queryEncodingTest() throws Exception {
        BareRequest mock = mock(BareRequest.class);
        when(mock.getUri()).thenReturn(new URI("http://localhost:123/one/two?a=b%26c=d&e=f&e=g&h=x%63%23e%3c#a%20frag%23ment"));

        Request request = new RequestTestStub(mock, mock(WebServer.class));

        assertThat("The query string must remain encoded otherwise no-one could tell whether a '&' was really a '&' or '%26'",
                          request.query(),
                          is("a=b%26c=d&e=f&e=g&h=x%63%23e%3c"));
        assertThat(request.fragment(), is("a frag#ment"));

        assertThat(request.queryParams().toMap(), hasEntry(is("e"), hasItems("f", "g")));
        assertThat(request.queryParams().toMap(), hasEntry(is("h"), hasItem("xc#e<")));
        assertThat(request.queryParams().toMap(), hasEntry(is("a"), hasItem("b&c=d")));
    }
}
