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

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.webserver.spi.TransportBindingFactory;
import io.helidon.webserver.spi.TransportBindingFactoryProvider;

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
    void testLegacyUnixBindAddressConfigFailsWithUdsBindingGuidance() {
        Config config = Config.just("""
                server:
                  bind-address: "unix:/tmp/server.sock"
                """, MediaTypes.APPLICATION_YAML);

        ConfigException failure = assertThrows(ConfigException.class, () -> ListenerConfig.builder()
                .config(config.get("server"))
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

    @Test
    void testConnectionSelectorProvidersDiscoveredByPrototype() {
        ListenerConfig listenerConfig = ListenerConfig.builder().buildPrototype();

        assertThat(listenerConfig.connectionSelectorProviders()
                           .stream()
                           .anyMatch(TestRequiredTransportConnectionSelectorProvider.class::isInstance),
                   is(true));
    }

    @Test
    void testConnectionSelectorProviderDiscoveryCanBeDisabled() {
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .connectionSelectorProvidersDiscoverServices(false)
                .buildPrototype();

        assertThat(listenerConfig.connectionSelectorProviders(), is(List.of()));
    }

    @Test
    void testDeprecatedMaxTcpConnectionsConfiguresMaxConnections() {
        Config config = Config.just("""
                server:
                  max-tcp-connections: 23
                """, MediaTypes.APPLICATION_YAML);

        ListenerConfig listenerConfig = ListenerConfig.builder()
                .config(config.get("server"))
                .buildPrototype();

        assertThat(listenerConfig.maxConnections(), is(23));
    }

    @Test
    void testMaxConnectionsOverridesDeprecatedMaxTcpConnections() {
        Config config = Config.just("""
                server:
                  max-connections: 42
                  max-tcp-connections: 23
                """, MediaTypes.APPLICATION_YAML);

        ListenerConfig listenerConfig = ListenerConfig.builder()
                .config(config.get("server"))
                .buildPrototype();

        assertThat(listenerConfig.maxConnections(), is(42));
    }

    @Test
    void testMaxConnectionsDefaultValueOverridesDeprecatedMaxTcpConnections() {
        Config config = Config.just("""
                server:
                  max-connections: -1
                  max-tcp-connections: 23
                """, MediaTypes.APPLICATION_YAML);

        ListenerConfig listenerConfig = ListenerConfig.builder()
                .config(config.get("server"))
                .buildPrototype();

        assertThat(listenerConfig.maxConnections(), is(-1));
    }

    @Test
    @SuppressWarnings("removal")
    void testDeprecatedMaxTcpConnectionsBuilderConfiguresMaxConnections() {
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .maxTcpConnections(23)
                .buildPrototype();

        assertThat(listenerConfig.maxConnections(), is(23));
    }

    @Test
    @SuppressWarnings("removal")
    void testLaterDeprecatedMaxTcpConnectionsBuilderOverridesMaxConnections() {
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .maxConnections(-1)
                .maxTcpConnections(23)
                .buildPrototype();

        assertThat(listenerConfig.maxConnections(), is(23));
    }

    @Test
    @SuppressWarnings("removal")
    void testLaterMaxConnectionsBuilderOverridesDeprecatedMaxTcpConnections() {
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .maxTcpConnections(23)
                .maxConnections(-1)
                .buildPrototype();

        assertThat(listenerConfig.maxConnections(), is(-1));
    }

    @Test
    @SuppressWarnings("removal")
    void testLaterDeprecatedMaxTcpConnectionsOverridesInheritedMaxConnections() {
        ListenerConfig inherited = ListenerConfig.builder()
                .maxConnections(42)
                .buildPrototype();

        ListenerConfig listenerConfig = ListenerConfig.builder(inherited)
                .maxTcpConnections(23)
                .buildPrototype();

        assertThat(listenerConfig.maxConnections(), is(23));
    }

    @Test
    void testProviderKeyedBindingObjectKeepsDefaultTcp() {
        ListenerConfig listenerConfig = listenerConfig("""
                server:
                  bindings:
                    uds:
                      socket: "/tmp/server.sock"
                """);

        assertThat(bindingDescriptions(listenerConfig), hasItem("tcp/tcp/true"));
        assertThat(bindingDescriptions(listenerConfig), hasItem("uds/uds/true"));
    }

    @Test
    void testExplicitTcpDisableSuppressesDefaultTcp() {
        ListenerConfig listenerConfig = listenerConfig("""
                server:
                  bindings:
                    tcp:
                      enabled: false
                    uds:
                      socket: "/tmp/server.sock"
                """);

        assertThat(bindingDescriptions(listenerConfig), hasItem("tcp/tcp/false"));
        assertThat(bindingDescriptions(listenerConfig), hasItem("uds/uds/true"));
        assertThat(bindingDescriptions(listenerConfig).stream()
                           .filter(description -> description.startsWith("tcp/"))
                           .count(),
                   is(1L));
    }

    @Test
    void testProgrammaticExplicitTcpDisableSuppressesDiscoveredDefault() {
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .addBinding(TcpTransportConfig.builder()
                                    .enabled(false)
                                    .buildPrototype())
                .addBinding(UdsTransportConfig.builder()
                                    .socket(UnixDomainSocketAddress.of("/tmp/server.sock"))
                                    .buildPrototype())
                .buildPrototype();

        assertThat(bindingDescriptions(listenerConfig), hasItem("tcp/tcp/false"));
        assertThat(bindingDescriptions(listenerConfig), hasItem("uds/uds/true"));
        assertThat(bindingDescriptions(listenerConfig).stream()
                           .filter(description -> description.startsWith("tcp/"))
                           .count(),
                   is(1L));
    }

    @Test
    void testBindingListFormIsRejectedBeforeProviderLookup() {
        Config config = Config.just("""
                server:
                  bindings:
                    - type: missing-binding
                """, MediaTypes.APPLICATION_YAML);

        ConfigException failure = assertThrows(ConfigException.class, () -> ListenerConfig.builder()
                .config(config.get("server"))
                .buildPrototype());

        assertThat(failure.getMessage(), containsString("Configured providers at server.bindings"));
        assertThat(failure.getMessage(), containsString("must use object form"));
    }

    @Test
    void testScalarBindingFormIsRejected() {
        Config config = Config.just("""
                server:
                  bindings: uds
                """, MediaTypes.APPLICATION_YAML);

        ConfigException failure = assertThrows(ConfigException.class, () -> ListenerConfig.builder()
                .config(config.get("server"))
                .buildPrototype());

        assertThat(failure.getMessage(), containsString("Configured providers at server.bindings"));
        assertThat(failure.getMessage(), containsString("must use object form"));
    }

    @Test
    void testNestedBindingNameIsRejectedBeforeProviderLookup() {
        ConfigException failure = assertThrows(ConfigException.class, () -> listenerConfig("""
                server:
                  bindings:
                    missing-binding:
                      name: alternate
                """));

        assertThat(failure.getMessage(), containsString("Configured provider \"missing-binding\""));
        assertThat(failure.getMessage(), containsString("must not declare \"name\""));
        assertThat(failure.getMessage(), containsString("provider identity is TYPE_ONLY"));
        assertThat(failure.getMessage(), containsString("provider type is the sole identity"));
    }

    @Test
    void testMismatchedNestedBindingTypeIsRejectedBeforeProviderLookup() {
        ConfigException failure = assertThrows(ConfigException.class, () -> listenerConfig("""
                server:
                  bindings:
                    missing-binding:
                      type: uds
                """));

        assertThat(failure.getMessage(), containsString("Configured provider \"missing-binding\""));
        assertThat(failure.getMessage(), containsString("must not declare \"type\""));
        assertThat(failure.getMessage(), containsString("provider identity is TYPE_ONLY"));
        assertThat(failure.getMessage(), containsString("object key is the provider type"));
    }

    @Test
    void testBuiltInBindingTypeConstants() {
        assertThat(TransportBindingTypes.TCP, is("tcp"));
        assertThat(TransportBindingTypes.UDS, is("uds"));
    }

    @Test
    void testConfiguredProviderKeyMustMatchBindingType() {
        TestTransportBindingProvider provider = new TestTransportBindingProvider();

        ConfigException failure = assertThrows(ConfigException.class,
                                               () -> provider.create(Config.empty(), "alias"));

        assertThat(failure.getMessage(), containsString("provider key \"test-transport\""));
        assertThat(failure.getMessage(), containsString("configured binding type \"alias\""));
    }

    @Test
    void testConfiguredProviderFactoryTypeMustMatchProviderKey() {
        TransportBindingFactoryProvider provider = new TransportBindingFactoryProvider() {
            @Override
            public String configKey() {
                return TestTransportBindingConfig.TYPE;
            }

            @Override
            public TransportBindingFactory create(Config config) {
                return TestTransportBindingConfig.alternate("alternate", true);
            }
        };

        ConfigException failure = assertThrows(ConfigException.class,
                                               () -> provider.create(Config.empty(), TestTransportBindingConfig.TYPE));

        assertThat(failure.getMessage(), containsString("provider key \"test-transport\""));
        assertThat(failure.getMessage(), containsString("factory type \"alternate-test-transport\""));
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

    private static ListenerConfig listenerConfig(String yaml) {
        Config config = Config.just(yaml, MediaTypes.APPLICATION_YAML);
        return ListenerConfig.builder()
                .config(config.get("server"))
                .buildPrototype();
    }
}
