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
package io.helidon.webclient.http1;

import io.helidon.http.Header;
import io.helidon.http.HeaderValues;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class Http1ClientRequestImplTest {

    @Test
    void testUpgradeHeaders() {
        Header req = HeaderValues.create("Upgrade", "websocket");
        Header res = HeaderValues.create("Upgrade", "WebSocket");       // camel case
        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(true));
    }

    @Test
    void testUpgradeHeadersMany() {
        Header req = HeaderValues.create("Upgrade", "websocket", "h2c");
        Header res = HeaderValues.create("Upgrade", "WebSocket");       // camel case
        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(true));
    }

    @Test
    void testUpgradeHeadersFail() {
        Header req = HeaderValues.create("Upgrade", "websocket", "http3");
        Header res = HeaderValues.create("Upgrade", "h2c");
        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(false));
    }
}
