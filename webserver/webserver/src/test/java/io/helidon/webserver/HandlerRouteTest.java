/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.http.Http;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests a {@link HandlerRoute}.
 */
public class HandlerRouteTest {

    private static final Handler VOID_HANDLER = (req, res) -> {};

    @Test
    public void standardMethodRouting() throws Exception {
        HandlerRoute rr = new HandlerRoute(null, VOID_HANDLER, Http.Method.POST, Http.Method.PUT);
        assertThat(rr.accepts(Http.Method.POST), is(true));
        assertThat(rr.accepts(Http.Method.PUT), is(true));
        assertThat(rr.accepts(Http.Method.GET), is(false));
        assertThat(rr.accepts(Http.Method.DELETE), is(false));
        assertThat(rr.accepts(Http.RequestMethod.create("FOO")), is(false));
        assertThat(rr.acceptedMethods().size(), is(2));
    }

    @Test
    public void specialMethodRouting() throws Exception {
        HandlerRoute rr = new HandlerRoute(null, VOID_HANDLER, Http.RequestMethod.create("FOO"));
        assertThat(rr.accepts(Http.Method.GET), is(false));
        assertThat(rr.accepts(Http.Method.POST), is(false));
        assertThat(rr.accepts(Http.RequestMethod.create("FOO")), is(true));
        assertThat(rr.accepts(Http.RequestMethod.create("BAR")), is(false));
        assertThat(rr.acceptedMethods().size(), is(1));
    }

    @Test
    public void combinedMethodRouting() throws Exception {
        HandlerRoute rr = new HandlerRoute(null,
                                           VOID_HANDLER,
                                           Http.Method.POST,
                                           Http.RequestMethod.create("FOO"),
                                           Http.Method.PUT);
        assertThat(rr.accepts(Http.Method.POST), is(true));
        assertThat(rr.accepts(Http.Method.PUT), is(true));
        assertThat(rr.accepts(Http.Method.GET), is(false));
        assertThat(rr.accepts(Http.Method.DELETE), is(false));
        assertThat(rr.accepts(Http.RequestMethod.create("FOO")), is(true));
        assertThat(rr.accepts(Http.RequestMethod.create("BAR")), is(false));
        assertThat(rr.acceptedMethods().size(), is(3));
    }

    @Test
    public void anyMethodRouting() throws Exception {
        HandlerRoute rr = new HandlerRoute(null, VOID_HANDLER);
        assertThat(rr.accepts(Http.Method.POST), is(true));
        assertThat(rr.accepts(Http.Method.GET), is(true));
        assertThat(rr.accepts(Http.RequestMethod.create("FOO")), is(true));
        assertThat(rr.accepts(Http.RequestMethod.create("BAR")), is(true));
        assertThat(rr.acceptedMethods().size(), is(0));
    }

    @Test
    public void anyPathMatcher() throws Exception {
        HandlerRoute rr = new HandlerRoute(null, VOID_HANDLER);
        final String path = "/foo";
        PathMatcher.Result result = rr.match(path);
        assertThat(result.matches(), is(true));
        assertThat(result.params().isEmpty(), is(true));
    }
}
