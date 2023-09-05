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

package io.helidon.webclient.websocket;

import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WsClientTest {
    @Test
    void testSchemeValidation() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                             () -> WsClient.builder()
                                     .baseUri("test://localhost:8888/")
                                     .shareConnectionCache(false)
                                     .build()
                                     .connect("/whatever", new WsListener() {
                                         @Override
                                         public void onMessage(WsSession session, String text, boolean last) {
                                             //not used
                                         }
                                     }),
                             "Should have failed because of invalid scheme.");

        assertThat(ex.getMessage(), startsWith("Not supported scheme test"));
    }
}
