/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.net.UnixDomainSocketAddress;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.webserver.spi.TransportBindingFactory;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ListenerConfigTest {
    private static final String DISCOVERED_TEST_BINDING = TestTransportBindingConfig.TYPE
            + "/" + TestTransportBindingConfig.TYPE + "/false";

    @Test
    void testUnixBindAddressFailsWithUdsBindingGuidance() {
        ConfigException failure = assertThrows(ConfigException.class, () -> ListenerConfig.builder()
                .bindAddress(UnixDomainSocketAddress.of("/tmp/server.sock"))
                .buildPrototype());

        assertThat(failure.getMessage(), containsString("bindings.uds.socket"));
        assertThat(failure.getMessage(), containsString("bindings.tcp.enabled=false"));
    }

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

    @Test
    void testBindingDiscoveryEnabledByDefault() {
        ListenerConfig listenerConfig = ListenerConfig.builder().buildPrototype();

        assertThat(bindingDescriptions(listenerConfig), hasItem(DISCOVERED_TEST_BINDING));
    }

    @Test
    void testBindingDiscoveryCanBeDisabled() {
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .bindingsDiscoverServices(false)
                .buildPrototype();

        assertThat(bindingDescriptions(listenerConfig), not(hasItem(DISCOVERED_TEST_BINDING)));
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

    @Test
    void testSniVirtualHostConfig() {
        Config config = Config.create();
        var webServerConfig = WebServer.builder().config(config.get("server4")).buildPrototype();

        assertThat(webServerConfig.sni().missing(), is(SniSelectionPolicy.REJECT));
        assertThat(webServerConfig.sni().unmatched(), is(SniSelectionPolicy.REJECT));
        assertThat(webServerConfig.sni().authorityMismatch(), is(SniAuthorityPolicy.ALLOW));
        assertThat(webServerConfig.sni().fallbackAuthority(), is(SniAuthorityPolicy.ALLOW));
        assertThat(webServerConfig.virtualHosts().size(), is(1));

        VirtualHostConfig virtualHost = webServerConfig.virtualHosts().getFirst();
        assertThat(virtualHost.host(), is("Api.Example.COM."));
        assertThat(virtualHost.tls().enabled(), is(true));
        assertThat(virtualHost.tls().prototype().enabledCipherSuites(),
                   is(List.of("TLS_AES_128_GCM_SHA256")));
    }

    private static List<String> bindingDescriptions(ListenerConfig listenerConfig) {
        return listenerConfig.bindings()
                .stream()
                .map(ListenerConfigTest::bindingDescription)
                .toList();
    }

    private static String bindingDescription(TransportBindingFactory binding) {
        return binding.type() + "/" + binding.name() + "/" + binding.enabled();
    }
}
