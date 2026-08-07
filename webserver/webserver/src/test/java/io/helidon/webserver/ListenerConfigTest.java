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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ListenerConfigTest {

    @Test
    void testProxyProtocolIsDefaultMethod() throws NoSuchMethodException {
        assertThat(ListenerConfig.class.getMethod("proxyProtocol").isDefault(), is(true));
    }

    @Test
    void testUnixBindAddressKeepsConfiguredPath() {
        Config config = Config.just(ConfigSources.create(Map.of("bind-address", "unix:/tmp/server.sock")));

        var socketAddress = (UnixDomainSocketAddress) ListenerConfig.create(config)
                .bindAddress()
                .orElseThrow();

        assertThat(socketAddress.getPath(), is(Path.of("/tmp/server.sock")));
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
    @SuppressWarnings("removal")
    void testEnableProxyProtocolConfig() {
        Config config = Config.create();

        // default is false in default socket
        var webServerConfig = WebServer.builder().config(config.get("server")).buildPrototype();
        assertThat(webServerConfig.enableProxyProtocol(), is(false));
        assertThat(webServerConfig.proxyProtocol().isEmpty(), is(true));
        ListenerConfig otherConfig = webServerConfig.sockets().get("other");
        assertThat(otherConfig.enableProxyProtocol(), is(false));
        assertThat(otherConfig.proxyProtocol().isEmpty(), is(true));

        // set to true in default socket
        var webServerConfig2 = WebServer.builder().config(config.get("server2")).buildPrototype();
        assertThat(webServerConfig2.enableProxyProtocol(), is(true));
        assertThat(webServerConfig2.proxyProtocol().orElseThrow().trustedProxies().orElseThrow().test("anything"), is(true));

        // set to true in non-default socket
        var webServerConfig3 = WebServer.builder().config(config.get("server3")).buildPrototype();
        assertThat(webServerConfig3.enableProxyProtocol(), is(false));
        ListenerConfig graceConfig = webServerConfig3.sockets().get("grace");
        assertThat(graceConfig.enableProxyProtocol(), is(true));
        assertThat(graceConfig.proxyProtocol().orElseThrow().trustedProxies().orElseThrow().test("anything"), is(true));
    }

    @Test
    void testEnableProxyProtocolRequiresNewConfig() {
        Config config = Config.just(ConfigSources.create(Map.of("enable-proxy-protocol", "true")));

        ConfigException exception = assertThrows(ConfigException.class, () -> ListenerConfig.create(config));

        assertThat(exception.getMessage(), containsString("proxy-protocol.trusted-proxies"));
    }

    @Test
    @SuppressWarnings("removal")
    void testLegacyProxyProtocolEnablementRequiresNewConfig() {
        ListenerConfig legacyConfig = (ListenerConfig) Proxy.newProxyInstance(
                ListenerConfig.class.getClassLoader(),
                new Class<?>[] {ListenerConfig.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("enableProxyProtocol")) {
                        return true;
                    }
                    return InvocationHandler.invokeDefault(proxy, method, args);
                });

        ConfigException exception = assertThrows(ConfigException.class, legacyConfig::proxyProtocol);

        assertThat(exception.getMessage(), containsString("proxy-protocol.trusted-proxies"));
    }

    @Test
    void testProxyProtocolRequiresTrustedProxies() {
        Config config = Config.just(ConfigSources.create(Map.of("proxy-protocol.enabled", "true")));

        ConfigException exception = assertThrows(ConfigException.class, () -> ListenerConfig.create(config));

        assertThat(exception.getMessage(), containsString("proxy-protocol.trusted-proxies"));
    }

    @Test
    @SuppressWarnings("removal")
    void testDisabledProxyProtocolDoesNotRequireTrustedProxies() {
        Config config = Config.just(ConfigSources.create(Map.of("proxy-protocol.enabled", "false")));
        ListenerConfig listenerConfig = ListenerConfig.create(config);

        assertThat(listenerConfig.enableProxyProtocol(), is(false));
        assertThat(listenerConfig.proxyProtocol().orElseThrow().enabled(), is(false));
    }
}
