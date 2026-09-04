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

package io.helidon.webserver.http3;

import java.util.Map;
import java.util.Set;

import io.helidon.common.Size;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.quic.QuicTransportBindingTypes;
import io.helidon.webserver.sse.SseSinkProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class Http3ConfigTest {
    @Test
    void http3ProviderIsDisabledWhenConfigIsAbsent() {
        Http3Config config = new Http3QuicProtocolProvider().create(Config.empty(), Http3BlueprintSupport.CONFIG_NAME);

        assertThat(config.type(), is(Http3BlueprintSupport.CONFIG_NAME));
        assertThat(config.name(), is(Http3BlueprintSupport.CONFIG_NAME));
        assertThat(config.enabled(), is(false));
        assertThat(config.maxHeadersSize(), is(16_384));
        assertThat(config.maxFieldSectionSize(), is(8192L));
        assertThat(config.qpackMaxTableCapacity(), is(-1L));
        assertThat(config.qpackBlockedStreams(), is(-1L));
        assertThat(config.responseDispatchWindowSize(), is(65_536));
        assertThat(config.sendErrorDetails(), is(false));
        assertThat(config.maxBufferedEntitySize().toBytes(), is(65_536L));
        assertThat(config.validateRequestHeaders(), is(true));
        assertThat(config.validateResponseHeaders(), is(true));
        assertThat(config.validatePath(), is(true));
        assertThat(config.log().receiveLog(), is(true));
        assertThat(config.log().sendLog(), is(true));
        assertThat(config.transportBindingTypes(), is(Set.of()));
    }

    @Test
    void http3DefaultFactoryUsesDefaults() {
        Http3Config config = Http3Config.create();

        assertThat(config.type(), is(Http3BlueprintSupport.CONFIG_NAME));
        assertThat(config.name(), is(Http3BlueprintSupport.CONFIG_NAME));
        assertThat(config.enabled(), is(true));
        assertThat(config.maxHeadersSize(), is(16_384));
        assertThat(config.maxFieldSectionSize(), is(8192L));
        assertThat(config.qpackMaxTableCapacity(), is(-1L));
        assertThat(config.qpackBlockedStreams(), is(-1L));
        assertThat(config.responseDispatchWindowSize(), is(65_536));
        assertThat(config.sendErrorDetails(), is(false));
        assertThat(config.maxBufferedEntitySize().toBytes(), is(65_536L));
        assertThat(config.validateRequestHeaders(), is(true));
        assertThat(config.validateResponseHeaders(), is(true));
        assertThat(config.validatePath(), is(true));
        assertThat(config.transportBindingTypes(), is(Set.of(QuicTransportBindingTypes.QUIC)));
    }

    @Test
    void http3DiscoversSinkProviders() {
        Http3Config config = Http3Config.create();

        assertThat(config.sinkProviders().stream().anyMatch(SseSinkProvider.class::isInstance), is(true));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void http3AllowsExplicitSinkProviderInjectionWithoutDiscovery() {
        SinkProvider provider = mock(SinkProvider.class);

        Http3Config config = Http3Config.builder()
                .sinkProvidersDiscoverServices(false)
                .addSinkProvider(provider)
                .buildPrototype();

        assertThat(config.sinkProviders(), contains(sameInstance(provider)));
    }

    @Test
    void http3BuilderAllowsExplicitSettings() {
        Http3Config config = Http3Config.builder()
                .maxHeadersSize(12_345)
                .maxFieldSectionSize(16_384)
                .qpackMaxTableCapacity(4_096)
                .qpackBlockedStreams(16)
                .responseDispatchWindowSize(32_768)
                .sendErrorDetails(true)
                .maxBufferedEntitySize(Size.create(8, Size.Unit.KIB))
                .validateRequestHeaders(false)
                .validateResponseHeaders(false)
                .validatePath(false)
                .log(it -> it.receiveLog(false)
                        .sendLog(false)
                        .loggerName("io.helidon.http3.test"))
                .buildPrototype();

        assertThat(config.maxHeadersSize(), is(12_345));
        assertThat(config.maxFieldSectionSize(), is(16_384L));
        assertThat(config.qpackMaxTableCapacity(), is(4_096L));
        assertThat(config.qpackBlockedStreams(), is(16L));
        assertThat(config.responseDispatchWindowSize(), is(32_768));
        assertThat(config.sendErrorDetails(), is(true));
        assertThat(config.maxBufferedEntitySize().toBytes(), is(8_192L));
        assertThat(config.validateRequestHeaders(), is(false));
        assertThat(config.validateResponseHeaders(), is(false));
        assertThat(config.validatePath(), is(false));
        assertThat(config.log().receiveLog(), is(false));
        assertThat(config.log().sendLog(), is(false));
        assertThat(config.log().loggerName().orElseThrow(), is("io.helidon.http3.test"));
    }

    @Test
    void http3LoadsFromProtocolConfig() {
        Http3QuicProtocolProvider provider = new Http3QuicProtocolProvider();
        Http3Config http3Config = provider.create(
                Config.create(ConfigSources.create(ObjectNode.builder()
                                                    .addObject(provider.configKey(), ObjectNode.builder()
                                                            .addValue("max-headers-size", "12345")
                                                            .addValue("max-field-section-size", "16384")
                                                            .addValue("qpack-max-table-capacity", "4096")
                                                            .addValue("qpack-blocked-streams", "32")
                                                            .addValue("response-dispatch-window-size", "32768")
                                                            .addValue("send-error-details", "true")
                                                            .addValue("max-buffered-entity-size", "16 KB")
                                                            .addValue("validate-request-headers", "false")
                                                            .addValue("validate-response-headers", "false")
                                                            .addValue("validate-path", "false")
                                                            .build())
                                                    .build()))
                        .get(provider.configKey()),
                provider.configKey());

        assertThat(http3Config.type(), is(Http3BlueprintSupport.CONFIG_NAME));
        assertThat(http3Config.name(), is(Http3BlueprintSupport.CONFIG_NAME));
        assertThat(http3Config.enabled(), is(true));
        assertThat(http3Config.maxHeadersSize(), is(12_345));
        assertThat(http3Config.maxFieldSectionSize(), is(16_384L));
        assertThat(http3Config.qpackMaxTableCapacity(), is(4_096L));
        assertThat(http3Config.qpackBlockedStreams(), is(32L));
        assertThat(http3Config.responseDispatchWindowSize(), is(32_768));
        assertThat(http3Config.sendErrorDetails(), is(true));
        assertThat(http3Config.maxBufferedEntitySize().toBytes(), is(16_384L));
        assertThat(http3Config.validateRequestHeaders(), is(false));
        assertThat(http3Config.validateResponseHeaders(), is(false));
        assertThat(http3Config.validatePath(), is(false));
    }

    @Test
    void rejectsNonPositiveResponseDispatchWindow() {
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Config.builder().responseDispatchWindowSize(0).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Config.builder().responseDispatchWindowSize(-1).buildPrototype());
    }

    @Test
    void validatesSettingBoundariesAtBuildAndConfigLoad() {
        long maxVarInt = VariableLengthEncoder.MAX_ENCODED_INTEGER;

        Http3Config sentinel = Http3Config.builder()
                .maxFieldSectionSize(-1)
                .qpackMaxTableCapacity(-1)
                .qpackBlockedStreams(-1)
                .buildPrototype();
        assertThat(sentinel.maxFieldSectionSize(), is(-1L));
        assertThat(sentinel.qpackMaxTableCapacity(), is(-1L));
        assertThat(sentinel.qpackBlockedStreams(), is(-1L));

        Http3Config zero = Http3Config.builder()
                .maxFieldSectionSize(0)
                .qpackMaxTableCapacity(0)
                .qpackBlockedStreams(0)
                .buildPrototype();
        assertThat(zero.maxFieldSectionSize(), is(0L));
        assertThat(zero.qpackMaxTableCapacity(), is(0L));
        assertThat(zero.qpackBlockedStreams(), is(0L));

        Http3Config maximum = Http3Config.builder()
                .maxFieldSectionSize(maxVarInt)
                .qpackMaxTableCapacity(maxVarInt)
                .qpackBlockedStreams(maxVarInt)
                .buildPrototype();
        assertThat(maximum.maxFieldSectionSize(), is(maxVarInt));
        assertThat(maximum.qpackMaxTableCapacity(), is(maxVarInt));
        assertThat(maximum.qpackBlockedStreams(), is(maxVarInt));

        Http3QuicProtocolProvider provider = new Http3QuicProtocolProvider();
        Http3Config loadedMaximum = provider.create(
                Config.just(ConfigSources.create(Map.of("max-field-section-size", Long.toString(maxVarInt),
                                                        "qpack-max-table-capacity", Long.toString(maxVarInt),
                                                        "qpack-blocked-streams", Long.toString(maxVarInt)))),
                provider.configKey());
        assertThat(loadedMaximum.maxFieldSectionSize(), is(maxVarInt));
        assertThat(loadedMaximum.qpackMaxTableCapacity(), is(maxVarInt));
        assertThat(loadedMaximum.qpackBlockedStreams(), is(maxVarInt));

        long tooLarge = maxVarInt + 1;
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Config.builder().maxFieldSectionSize(-2).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Config.builder().qpackMaxTableCapacity(-2).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Config.builder().qpackBlockedStreams(-2).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Config.builder().maxFieldSectionSize(tooLarge).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Config.builder().qpackMaxTableCapacity(tooLarge).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Config.builder().qpackBlockedStreams(tooLarge).buildPrototype());

        Config invalidExternal = Config.just(
                ConfigSources.create(Map.of("max-field-section-size", Long.toString(tooLarge))));
        assertThrows(IllegalArgumentException.class,
                     () -> provider.create(invalidExternal, provider.configKey()));
    }
}
