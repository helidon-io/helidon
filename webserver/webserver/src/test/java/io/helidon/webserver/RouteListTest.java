/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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
import java.util.List;

import io.helidon.common.http.Http;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        routes.add(new RouteList(List.of(new HandlerRoute(null,
                                                          VOID_HANDLER,
                                                          Http.Method.GET,
                                                          Http.RequestMethod.create("FOO")))));
        routes.add(new HandlerRoute(null, VOID_HANDLER, Http.RequestMethod.create("BAR")));
        RouteList r = new RouteList(routes);
        // assertion
        assertThat(r.accepts(Http.Method.POST), is(true));
        assertThat(r.accepts(Http.Method.PUT), is(true));
        assertThat(r.accepts(Http.Method.DELETE), is(true));
        assertThat(r.accepts(Http.Method.GET), is(true));
        assertThat(r.accepts(Http.RequestMethod.create("FOO")), is(true));
        assertThat(r.accepts(Http.RequestMethod.create("BAR")), is(true));
        assertThat(r.accepts(Http.Method.OPTIONS), is(false));
        assertThat(r.acceptedMethods().size(), is(6));
    }

    @Test
    public void testAcceptMethodAny() throws Exception {
        Collection<Route> routes = new ArrayList<>();
        routes.add(new HandlerRoute(null, VOID_HANDLER, Http.Method.POST, Http.Method.PUT));
        routes.add(new HandlerRoute(null, VOID_HANDLER, Http.Method.POST, Http.Method.DELETE));
        routes.add(new RouteList(List.of(new HandlerRoute(null,
                                                                           VOID_HANDLER,
                                                                           Http.Method.GET,
                                                                           Http.RequestMethod.create("FOO")),
                                                          new HandlerRoute(null, VOID_HANDLER))));
        routes.add(new HandlerRoute(null, VOID_HANDLER, Http.RequestMethod.create("BAR")));
        RouteList r = new RouteList(routes);
        // assertion
        assertThat(r.accepts(Http.Method.POST), is(true));
        assertThat(r.accepts(Http.Method.PUT), is(true));
        assertThat(r.accepts(Http.Method.DELETE), is(true));
        assertThat(r.accepts(Http.Method.GET), is(true));
        assertThat(r.accepts(Http.RequestMethod.create("FOO")), is(true));
        assertThat(r.accepts(Http.RequestMethod.create("BAR")), is(true));
        assertThat(r.accepts(Http.RequestMethod.create("BAZ")), is(true));
        assertThat(r.accepts(Http.Method.OPTIONS), is(true));
        assertThat(r.acceptedMethods().size(), is(0));
    }
}
