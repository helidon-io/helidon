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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.http.Http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RouteList}.
 */
public class RouteListTest {

    private static final Handler VOID_HANDLER = (req, res) -> {};

    @Test
    public void testImmutable1() throws Exception {
        RouteList r = new RouteList(Collections.emptyList());
        assertThrows(UnsupportedOperationException.class, () -> {
            r.add(new HandlerRoute(null, VOID_HANDLER));
        });
    }

    @Test
    public void testImmutable2() throws Exception {
        RouteList r = new RouteList(Collections.emptyList());
        assertThrows(UnsupportedOperationException.class, () -> {
            r.remove(new HandlerRoute(null, VOID_HANDLER));
        });
    }

    @Test
    public void testAcceptMethod() throws Exception {
        Collection<Route> routes = new ArrayList<>();
        routes.add(new HandlerRoute(null, VOID_HANDLER, Http.Method.POST, Http.Method.PUT));
        routes.add(new HandlerRoute(null, VOID_HANDLER, Http.Method.POST, Http.Method.DELETE));
        routes.add(new RouteList(CollectionsHelper.listOf(new HandlerRoute(null, VOID_HANDLER, Http.Method.GET, Http.RequestMethod.from("FOO")))));
        routes.add(new HandlerRoute(null, VOID_HANDLER, Http.RequestMethod.from("BAR")));
        RouteList r = new RouteList(routes);
        // assertion
        assertTrue(r.accepts(Http.Method.POST));
        assertTrue(r.accepts(Http.Method.PUT));
        assertTrue(r.accepts(Http.Method.DELETE));
        assertTrue(r.accepts(Http.Method.GET));
        assertTrue(r.accepts(Http.RequestMethod.from("FOO")));
        assertTrue(r.accepts(Http.RequestMethod.from("BAR")));
        assertFalse(r.accepts(Http.Method.OPTIONS));
        assertEquals(6, r.acceptedMethods().size());
    }

    @Test
    public void testAcceptMethodAny() throws Exception {
        Collection<Route> routes = new ArrayList<>();
        routes.add(new HandlerRoute(null, VOID_HANDLER, Http.Method.POST, Http.Method.PUT));
        routes.add(new HandlerRoute(null, VOID_HANDLER, Http.Method.POST, Http.Method.DELETE));
        routes.add(new RouteList(CollectionsHelper.listOf(new HandlerRoute(null, VOID_HANDLER, Http.Method.GET, Http.RequestMethod.from("FOO")),
                                         new HandlerRoute(null, VOID_HANDLER))));
        routes.add(new HandlerRoute(null, VOID_HANDLER, Http.RequestMethod.from("BAR")));
        RouteList r = new RouteList(routes);
        // assertion
        assertTrue(r.accepts(Http.Method.POST));
        assertTrue(r.accepts(Http.Method.PUT));
        assertTrue(r.accepts(Http.Method.DELETE));
        assertTrue(r.accepts(Http.Method.GET));
        assertTrue(r.accepts(Http.RequestMethod.from("FOO")));
        assertTrue(r.accepts(Http.RequestMethod.from("BAR")));
        assertTrue(r.accepts(Http.RequestMethod.from("BAZ")));
        assertTrue(r.accepts(Http.Method.OPTIONS));
        assertEquals(0, r.acceptedMethods().size());
    }
}
