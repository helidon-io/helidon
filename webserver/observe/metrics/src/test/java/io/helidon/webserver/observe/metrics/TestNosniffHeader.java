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

package io.helidon.webserver.observe.metrics;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RoutingTest
class TestNosniffHeader {

    private final WebClient client;

    TestNosniffHeader(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void setUpRoute(HttpRouting.Builder routing) {
    }

    @Test
    void testNosniffHeader() {
        try (HttpClientResponse response = client.get("/observe/metrics").accept(MediaTypes.APPLICATION_JSON)
                .request()) {
            assertThat("Response", response.status().code(), is(Status.OK_200.code()));
            assertThat("Response headers",
                       response.headers(),
                       HttpHeaderMatcher.hasHeader(HeaderNames.X_CONTENT_TYPE_OPTIONS, "nosniff"));
        }
    }

}
