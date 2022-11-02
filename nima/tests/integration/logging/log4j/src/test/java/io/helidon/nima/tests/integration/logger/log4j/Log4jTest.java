/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.logger.log4j;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http1.Http1Route;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static java.lang.System.getLogger;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;

@ServerTest
class Log4jTest {

    System.Logger logger = getLogger(Log4jTest.class.getName());
    private final Http1Client client;

    public Log4jTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules builder) {
        builder.route(Http1Route.route(Http.Method.GET,
                "/",
                (req, res) -> res.send("Hi")));
    }

    //The server should just work
    @Test
    void testOk() {
        String response = client.method(Http.Method.GET)
                .request()
                .as(String.class);

        logger.log(System.Logger.Level.DEBUG,"Message");

        assertThat(response, is("Hi"));
    }
}
