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

package io.helidon.webserver.tests;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

@RoutingTest
class MatchingPatternRoutingTest extends MatchingPatternBase {

    MatchingPatternRoutingTest(Http1Client client) {
        super(client);
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.addFilter(
                (chain, req, res) -> {
                  assertThat(req.matchingPattern().orElse(null), nullValue());
                  chain.proceed();
                  assertThat(req.matchingPattern().orElse(null), notNullValue());
                })
                .get("/", HANDLER)
                .get("/foo", HANDLER)
                .get("/foo/bar", HANDLER)
                .get("/foo/bar/baz", HANDLER)
                .get("/greet/{name}", HANDLER)
                .get("/greet/{name1}/{}", HANDLER)
                .get("/greet/{name1}/and/{name2}", HANDLER)
                .get("/greet1/*", HANDLER)
                .get("/greet2/greet/*", HANDLER)
                .get("/greet3[/greet]", HANDLER)
                .get("/greet4/*/*", HANDLER)
                .register("/greet-service", new GreetService());
    }
}
