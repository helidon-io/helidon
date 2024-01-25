/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp.reactivemessaging;

import java.time.Duration;

import io.helidon.messaging.connectors.mock.MockConnector;
import io.helidon.messaging.connectors.mock.TestConnector;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.microprofile.testing.junit5.AddConfig;

import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.junit.jupiter.api.Test;

@SuppressWarnings("ALL")
class MockSnippets {

    static final Duration TIMEOUT = Duration.ofSeconds(10);

    // tag::snippet_1[]
    @Inject
    @TestConnector
    MockConnector mockConnector;
    // end::snippet_1[]

    void snippet_2() {
        // tag::snippet_2[]
        mockConnector.incoming("my-incoming-channel", String.class) // <1>
                .emit("a", "b", "c");
        // end::snippet_2[]
    }

    void snippet_3() {
        // tag::snippet_3[]
        mockConnector
                .outgoing("my-outgoing-channel", String.class) // <1>
                .awaitData(TIMEOUT, Message::getPayload, "a", "b", "c"); // <2>
        // end::snippet_3[]
    }

    // tag::snippet_4[]
    @HelidonTest
    @DisableDiscovery // <1>
    @AddBean(MockConnector.class) // <2>
    @AddExtension(MessagingCdiExtension.class) // <3>
    @AddConfig(key = "mp.messaging.incoming.test-channel-in.connector", value = MockConnector.CONNECTOR_NAME) // <4>
    @AddConfig(key = "mp.messaging.incoming.test-channel-in.mock-data-type", value = "java.lang.Integer") // <5>
    @AddConfig(key = "mp.messaging.incoming.test-channel-in.mock-data", value = "6,7,8") // <6>
    @AddConfig(key = "mp.messaging.outgoing.test-channel-out.connector", value = MockConnector.CONNECTOR_NAME) // <7>
    public class MessagingTest {

        private static final Duration TIMEOUT = Duration.ofSeconds(15);

        @Inject
        @TestConnector
        private MockConnector mockConnector; // <8>

        @Incoming("test-channel-in")
        @Outgoing("test-channel-out")
        int multiply(int payload) {  // <9>
            return payload * 10;
        }

        @Test
        void testMultiplyChannel() {
            mockConnector.outgoing("test-channel-out", Integer.TYPE) // <10>
                    .awaitPayloads(TIMEOUT, 60, 70, 80);
        }
    }
    // end::snippet_4[]

}
