/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.quic;

import java.time.Duration;
import java.util.List;

import io.helidon.common.Weighted;
import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.quic.QuicVersion;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ListenerTlsContext;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.TransportBindingTypes;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.quic.spi.QuicSubProtocolConfig;
import io.helidon.webserver.quic.spi.QuicSubProtocolProvider;
import io.helidon.webserver.quic.spi.QuicSubProtocolRuntime;
import io.helidon.webserver.spi.PortTransportBinding;
import io.helidon.webserver.spi.TransportBindingFactory;
import io.helidon.webserver.spi.TransportConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuicTransportConfigTest {
    @Test
    void defaultFactoryUsesDefaults() {
        QuicTransportConfig config = QuicTransportConfig.create();

        assertThat(config.enabled(), is(true));
        assertThat(config.required(), is(false));
        assertThat(config.retryEnabled(), is(false));
        assertThat(config.handshakeTimeout(), is(Duration.ofSeconds(10)));
        assertThat(config.maxPendingHandshakes(), is(256));
        assertThat(config.alpnPreference(), is(List.of()));
        assertThat(config.quic().availableVersions(), is(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1)));
        assertThat(config.quic().idleTimeout(), is(Duration.ofSeconds(30)));
    }

    @Test
    void usesQuicAsSoleBindingIdentity() {
        QuicTransportBindingProvider provider = new QuicTransportBindingProvider();
        TransportBindingFactory factory = QuicTransportBindingFactory.create(QuicTransportConfig.create());

        assertThat(provider.configKey(), is(QuicTransportBindingTypes.QUIC));
        assertThat(factory.type(), is(QuicTransportBindingTypes.QUIC));
        assertThat(factory.name(), is(QuicTransportBindingTypes.QUIC));
        assertThat(provider.weight(), is(Weighted.DEFAULT_WEIGHT - 10));
    }

    @Test
    void addBindingReturnsSameListenerBuilder() {
        TransportConfig config = QuicTransportConfig.create();
        ListenerConfig.Builder listenerBuilder = ListenerConfig.builder()
                .bindingsDiscoverServices(false);

        ListenerConfig.Builder result = listenerBuilder.addBinding(config);

        assertThat(result, sameInstance(listenerBuilder));
        assertThat(listenerBuilder.bindings().stream().map(TransportBindingFactory::type).toList(),
                   contains(QuicTransportBindingTypes.QUIC));
        assertThat(listenerBuilder.buildPrototype()
                           .bindings()
                           .stream()
                           .map(TransportBindingFactory::type)
                           .toList(),
                   contains(TransportBindingTypes.TCP, QuicTransportBindingTypes.QUIC));
    }

    @Test
    void addBindingReturnsSameWebServerBuilder() {
        TransportConfig config = QuicTransportConfig.create();
        WebServerConfig.Builder serverBuilder = WebServer.builder()
                .bindingsDiscoverServices(false);

        WebServerConfig.Builder result = serverBuilder.addBinding(config);

        assertThat(result, sameInstance(serverBuilder));
        assertThat(serverBuilder.bindings().stream().map(TransportBindingFactory::type).toList(),
                   contains(QuicTransportBindingTypes.QUIC));
    }

    @Test
    void listenerBuilderRejectsNullTransportConfig() {
        ListenerConfig.Builder builder = ListenerConfig.builder();

        assertThrows(NullPointerException.class, () -> builder.addBinding((TransportConfig) null));
    }

    @Test
    void webServerBuilderRejectsNullTransportConfig() {
        WebServerConfig.Builder builder = WebServer.builder();

        assertThrows(NullPointerException.class, () -> builder.addBinding((TransportConfig) null));
    }

    @Test
    void explicitBindingPreservesDisabledAndRequiredSettings() {
        QuicTransportConfig config = QuicTransportConfig.builder()
                .enabled(false)
                .required(true)
                .buildPrototype();
        ListenerConfig listener = ListenerConfig.builder()
                .bindingsDiscoverServices(false)
                .addBinding(config)
                .buildPrototype();
        TransportBindingFactory factory = listener.bindings()
                .stream()
                .filter(binding -> QuicTransportBindingTypes.QUIC.equals(binding.type()))
                .findFirst()
                .orElseThrow();

        assertThat(factory.type(), is(QuicTransportBindingTypes.QUIC));
        assertThat(factory.enabled(), is(false));
        assertThat(factory.required(), is(true));
    }

    @Test
    void explicitBindingPreservesQuicStreamLimitBeforeOpeningSocket() {
        var protocol = mock(QuicSubProtocolConfig.class);
        when(protocol.type()).thenReturn("test-tuning");
        when(protocol.name()).thenReturn("test-tuning");
        when(protocol.enabled()).thenReturn(true);
        var runtime = mock(QuicSubProtocolRuntime.class);
        when(runtime.minimumPeerUniStreams()).thenReturn(2L);
        @SuppressWarnings("unchecked")
        QuicSubProtocolProvider<QuicSubProtocolConfig> provider = mock(QuicSubProtocolProvider.class);
        when(provider.configKey()).thenReturn("test-tuning");
        when(provider.protocolConfigType()).thenReturn(QuicSubProtocolConfig.class);

        QuicTransportConfig config = QuicTransportConfig.builder()
                .quic(quic -> quic.maxUniStreams(1))
                .subProtocolProvidersDiscoverServices(false)
                .addSubProtocolProvider(provider)
                .buildPrototype();
        ListenerConfig listener = ListenerConfig.builder()
                .bindingsDiscoverServices(false)
                .protocolsDiscoverServices(false)
                .addBinding(config)
                .addProtocol(protocol)
                .buildPrototype();
        TransportBindingFactory factory = listener.bindings()
                .stream()
                .filter(binding -> QuicTransportBindingTypes.QUIC.equals(binding.type()))
                .findFirst()
                .orElseThrow();
        var context = mock(TransportBindingContext.class);
        var listenerContext = mock(ListenerContext.class);
        var listenerTls = mock(ListenerTlsContext.class);
        var tls = mock(Tls.class);
        when(context.listenerContext()).thenReturn(listenerContext);
        when(context.listenerTls()).thenReturn(listenerTls);
        when(listenerContext.config()).thenReturn(listener);
        when(listenerTls.tls()).thenReturn(tls);
        when(tls.enabled()).thenReturn(true);
        when(provider.create(context, protocol)).thenReturn(runtime);
        PortTransportBinding binding = (PortTransportBinding) factory.create(context);

        try {
            ConfigException failure = assertThrows(ConfigException.class, binding::start);

            assertThat(failure.getMessage(),
                       containsString("requires QuicConfig.maxUniStreams to be at least 2, but the binding configures 1"));
            assertThat(binding.port(), is(-1));
        } finally {
            binding.stop(Duration.ZERO);
        }
    }

    @Test
    void configuredBindingsStartTcpBeforeQuicRegardlessOfObjectOrder() {
        assertThat(configuredBindingTypes(TransportBindingTypes.TCP, QuicTransportBindingTypes.QUIC),
                   contains(TransportBindingTypes.TCP, QuicTransportBindingTypes.QUIC));
        assertThat(configuredBindingTypes(QuicTransportBindingTypes.QUIC, TransportBindingTypes.TCP),
                   contains(TransportBindingTypes.TCP, QuicTransportBindingTypes.QUIC));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void discoversAndAcceptsInjectedSubProtocolProviders() {
        QuicTransportConfig discovered = QuicTransportConfig.create();

        assertThat(discovered.subProtocolProviders().stream().map(QuicSubProtocolProvider::configKey).toList(),
                   hasItems("first-test", "second-test", "test-echo"));

        QuicSubProtocolProvider provider = discovered.subProtocolProviders().getFirst();
        QuicTransportConfig injected = QuicTransportConfig.builder()
                .subProtocolProvidersDiscoverServices(false)
                .addSubProtocolProvider(provider)
                .buildPrototype();

        assertThat(injected.subProtocolProviders(), contains(sameInstance(provider)));
    }

    @Test
    void builderMapsTransportSettingsToQuicConfig() {
        QuicTransportConfig config = QuicTransportConfig.builder()
                .retryEnabled(true)
                .handshakeTimeout(Duration.ofSeconds(7))
                .maxPendingHandshakes(17)
                .alpnPreference(List.of("test-beta", "test-alpha"))
                .quic(q -> q
                        .idleTimeout(Duration.ofSeconds(45))
                        .socketReceiveBufferSize(65_536)
                        .socketSendBufferSize(32_768)
                        .maxUdpPayloadSize(1_350)
                        .maxHandshakeMessageSize(16_384)
                        .initialMaxData(8_192)
                        .maxBidiStreams(4))
                .buildPrototype();

        var quicConfig = config.quic();
        assertThat(config.retryEnabled(), is(true));
        assertThat(config.handshakeTimeout(), is(Duration.ofSeconds(7)));
        assertThat(config.maxPendingHandshakes(), is(17));
        assertThat(config.alpnPreference(), is(List.of("test-beta", "test-alpha")));
        assertThat(quicConfig.idleTimeout(), is(Duration.ofSeconds(45)));
        assertThat(quicConfig.socketReceiveBufferSize().orElseThrow(), is(65_536));
        assertThat(quicConfig.socketSendBufferSize().orElseThrow(), is(32_768));
        assertThat(quicConfig.maxUdpPayloadSize(), is(1_350));
        assertThat(quicConfig.maxHandshakeMessageSize(), is(16_384));
        assertThat(quicConfig.initialMaxData(), is(8_192L));
        assertThat(quicConfig.maxBidiStreams(), is(4L));
    }

    @Test
    void providerLoadsTransportFactoryFromBindingConfigRoot() {
        QuicTransportBindingProvider provider = new QuicTransportBindingProvider();
        Config bindingConfig = Config.create(ConfigSources.create(ObjectNode.builder()
                                                                   .addObject(provider.configKey(), ObjectNode.builder()
                                                                           .addValue("enabled", "false")
                                                                           .addValue("required", "true")
                                                                           .addValue("retry-enabled", "true")
                                                                           .addValue("handshake-timeout", "PT7S")
                                                                           .addValue("max-pending-handshakes", "17")
                                                                           .addList("alpn-preference",
                                                                                    ListNode.builder()
                                                                                            .addValue("test-beta")
                                                                                            .addValue("test-alpha")
                                                                                            .build())
                                                                           .addValue("idle-timeout", "PT45S")
                                                                           .addValue("socket-receive-buffer-size", "65536")
                                                                           .addValue("socket-send-buffer-size", "32768")
                                                                           .addValue("max-udp-payload-size", "1350")
                                                                           .addValue("max-handshake-message-size", "16384")
                                                                           .addValue("initial-max-data", "8192")
                                                                           .addValue("max-bidi-streams", "4")
                                                                           .build())
                                                                   .build()))
                .get(provider.configKey());
        TransportBindingFactory factory = provider.create(bindingConfig);

        assertThat(factory.type(), is(provider.configKey()));
        assertThat(factory.name(), is(provider.configKey()));
        assertThat(factory.enabled(), is(false));
        assertThat(factory.required(), is(true));

        QuicTransportConfig config = QuicTransportConfig.create(bindingConfig);
        assertThat(config.retryEnabled(), is(true));
        assertThat(config.handshakeTimeout(), is(Duration.ofSeconds(7)));
        assertThat(config.maxPendingHandshakes(), is(17));
        assertThat(config.alpnPreference(), is(List.of("test-beta", "test-alpha")));
        var quicConfig = config.quic();
        assertThat(quicConfig.idleTimeout(), is(Duration.ofSeconds(45)));
        assertThat(quicConfig.socketReceiveBufferSize().orElseThrow(), is(65_536));
        assertThat(quicConfig.socketSendBufferSize().orElseThrow(), is(32_768));
        assertThat(quicConfig.maxUdpPayloadSize(), is(1_350));
        assertThat(quicConfig.maxHandshakeMessageSize(), is(16_384));
        assertThat(quicConfig.initialMaxData(), is(8_192L));
        assertThat(quicConfig.maxBidiStreams(), is(4L));
    }

    @Test
    void rejectsDuplicateAlpnPreference() {
        IllegalArgumentException builderDuplicate = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTransportConfig.builder()
                        .alpnPreference(List.of("test", "test"))
                        .buildPrototype());
        Config externalDuplicate = Config.create(ConfigSources.create(
                ObjectNode.builder()
                        .addList("alpn-preference",
                                 ListNode.builder()
                                         .addValue("test")
                                         .addValue("test")
                                         .build())
                        .build()));
        IllegalArgumentException configuredDuplicate = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTransportConfig.create(externalDuplicate));

        assertThat(builderDuplicate.getMessage(),
                   is("alpnPreference must not contain duplicate ALPN identifier: \"test\""));
        assertThat(configuredDuplicate.getMessage(),
                   is("alpnPreference must not contain duplicate ALPN identifier: \"test\""));
    }

    @Test
    void rejectsInvalidHandshakeAdmissionConfiguration() {
        IllegalArgumentException zeroTimeout = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTransportConfig.builder().handshakeTimeout(Duration.ZERO).buildPrototype());
        IllegalArgumentException negativeTimeout = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTransportConfig.builder().handshakeTimeout(Duration.ofSeconds(-1)).buildPrototype());
        IllegalArgumentException excessiveTimeout = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTransportConfig.builder()
                        .handshakeTimeout(Duration.ofSeconds(Long.MAX_VALUE))
                        .buildPrototype());
        IllegalArgumentException zeroPending = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTransportConfig.builder().maxPendingHandshakes(0).buildPrototype());
        IllegalArgumentException negativePending = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTransportConfig.builder().maxPendingHandshakes(-1).buildPrototype());
        Config externalZeroTimeout = Config.create(ConfigSources.create(ObjectNode.builder()
                                                                                 .addValue("handshake-timeout", "PT0S")
                                                                                 .build()));
        Config externalZeroPending = Config.create(ConfigSources.create(ObjectNode.builder()
                                                                                 .addValue("max-pending-handshakes", "0")
                                                                                 .build()));
        IllegalArgumentException configuredZeroTimeout = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTransportConfig.create(externalZeroTimeout));
        IllegalArgumentException configuredZeroPending = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTransportConfig.create(externalZeroPending));

        assertThat(zeroTimeout.getMessage(), is("handshakeTimeout must be positive: PT0S"));
        assertThat(negativeTimeout.getMessage(), is("handshakeTimeout must be positive: PT-1S"));
        assertThat(excessiveTimeout.getMessage(),
                   is("handshakeTimeout must fit in nanoseconds: PT2562047788015215H30M7S"));
        assertThat(zeroPending.getMessage(), is("maxPendingHandshakes must be greater than 0: 0"));
        assertThat(negativePending.getMessage(), is("maxPendingHandshakes must be greater than 0: -1"));
        assertThat(configuredZeroTimeout.getMessage(), is("handshakeTimeout must be positive: PT0S"));
        assertThat(configuredZeroPending.getMessage(), is("maxPendingHandshakes must be greater than 0: 0"));
    }

    private static List<String> configuredBindingTypes(String first, String second) {
        Config listenerConfig = Config.create(ConfigSources.create(ObjectNode.builder()
                                                                             .addObject("bindings",
                                                                                        ObjectNode.builder()
                                                                                                .addObject(first,
                                                                                                           ObjectNode.builder()
                                                                                                                   .addValue("enabled",
                                                                                                                             "true")
                                                                                                                   .build())
                                                                                                .addObject(second,
                                                                                                           ObjectNode.builder()
                                                                                                                   .addValue("enabled",
                                                                                                                             "true")
                                                                                                                   .build())
                                                                                                .build())
                                                                             .build()));
        return ListenerConfig.builder()
                .config(listenerConfig)
                .buildPrototype()
                .bindings()
                .stream()
                .map(TransportBindingFactory::type)
                .filter(it -> TransportBindingTypes.TCP.equals(it) || QuicTransportBindingTypes.QUIC.equals(it))
                .toList();
    }
}
