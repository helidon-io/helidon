/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ListenerConfigTest {

    @Test
    void testListenerConfig() {
        Config config = Config.create();
        var webServerConfig = WebServer.builder().config(config.get("server")).buildPrototype();
        assertThat(webServerConfig.writeQueueLength(), is(0));         // default
        assertThat(webServerConfig.writeBufferSize(), is(4096));       // default
        assertThat(webServerConfig.shutdownGracePeriod().toMillis(), is(500L));   // default
        ListenerConfig listenerConfig2 = webServerConfig.sockets().get("other");
        assertThat(listenerConfig2.writeQueueLength(), is(64));
        assertThat(listenerConfig2.writeBufferSize(), is(1024));
    }


    // Verify that value of server2.shutdown-grace-period is present in ListenerConfiguration instance
    // of the default socket.
    @Test
    void tesDefaulttListenerConfigFromConfigFile() {
        Config config = Config.create();
        var webServerConfig = WebServer.builder().config(config.get("server2")).buildPrototype();
        assertThat(webServerConfig.shutdownGracePeriod().toMillis(), is(1000L));
    }

    // Verify that value of server3.sockets[name="grace"].shutdown-grace-period is present
    // in ListenerConfiguration instance of the "grace" socket.
    @Test
    void testSpecificListenerConfigFromConfigFile() {
        Config config = Config.create();
        var webServerConfig = WebServer.builder().config(config.get("server3")).buildPrototype();
        ListenerConfig listenerConfig = webServerConfig.sockets().get("grace");
        assertThat(listenerConfig.shutdownGracePeriod().toMillis(), is(2000L));
    }

    @Test
    void testEnableProxyProtocolConfig() {
        Config config = Config.create();

        // default is false in default socket
        var webServerConfig = WebServer.builder().config(config.get("server")).buildPrototype();
        assertThat(webServerConfig.enableProxyProtocol(), is(false));
        ListenerConfig otherConfig = webServerConfig.sockets().get("other");
        assertThat(otherConfig.enableProxyProtocol(), is(false));

        // set to true in default socket
        var webServerConfig2 = WebServer.builder().config(config.get("server2")).buildPrototype();
        assertThat(webServerConfig2.enableProxyProtocol(), is(true));

        // set to true in non-default socket
        var webServerConfig3 = WebServer.builder().config(config.get("server3")).buildPrototype();
        assertThat(webServerConfig3.enableProxyProtocol(), is(false));
        ListenerConfig graceConfig = webServerConfig3.sockets().get("grace");
        assertThat(graceConfig.enableProxyProtocol(), is(true));
    }
}
