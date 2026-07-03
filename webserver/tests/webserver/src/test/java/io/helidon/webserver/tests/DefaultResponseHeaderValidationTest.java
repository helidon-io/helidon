/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class DefaultResponseHeaderValidationTest {
    private static final String HEADER_NAME = "Valid-Header-Name";
    private static final String INJECTED_HEADER = "Injected-Header: injected";

    private final SocketHttpClient socketHttpClient;

    DefaultResponseHeaderValidationTest(SocketHttpClient socketHttpClient) {
        this.socketHttpClient = socketHttpClient;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/header", (req, res) -> {
            res.headers().add(HeaderValues.create(HeaderNames.create(HEADER_NAME), "safe\r\n" + INJECTED_HEADER));
            res.send("body");
        });
    }

    @Test
    void defaultResponseHeaderValidationRejectsLineBreaks() {
        String response = socketHttpClient.sendAndReceive(Method.GET, "/header", null);

        assertThat(SocketHttpClient.statusFromResponse(response), is(Status.INTERNAL_SERVER_ERROR_500));
        assertThat(response, not(containsString("\n" + INJECTED_HEADER + "\n")));
    }
}
