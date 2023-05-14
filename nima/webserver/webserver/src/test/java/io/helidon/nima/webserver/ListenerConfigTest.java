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

package io.helidon.nima.webserver;

import io.helidon.config.Config;
import org.junit.jupiter.api.Test;

import static io.helidon.nima.webserver.WebServer.DEFAULT_SOCKET_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class ListenerConfigTest {

    @Test
    void testListenerConfig() {
        Config config = Config.create();
        WebServer.Builder webServerBuilder = WebServer.builder().config(config.get("server"));
        ListenerConfiguration.Builder listenerBuilder1 = webServerBuilder.socket(DEFAULT_SOCKET_NAME);
        assertThat(listenerBuilder1.build().writeQueueLength(), is(0));                 // default
        assertThat(listenerBuilder1.build().writeBufferSize(), is(512));                // default
        assertThat(listenerBuilder1.build().gracePeriod().toMillis(), is(500L));        // default
        ListenerConfiguration.Builder listenerBuilder2 = webServerBuilder.socket("other");
        assertThat(listenerBuilder2.build().writeQueueLength(), is(64));
        assertThat(listenerBuilder2.build().writeBufferSize(), is(1024));
    }
}
