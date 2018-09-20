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

import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Method.DELETE;
import static io.helidon.common.http.Http.Method.GET;
import static io.helidon.common.http.Http.Method.HEAD;
import static io.helidon.common.http.Http.Method.OPTIONS;
import static io.helidon.common.http.Http.Method.POST;
import static io.helidon.common.http.Http.Method.PUT;
import static io.helidon.common.http.Http.Method.TRACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RouteListRoutingRules}.
 */
public class RouteListRoutingRulesTest {

    private static final Handler VOID_HANDLER = (req, res) -> {};

    @Test
    public void simpleRouting() throws Exception {
        RouteList routes = new RouteListRoutingRules()
                .get(VOID_HANDLER)
                .get("/foo", VOID_HANDLER)
                .get(PathPattern.compile("/bar"), VOID_HANDLER)
                .post(VOID_HANDLER)
                .post("/foo", VOID_HANDLER)
                .post(PathPattern.compile("/bar"), VOID_HANDLER)
                .delete(VOID_HANDLER)
                .delete("/foo", VOID_HANDLER)
                .delete(PathPattern.compile("/bar"), VOID_HANDLER)
                .put(VOID_HANDLER)
                .put("/foo", VOID_HANDLER)
                .put(PathPattern.compile("/bar"), VOID_HANDLER)
                .trace(VOID_HANDLER)
                .trace("/foo", VOID_HANDLER)
                .trace(PathPattern.compile("/bar"), VOID_HANDLER)
                .options(VOID_HANDLER)
                .options("/foo", VOID_HANDLER)
                .options(PathPattern.compile("/bar"), VOID_HANDLER)
                .head(VOID_HANDLER)
                .head("/foo", VOID_HANDLER)
                .head(PathPattern.compile("/bar"), VOID_HANDLER)
                .aggregate()
                .getRouteList();
        assertNotNull(routes);
        assertEquals(7*3, routes.size());
        assertEquals(7, routes.acceptedMethods().size());

        assertTrue(routes.get(0).accepts(GET));
        assertFalse(routes.get(0).accepts(POST));
        assertTrue(routes.get(1).accepts(GET));
        assertFalse(routes.get(1).accepts(PUT));
        assertTrue(routes.get(2).accepts(GET));
        assertFalse(routes.get(2).accepts(DELETE));

        assertTrue(routes.get(3).accepts(POST));
        assertFalse(routes.get(3).accepts(GET));
        assertTrue(routes.get(4).accepts(POST));
        assertFalse(routes.get(4).accepts(GET));
        assertTrue(routes.get(5).accepts(POST));
        assertFalse(routes.get(5).accepts(GET));

        assertTrue(routes.get(6).accepts(DELETE));
        assertFalse(routes.get(6).accepts(GET));
        assertTrue(routes.get(7).accepts(DELETE));
        assertFalse(routes.get(7).accepts(GET));
        assertTrue(routes.get(8).accepts(DELETE));
        assertFalse(routes.get(8).accepts(GET));

        assertTrue(routes.get(9).accepts(PUT));
        assertFalse(routes.get(9).accepts(GET));
        assertTrue(routes.get(10).accepts(PUT));
        assertFalse(routes.get(10).accepts(GET));
        assertTrue(routes.get(11).accepts(PUT));
        assertFalse(routes.get(11).accepts(GET));

        assertTrue(routes.get(12).accepts(TRACE));
        assertFalse(routes.get(12).accepts(GET));
        assertTrue(routes.get(13).accepts(TRACE));
        assertFalse(routes.get(13).accepts(GET));
        assertTrue(routes.get(14).accepts(TRACE));
        assertFalse(routes.get(14).accepts(GET));

        assertTrue(routes.get(15).accepts(OPTIONS));
        assertFalse(routes.get(15).accepts(GET));
        assertTrue(routes.get(16).accepts(OPTIONS));
        assertFalse(routes.get(16).accepts(GET));
        assertTrue(routes.get(17).accepts(OPTIONS));
        assertFalse(routes.get(17).accepts(GET));

        assertTrue(routes.get(18).accepts(HEAD));
        assertFalse(routes.get(18).accepts(GET));
        assertTrue(routes.get(19).accepts(HEAD));
        assertFalse(routes.get(19).accepts(GET));
        assertTrue(routes.get(20).accepts(HEAD));
        assertFalse(routes.get(20).accepts(GET));

        PathMatcher.PrefixResult result = routes.prefixMatch("/any");
        assertNotNull(result);
        assertTrue(result.matches());
        assertEquals("/any", result.remainingPart());
    }

    @Test
    public void anyRouting() throws Exception {
        RouteList routes = new RouteListRoutingRules()
                .get(VOID_HANDLER)
                .any("/foo", VOID_HANDLER)
                .delete("/foo", VOID_HANDLER)
                .post("/bar", VOID_HANDLER)
                .post("/bar", (req, res) -> {}, (req, res) -> {})
                .aggregate()
                .getRouteList();
        assertNotNull(routes);
        assertEquals(6, routes.size());
        assertEquals(0, routes.acceptedMethods().size());
        assertTrue(routes.get(0).accepts(GET));
        assertFalse(routes.get(0).accepts(POST));
        assertTrue(routes.get(1).accepts(POST));
        assertTrue(routes.get(1).accepts(GET));
        assertTrue(routes.get(2).accepts(DELETE));
        assertTrue(routes.get(3).accepts(POST));
        assertTrue(routes.get(4).accepts(POST));
        assertTrue(routes.get(5).accepts(POST));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void nestedRouting() throws Exception {
        RouteList routes = new RouteListRoutingRules()
                .post("/foo", VOID_HANDLER)
                .register(c -> c.get(VOID_HANDLER)
                                .post("/foo", VOID_HANDLER))
                .delete("/foo", VOID_HANDLER)
                .register("/bar", c -> c.delete(VOID_HANDLER)
                                                     .put(VOID_HANDLER))
                .aggregate()
                .getRouteList();
        assertNotNull(routes);
        assertEquals(5, routes.size());
        assertEquals(4, routes.acceptedMethods().size());
        assertTrue(routes.get(0).accepts(POST));
        assertFalse(routes.get(0).accepts(GET));
        assertTrue(routes.get(1).accepts(GET));
        assertFalse(routes.get(1).accepts(POST));
        assertTrue(routes.get(2).accepts(POST));
        assertFalse(routes.get(2).accepts(GET));
        assertTrue(routes.get(3).accepts(DELETE));
        assertFalse(routes.get(3).accepts(GET));
        assertTrue(routes.get(4) instanceof RouteList);
        assertTrue(routes.get(4).accepts(DELETE));
        assertTrue(routes.get(4).accepts(PUT));
        assertFalse(routes.get(4).accepts(GET));
    }

    @Test
    public void emptyNestedRouting() throws Exception {
        RouteList routes = new RouteListRoutingRules()
                .get(VOID_HANDLER)
                .register("/foo", c -> {})
                .post("/bar", VOID_HANDLER)
                .aggregate()
                .getRouteList();
        assertNotNull(routes);
        assertEquals(2, routes.size());
        assertEquals(2, routes.acceptedMethods().size());
    }
}
