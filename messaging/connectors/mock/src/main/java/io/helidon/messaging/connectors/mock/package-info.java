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

/**
 * Helidon messaging mock connector for testing purposes.
 *
 * Mock connector can be used for testing Helidon messaging
 * without the need of connection to actual messaging broker.
 *
 * <pre>{@code
 * @HelidonTest
 * @DisableDiscovery
 * @AddBean(MockConnector.class)
 * @AddExtension(MessagingCdiExtension.class)
 * //Use mock connector as an upstream for channel test-channel-incoming
 * @AddConfig(key = "mp.messaging.incoming.test-channel-incoming", value = MockConnector.CONNECTOR_NAME)
 * //mock-data-type is optional, defaults to String.class
 * @AddConfig(key = "mp.messaging.incoming.test-channel-incoming.mock-data-type", value = "java.lang.Long")
 * //mock-data is optional, can generate data to the connected channel right after start
 * @AddConfig(key = "mp.messaging.incoming.test-channel-incoming.mock-data", value = "9,10,11,12")
 * //Use mock connector as a downstream for channel test-channel-outgoing
 * @AddConfig(key = "mp.messaging.outoging.test-channel-outgoing.connector", value = MockConnector.CONNECTOR_NAME)
 * public class MockConnectorTest {
 *
 *     @Inject
 *     @TestConnector
 *     private MockConnector mockConnector;
 *
 *     @Incoming("test-channel-incoming")
 *     @Outgoing("test-channel-outgoing")
 *     public long sampleProcessorMethod(long payload) {
 *         return payload;
 *     }
 *
 *     @Test
 *     void sampleTestMethod() {
 *         mockConnector.outgoing("test-channel-outgoing", Long.TYPE)
 *                 .awaitMessage(TIMEOUT, longMessage -> longMessage.getPayload() == 12L)
 *                 .assertPayloads(9L, 10L, 11L, 12L);
 *     }
 * }
 * }</pre>
 */
package io.helidon.messaging.connectors.mock;
