/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

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
                .routeList();
        assertThat(routes, notNullValue());
        assertThat(routes.size(), is(7*3));
        assertThat(routes.acceptedMethods().size(), is(7));

        assertThat(routes.get(0).accepts(GET), is(true));
        assertThat(routes.get(0).accepts(POST), is(false));
        assertThat(routes.get(1).accepts(GET), is(true));
        assertThat(routes.get(1).accepts(PUT), is(false));
        assertThat(routes.get(2).accepts(GET), is(true));
        assertThat(routes.get(2).accepts(DELETE), is(false));

        assertThat(routes.get(3).accepts(POST), is(true));
        assertThat(routes.get(3).accepts(GET), is(false));
        assertThat(routes.get(4).accepts(POST), is(true));
        assertThat(routes.get(4).accepts(GET), is(false));
        assertThat(routes.get(5).accepts(POST), is(true));
        assertThat(routes.get(5).accepts(GET), is(false));

        assertThat(routes.get(6).accepts(DELETE), is(true));
        assertThat(routes.get(6).accepts(GET), is(false));
        assertThat(routes.get(7).accepts(DELETE), is(true));
        assertThat(routes.get(7).accepts(GET), is(false));
        assertThat(routes.get(8).accepts(DELETE), is(true));
        assertThat(routes.get(8).accepts(GET), is(false));

        assertThat(routes.get(9).accepts(PUT), is(true));
        assertThat(routes.get(9).accepts(GET), is(false));
        assertThat(routes.get(10).accepts(PUT), is(true));
        assertThat(routes.get(10).accepts(GET), is(false));
        assertThat(routes.get(11).accepts(PUT), is(true));
        assertThat(routes.get(11).accepts(GET), is(false));

        assertThat(routes.get(12).accepts(TRACE), is(true));
        assertThat(routes.get(12).accepts(GET), is(false));
        assertThat(routes.get(13).accepts(TRACE), is(true));
        assertThat(routes.get(13).accepts(GET), is(false));
        assertThat(routes.get(14).accepts(TRACE), is(true));
        assertThat(routes.get(14).accepts(GET), is(false));

        assertThat(routes.get(15).accepts(OPTIONS), is(true));
        assertThat(routes.get(15).accepts(GET), is(false));
        assertThat(routes.get(16).accepts(OPTIONS), is(true));
        assertThat(routes.get(16).accepts(GET), is(false));
        assertThat(routes.get(17).accepts(OPTIONS), is(true));
        assertThat(routes.get(17).accepts(GET), is(false));

        assertThat(routes.get(18).accepts(HEAD), is(true));
        assertThat(routes.get(18).accepts(GET), is(false));
        assertThat(routes.get(19).accepts(HEAD), is(true));
        assertThat(routes.get(19).accepts(GET), is(false));
        assertThat(routes.get(20).accepts(HEAD), is(true));
        assertThat(routes.get(20).accepts(GET), is(false));

        PathMatcher.PrefixResult result = routes.prefixMatch("/any");
        assertThat(result, notNullValue());
        assertThat(result.matches(), is(true));
        assertThat(result.remainingPart(), is("/any"));
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
                .routeList();
        assertThat(routes, notNullValue());
        assertThat(routes.size(), is(6));
        assertThat(routes.acceptedMethods().size(), is(0));
        assertThat(routes.get(0).accepts(GET), is(true));
        assertThat(routes.get(0).accepts(POST), is(false));
        assertThat(routes.get(1).accepts(POST), is(true));
        assertThat(routes.get(1).accepts(GET), is(true));
        assertThat(routes.get(2).accepts(DELETE), is(true));
        assertThat(routes.get(3).accepts(POST), is(true));
        assertThat(routes.get(4).accepts(POST), is(true));
        assertThat(routes.get(5).accepts(POST), is(true));
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
                .routeList();
        assertThat(routes, notNullValue());
        assertThat(routes.size(), is(5));
        assertThat(routes.acceptedMethods().size(), is(4));
        assertThat(routes.get(0).accepts(POST), is(true));
        assertThat(routes.get(0).accepts(GET), is(false));
        assertThat(routes.get(1).accepts(GET), is(true));
        assertThat(routes.get(1).accepts(POST), is(false));
        assertThat(routes.get(2).accepts(POST), is(true));
        assertThat(routes.get(2).accepts(GET), is(false));
        assertThat(routes.get(3).accepts(DELETE), is(true));
        assertThat(routes.get(3).accepts(GET), is(false));
        assertThat(routes.get(4) instanceof RouteList, is(true));
        assertThat(routes.get(4).accepts(DELETE), is(true));
        assertThat(routes.get(4).accepts(PUT), is(true));
        assertThat(routes.get(4).accepts(GET), is(false));
    }

    @Test
    public void emptyNestedRouting() throws Exception {
        RouteList routes = new RouteListRoutingRules()
                .get(VOID_HANDLER)
                .register("/foo", c -> {})
                .post("/bar", VOID_HANDLER)
                .aggregate()
                .routeList();
        assertThat(routes, notNullValue());
        assertThat(routes.size(), is(2));
        assertThat(routes.acceptedMethods().size(), is(2));
    }
}
