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

package io.helidn.webserver.validation;

import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.validation.Validators;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest
public class WebServerValidationTest {
    private final Http1Client client;

    public WebServerValidationTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    public static void setUp(HttpRules rules) {
        rules.get("/fail", (req, res) -> {
                    Validators.checkFalse(true);
                    res.send("bad");
                })
                .get("/ok", (req, res) -> {
                    Validators.checkFalse(false);
                    res.send("ok");
                });
    }

    @Test
    public void testBadRequest() {
        try (var response = client.get("/fail")
                .request()) {

            // we must get a bad request, as the validation failed
            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            // and there should be no content length (so we do not replay bad values to request)
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }
    }

    @Test
    public void testGoodRequest() {
        ClientResponseTyped<String> response = client.get("/ok")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("ok"));
    }
}
