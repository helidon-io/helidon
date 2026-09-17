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

package io.helidon.quic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

import org.junit.jupiter.api.Test;

import static io.helidon.quic.QuicEndpoint.ChannelType.BLOCKING_WITH_VIRTUAL_THREADS;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_streams_bidi;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_streams_uni;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicConfigTest {
    @Test
    void unsafeRawDataCanOnlyBeEnabledProgrammatically() {
        assertThat(QuicConfig.create().unsafeRawData(), is(false));
        assertThat(QuicConfig.builder().unsafeRawData(true).build().unsafeRawData(), is(true));

        Config externalConfig = Config.just(ConfigSources.create(Map.of("unsafe-raw-data", "true")));
        assertThat(QuicConfig.create(externalConfig).unsafeRawData(), is(false));
    }

    @Test
    void shouldProvideStablePublicDefaults() {
        QuicConfig config = QuicConfig.create();

        assertAll(
                () -> assertThat(config.availableVersions(), is(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1))),
                () -> assertThat(config.socketReceiveBufferSize().isEmpty(), is(true)),
                () -> assertThat(config.socketSendBufferSize().isEmpty(), is(true)),
                () -> assertThat(config.maxUdpPayloadSize(), is(QuicConfigSupport.DEFAULT_MAX_UDP_PAYLOAD_SIZE)),
                () -> assertThat(config.maxAckRangesPerFrame(),
                                 is(QuicConfigSupport.DEFAULT_MAX_ACK_RANGES_PER_FRAME)),
                () -> assertThat(config.maxHandshakeMessageSize(),
                                 is(QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE)),
                () -> assertThat(config.initialMaxData(), is(QuicConfigSupport.DEFAULT_INITIAL_MAX_DATA)),
                () -> assertThat(config.initialMaxStreamData(),
                                 is(QuicConfigSupport.DEFAULT_INITIAL_MAX_STREAM_DATA)),
                () -> assertThat(config.maxBidiStreams(), is(QuicConfigSupport.DEFAULT_MAX_BIDI_STREAMS)),
                () -> assertThat(config.maxUniStreams(), is(QuicConfigSupport.DEFAULT_MAX_UNI_STREAMS)),
                () -> assertThat(config.transportParameters().ackDelayExponent().isEmpty(), is(true)),
                () -> assertThat(config.transportParameters().maxAckDelay().isEmpty(), is(true)),
                () -> assertThat(config.transportParameters().activeConnectionIdLimit().isEmpty(), is(true)),
                () -> assertThat(config.idleTimeout(), is(Duration.ofSeconds(30))),
                () -> assertThat(config.congestionAlgorithm(), is(QuicCongestionAlgorithm.CUBIC)),
                () -> assertThat(config.maxBytesInFlight(), is(QuicConfigSupport.DEFAULT_MAX_BYTES_IN_FLIGHT)),
                () -> assertThat(config.unsafeRawData(), is(false)));
    }

    @Test
    void shouldRetainConfiguredPublicValues() {
        QuicTransportParametersConfig transportParameters = QuicTransportParametersConfig.builder()
                .ackDelayExponent(4)
                .maxAckDelay(Duration.ofMillis(12))
                .activeConnectionIdLimit(7L)
                .buildPrototype();
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .socketReceiveBufferSize(65_536)
                .socketSendBufferSize(32_768)
                .maxUdpPayloadSize(1400)
                .maxAckRangesPerFrame(2048)
                .maxHandshakeMessageSize(8192)
                .initialMaxData(8192)
                .initialMaxStreamData(4096)
                .maxBidiStreams(12)
                .maxUniStreams(8)
                .transportParameters(transportParameters)
                .idleTimeout(Duration.ofSeconds(45))
                .congestionAlgorithm(QuicCongestionAlgorithm.RENO)
                .maxBytesInFlight(65_536)
                .unsafeRawData(true)
                .buildPrototype();

        assertAll(
                () -> assertThat(config.availableVersions(), is(List.of(QuicVersion.QUIC_V1))),
                () -> assertThat(config.socketReceiveBufferSize().orElseThrow(), is(65_536)),
                () -> assertThat(config.socketSendBufferSize().orElseThrow(), is(32_768)),
                () -> assertThat(config.maxUdpPayloadSize(), is(1400)),
                () -> assertThat(config.maxAckRangesPerFrame(), is(2048)),
                () -> assertThat(config.maxHandshakeMessageSize(), is(8192)),
                () -> assertThat(config.initialMaxData(), is(8192L)),
                () -> assertThat(config.initialMaxStreamData(), is(4096L)),
                () -> assertThat(config.maxBidiStreams(), is(12L)),
                () -> assertThat(config.maxUniStreams(), is(8L)),
                () -> assertThat(config.transportParameters(), is(transportParameters)),
                () -> assertThat(config.idleTimeout(), is(Duration.ofSeconds(45))),
                () -> assertThat(config.congestionAlgorithm(), is(QuicCongestionAlgorithm.RENO)),
                () -> assertThat(config.maxBytesInFlight(), is(65_536L)),
                () -> assertThat(config.unsafeRawData(), is(true)));
    }

    @Test
    void shouldRepresentMaximumConfiguredStreamCountsAsTransportParameters() {
        long maximumStreamCount = 1L << 60;
        QuicConfig config = QuicConfig.builder()
                .maxBidiStreams(maximumStreamCount)
                .maxUniStreams(maximumStreamCount)
                .buildPrototype();
        QuicTransportParameters parameters = QuicTransportParameters.create();

        assertAll(
                () -> {
                    assertThat(config.maxBidiStreams(), is(maximumStreamCount));
                    parameters.intParameter(initial_max_streams_bidi, config.maxBidiStreams());
                    assertThat(parameters.intParameter(initial_max_streams_bidi), is(maximumStreamCount));
                },
                () -> {
                    assertThat(config.maxUniStreams(), is(maximumStreamCount));
                    parameters.intParameter(initial_max_streams_uni, config.maxUniStreams());
                    assertThat(parameters.intParameter(initial_max_streams_uni), is(maximumStreamCount));
                });
    }

    @Test
    void shouldLoadAvailableVersionOrderFromConfig() {
        Config versionOneFirst = Config.create(ConfigSources.create(
                ObjectNode.builder()
                        .addList("available-versions",
                                 ListNode.builder()
                                         .addValue("QUIC_V1")
                                         .addValue("QUIC_V2")
                                         .build())
                        .build()));
        Config versionTwoFirst = Config.create(ConfigSources.create(
                ObjectNode.builder()
                        .addList("available-versions",
                                 ListNode.builder()
                                         .addValue("QUIC_V2")
                                         .addValue("QUIC_V1")
                                         .build())
                        .build()));

        QuicConfig versionOneFirstConfig = QuicConfig.create(versionOneFirst);
        QuicConfig versionTwoFirstConfig = QuicConfig.create(versionTwoFirst);

        assertAll(
                () -> assertThat(versionOneFirstConfig.availableVersions(),
                                 is(List.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2))),
                () -> assertThat(QuicVersion.firstFlightVersion(versionOneFirstConfig.availableVersions()),
                                 is(QuicVersion.QUIC_V1)),
                () -> assertThat(versionTwoFirstConfig.availableVersions(),
                                 is(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1))),
                () -> assertThat(QuicVersion.firstFlightVersion(versionTwoFirstConfig.availableVersions()),
                                 is(QuicVersion.QUIC_V2)));
    }

    @Test
    void shouldResolveRuntimeDefaults() {
        QuicConfig config = QuicConfig.create();

        QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(config);
        boolean usesBlockingChannels = runtimeConfig.endpoint().channelType() == BLOCKING_WITH_VIRTUAL_THREADS;

        assertAll(
                () -> assertThat(runtimeConfig.userConfig(), is(config)),
                () -> assertThat(runtimeConfig.endpoint().selectorThreading(),
                                 is(usesBlockingChannels
                                            ? QuicSelectorThreading.VIRTUAL
                                            : QuicSelectorThreading.PLATFORM)),
                () -> assertThat(runtimeConfig.recovery().timerFrequencyHz(), is(usesBlockingChannels ? 1000L : 64L)),
                () -> assertThat(runtimeConfig.endpoint().pollerUsePlatformThreads(), is(false)),
                () -> assertThat(runtimeConfig.endpoint().maxEndpoints(), is(1)),
                () -> assertThat(runtimeConfig.endpoint().sendAsync(), is(false)),
                () -> assertThat(runtimeConfig.endpoint().maxBufferedHigh(),
                                 is(QuicRuntimeConfig.DEFAULT_MAX_BUFFERED_HIGH)),
                () -> assertThat(runtimeConfig.endpoint().maxBufferedLow(),
                                 is(QuicRuntimeConfig.DEFAULT_MAX_BUFFERED_LOW)),
                () -> assertThat(runtimeConfig.endpoint().useDirectBufferPool(), is(true)),
                () -> assertThat(runtimeConfig.endpoint().defaultDatagramSize(),
                                 is(QuicRuntimeConfig.DEFAULT_DATAGRAM_SIZE)),
                () -> assertThat(runtimeConfig.recovery().maxPtoBackoffExponent(),
                                 is(QuicRuntimeConfig.DEFAULT_MAX_PTO_BACKOFF_EXPONENT)),
                () -> assertThat(runtimeConfig.recovery().maxPtoBackoffTimeout(),
                                 is(QuicRuntimeConfig.DEFAULT_MAX_PTO_BACKOFF_TIMEOUT)),
                () -> assertThat(runtimeConfig.recovery().minPtoBackoffTimeout(),
                                 is(QuicRuntimeConfig.DEFAULT_MIN_PTO_BACKOFF_TIMEOUT)),
                () -> assertThat(runtimeConfig.recovery().initialRtt(), is(QuicRuntimeConfig.DEFAULT_INITIAL_RTT)),
                () -> assertThat(runtimeConfig.transportParameters().ackDelayExponent(),
                                 is(QuicTransportParametersConfigSupport.DEFAULT_ACK_DELAY_EXPONENT)),
                () -> assertThat(runtimeConfig.transportParameters().maxAckDelay(),
                                 is(QuicTransportParametersConfigSupport.DEFAULT_MAX_ACK_DELAY)),
                () -> assertThat(runtimeConfig.transportParameters().activeConnectionIdLimit(),
                                 is(QuicTransportParametersConfigSupport.DEFAULT_ACTIVE_CONNECTION_ID_LIMIT)),
                () -> assertThat(runtimeConfig.confidentialityLimits(),
                                 is(QuicAeadLimits.Confidentiality.defaults())));
    }

    @Test
    void shouldResolveConfiguredTransportParametersForRuntime() {
        QuicConfig config = QuicConfig.builder()
                .transportParameters(QuicTransportParametersConfig.builder()
                                             .ackDelayExponent(4)
                                             .maxAckDelay(Duration.ofMillis(12))
                                             .activeConnectionIdLimit(7L)
                                             .buildPrototype())
                .buildPrototype();

        QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(config);

        assertAll(
                () -> assertThat(runtimeConfig.transportParameters().ackDelayExponent(), is(4)),
                () -> assertThat(runtimeConfig.transportParameters().maxAckDelay(), is(Duration.ofMillis(12))),
                () -> assertThat(runtimeConfig.transportParameters().activeConnectionIdLimit(), is(7L)));
    }

    @Test
    void shouldRejectInvalidPublicValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().socketReceiveBufferSize(0).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().socketSendBufferSize(0).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().maxUdpPayloadSize(1199).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().maxUdpPayloadSize(65_528).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().maxAckRangesPerFrame(0).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder()
                                           .maxAckRangesPerFrame(QuicConfigSupport.MAX_ACK_RANGES_PER_FRAME + 1)
                                           .buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().maxHandshakeMessageSize(0).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().initialMaxData(-1).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().initialMaxData(Long.MAX_VALUE).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().initialMaxStreamData(-1).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().initialMaxStreamData(Long.MAX_VALUE).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().maxBidiStreams(-1).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder()
                                           .maxBidiStreams(QuicConfigSupport.MAX_STREAM_COUNT + 1)
                                           .buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().maxUniStreams(-1).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder()
                                           .maxUniStreams(QuicConfigSupport.MAX_STREAM_COUNT + 1)
                                           .buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().idleTimeout(Duration.ofNanos(-1)).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder()
                                           .idleTimeout(QuicConfigSupport.MAX_IDLE_TIMEOUT.plusMillis(1))
                                           .buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().maxBytesInFlight(1199).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> QuicConfig.builder().maxBytesInFlight(Long.MAX_VALUE).buildPrototype()));
    }

    @Test
    void shouldRejectInvalidAvailableVersionConfiguration() {
        List<QuicVersion> versionsWithNull = new ArrayList<>(List.of(QuicVersion.QUIC_V2));
        versionsWithNull.add(null);
        IllegalArgumentException builderEmpty = assertThrows(
                IllegalArgumentException.class,
                () -> QuicConfig.builder().availableVersions(List.of()).buildPrototype());
        IllegalArgumentException builderDuplicate = assertThrows(
                IllegalArgumentException.class,
                () -> QuicConfig.builder()
                        .availableVersions(List.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V1))
                        .buildPrototype());
        NullPointerException builderNull = assertThrows(
                NullPointerException.class,
                () -> QuicConfig.builder()
                        .availableVersions(versionsWithNull)
                        .buildPrototype());
        Config empty = Config.create(ConfigSources.create(
                ObjectNode.builder()
                        .addList("available-versions", ListNode.builder().build())
                        .build()));
        Config duplicate = Config.create(ConfigSources.create(
                ObjectNode.builder()
                        .addList("available-versions",
                                 ListNode.builder()
                                         .addValue("QUIC_V1")
                                         .addValue("QUIC_V1")
                                         .build())
                        .build()));
        Config unsupported = Config.create(ConfigSources.create(
                ObjectNode.builder()
                        .addList("available-versions",
                                 ListNode.builder()
                                         .addValue("QUIC_V3")
                                         .build())
                        .build()));

        IllegalArgumentException configuredEmpty = assertThrows(
                IllegalArgumentException.class,
                () -> QuicConfig.create(empty));
        IllegalArgumentException configuredDuplicate = assertThrows(
                IllegalArgumentException.class,
                () -> QuicConfig.create(duplicate));
        ConfigMappingException configuredUnsupported = assertThrows(
                ConfigMappingException.class,
                () -> QuicConfig.create(unsupported));

        assertAll(
                () -> assertThat(builderEmpty.getMessage(), is("Need at least one available QUIC version")),
                () -> assertThat(configuredEmpty.getMessage(), is(builderEmpty.getMessage())),
                () -> assertThat(builderDuplicate.getMessage(),
                                 is("Available QUIC versions must be distinct: [QUIC_V1, QUIC_V1]")),
                () -> assertThat(configuredDuplicate.getMessage(), is(builderDuplicate.getMessage())),
                () -> assertThat(builderNull.getMessage(), is("availableVersions contains null")),
                () -> assertThat(configuredUnsupported.getMessage(), containsString("QUIC_V3")));
    }

    @Test
    void shouldLoadAckRangeLimitFromConfig() {
        Config externalConfig = Config.just(ConfigSources.create(Map.of("max-ack-ranges-per-frame", "2048")));

        assertThat(QuicConfig.create(externalConfig).maxAckRangesPerFrame(), is(2048));
    }

    @Test
    void shouldRejectInvalidTransportParameters() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> QuicTransportParametersConfig.builder()
                                                                  .activeConnectionIdLimit(1L)
                                                                  .buildPrototype());

        assertThat(exception.getMessage(),
                   is("activeConnectionIdLimit must be between 2 and 1024: 1"));

        assertThrows(IllegalArgumentException.class,
                     () -> QuicTransportParametersConfig.builder()
                             .activeConnectionIdLimit(QuicTransportParametersConfigSupport.MAX_ACTIVE_CONNECTION_ID_LIMIT + 1)
                             .buildPrototype());
    }

    @Test
    void shouldAlwaysDisableActiveMigration() {
        QuicTransportParameters parameters = QuicTransportParametersConfigSupport.createTransportParameters(
                QuicConfig.create().transportParameters());

        assertThat(parameters.booleanParameter(QuicTransportParameters.ParameterId.disable_active_migration),
                   is(true));
    }

}
