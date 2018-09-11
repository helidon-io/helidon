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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests a {@link HandlerRoute}.
 */
public class HandlerRouteTest {

    private static final Handler VOID_HANDLER = (req, res) -> {};

    @Test
    public void standardMethodRouting() throws Exception {
        HandlerRoute rr = new HandlerRoute(null, VOID_HANDLER, Http.Method.POST, Http.Method.PUT);
        assertTrue(rr.accepts(Http.Method.POST));
        assertTrue(rr.accepts(Http.Method.PUT));
        assertFalse(rr.accepts(Http.Method.GET));
        assertFalse(rr.accepts(Http.Method.DELETE));
        assertFalse(rr.accepts(Http.RequestMethod.from("FOO")));
        assertEquals(2, rr.acceptedMethods().size());
    }

    @Test
    public void specialMethodRouting() throws Exception {
        HandlerRoute rr = new HandlerRoute(null, VOID_HANDLER, Http.RequestMethod.from("FOO"));
        assertFalse(rr.accepts(Http.Method.GET));
        assertFalse(rr.accepts(Http.Method.POST));
        assertTrue(rr.accepts(Http.RequestMethod.from("FOO")));
        assertFalse(rr.accepts(Http.RequestMethod.from("BAR")));
        assertEquals(1, rr.acceptedMethods().size());
    }

    @Test
    public void combinedMethodRouting() throws Exception {
        HandlerRoute rr = new HandlerRoute(null, VOID_HANDLER, Http.Method.POST, Http.RequestMethod.from("FOO"), Http.Method.PUT);
        assertTrue(rr.accepts(Http.Method.POST));
        assertTrue(rr.accepts(Http.Method.PUT));
        assertFalse(rr.accepts(Http.Method.GET));
        assertFalse(rr.accepts(Http.Method.DELETE));
        assertTrue(rr.accepts(Http.RequestMethod.from("FOO")));
        assertFalse(rr.accepts(Http.RequestMethod.from("BAR")));
        assertEquals(3, rr.acceptedMethods().size());
    }

    @Test
    public void anyMethodRouting() throws Exception {
        HandlerRoute rr = new HandlerRoute(null, VOID_HANDLER);
        assertTrue(rr.accepts(Http.Method.POST));
        assertTrue(rr.accepts(Http.Method.GET));
        assertTrue(rr.accepts(Http.RequestMethod.from("FOO")));
        assertTrue(rr.accepts(Http.RequestMethod.from("BAR")));
        assertEquals(0, rr.acceptedMethods().size());
    }

    @Test
    public void anyPathMatcher() throws Exception {
        HandlerRoute rr = new HandlerRoute(null, VOID_HANDLER);
        final String path = "/foo";
        PathMatcher.Result result = rr.match(path);
        assertTrue(result.matches());
        assertTrue(result.params().isEmpty());
    }
}
