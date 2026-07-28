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

import java.util.List;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.StuckThreadDetectionFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpFeatures;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.SocketHttpClient.statusFromResponse;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class StuckThreadDetectionConnectTest {
    private final SocketHttpClient client;

    StuckThreadDetectionConnectTest(SocketHttpClient client) {
        this.client = client;
    }

    @SetUpFeatures
    static List<ServerFeature> features() {
        return List.of(StuckThreadDetectionFeature.create());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.route(HttpRoute.builder()
                              .methods(Method.CONNECT)
                              .handler((req, res) -> res.header(HeaderValues.CONNECTION_CLOSE)
                                      .status(Status.OK_200)
                                      .send())
                              .build());
    }

    @Test
    void validAuthorityFormReachesHandler() {
        String response = client.sendAndReceive(Method.CONNECT,
                                                "example.com:443",
                                                null,
                                                List.of("Connection: close"));

        assertThat(statusFromResponse(response), is(Status.OK_200));
    }
}
