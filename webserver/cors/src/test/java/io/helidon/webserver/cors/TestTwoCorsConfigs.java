/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.cors;

import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class TestTwoCorsConfigs extends AbstractCorsTest {
    private final Http1Client client;

    TestTwoCorsConfigs(Http1Client client) {
        this.client = client;
    }

    @Override
    String contextRoot() {
        return TestUtil.OTHER_GREETING_PATH;
    }

    @Override
    Http1Client client() {
        return client;
    }

    @Override
    String fooOrigin() {
        return "http://otherfoo.bar";
    }

    @Override
    String fooHeader() {
        return "X-otherfoo";
    }

    @Test
    void test1PreFlightAllowedOriginOtherGreeting() {
        HttpClientResponse response = runTest1PreFlightAllowedOrigin();

        Status status = response.status();
        assertThat(status.code(), is(Status.FORBIDDEN_403.code()));
        assertThat(status.reasonPhrase(), is("Forbidden"));
    }

}
