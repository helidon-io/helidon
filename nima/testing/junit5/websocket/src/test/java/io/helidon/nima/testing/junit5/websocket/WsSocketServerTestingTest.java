/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.testing.junit5.websocket;

import io.helidon.nima.testing.junit5.webserver.DirectClient;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.websocket.client.WsClient;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

@ServerTest
class WsSocketServerTestingTest extends WsSocketAbstractTestingTest {
    WsSocketServerTestingTest(Http1Client httpClient, WsClient wsClient) {
        super(httpClient, wsClient);

        assertThat(httpClient, not(instanceOf(DirectClient.class)));
        assertThat(wsClient, not(instanceOf(DirectWsClient.class)));
    }
}
