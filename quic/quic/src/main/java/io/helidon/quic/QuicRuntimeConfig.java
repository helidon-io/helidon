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

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import static io.helidon.quic.QuicEndpoint.ChannelType.BLOCKING_WITH_VIRTUAL_THREADS;
import static io.helidon.quic.QuicEndpoint.ChannelType.NON_BLOCKING_WITH_SELECTOR;

/**
 * Resolved implementation policy for one QUIC runtime.
 */
final class QuicRuntimeConfig {
    static final int DEFAULT_MAX_BUFFERED_HIGH = 512 << 10;
    static final int DEFAULT_MAX_BUFFERED_LOW = 384 << 10;
    static final int DEFAULT_DATAGRAM_SIZE = 1200;
    static final int DEFAULT_MAX_PTO_BACKOFF_EXPONENT = 8;
    static final Duration DEFAULT_MAX_PTO_BACKOFF_TIMEOUT = Duration.ofMinutes(4);
    static final Duration DEFAULT_MIN_PTO_BACKOFF_TIMEOUT = Duration.ofSeconds(15);
    static final Duration DEFAULT_INITIAL_RTT = Duration.ofMillis(333);
    private static final long DEFAULT_TIMER_FREQUENCY_OTHER = 1000;
    private static final long DEFAULT_TIMER_FREQUENCY_WINDOWS = 64;

    private final QuicConfig userConfig;
    private final Endpoint endpoint;
    private final Recovery recovery;
    private final TransportParameters transportParameters;
    private final QuicAeadLimits.Confidentiality confidentialityLimits;

    QuicRuntimeConfig(QuicConfig userConfig,
                      Endpoint endpoint,
                      Recovery recovery,
                      TransportParameters transportParameters,
                      QuicAeadLimits.Confidentiality confidentialityLimits) {
        this.userConfig = Objects.requireNonNull(userConfig, "userConfig");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.transportParameters = Objects.requireNonNull(transportParameters, "transportParameters");
        this.confidentialityLimits = Objects.requireNonNull(confidentialityLimits, "confidentialityLimits");
    }

    static QuicRuntimeConfig create(QuicConfig userConfig) {
        Objects.requireNonNull(userConfig, "userConfig");
        boolean windows = OperatingSystemHolder.OS_NAME.startsWith("windows");
        QuicEndpoint.ChannelType channelType = windows
                ? NON_BLOCKING_WITH_SELECTOR
                : BLOCKING_WITH_VIRTUAL_THREADS;
        QuicSelectorThreading selectorThreading = channelType == BLOCKING_WITH_VIRTUAL_THREADS
                ? QuicSelectorThreading.VIRTUAL
                : QuicSelectorThreading.PLATFORM;
        var configuredTransportParameters = userConfig.transportParameters();

        return new QuicRuntimeConfig(
                userConfig,
                new Endpoint(channelType,
                             selectorThreading,
                             false,
                             1,
                             false,
                             DEFAULT_MAX_BUFFERED_HIGH,
                             DEFAULT_MAX_BUFFERED_LOW,
                             true,
                             DEFAULT_DATAGRAM_SIZE),
                new Recovery(DEFAULT_MAX_PTO_BACKOFF_EXPONENT,
                             DEFAULT_MAX_PTO_BACKOFF_TIMEOUT,
                             DEFAULT_MIN_PTO_BACKOFF_TIMEOUT,
                             DEFAULT_INITIAL_RTT,
                             windows ? DEFAULT_TIMER_FREQUENCY_WINDOWS : DEFAULT_TIMER_FREQUENCY_OTHER),
                new TransportParameters(
                        configuredTransportParameters.ackDelayExponent()
                                .orElse(QuicTransportParametersConfigSupport.DEFAULT_ACK_DELAY_EXPONENT),
                        configuredTransportParameters.maxAckDelay()
                                .orElse(QuicTransportParametersConfigSupport.DEFAULT_MAX_ACK_DELAY),
                        configuredTransportParameters.activeConnectionIdLimit()
                                .orElse(QuicTransportParametersConfigSupport.DEFAULT_ACTIVE_CONNECTION_ID_LIMIT)),
                QuicAeadLimits.Confidentiality.defaults());
    }

    QuicConfig userConfig() {
        return userConfig;
    }

    Endpoint endpoint() {
        return endpoint;
    }

    Recovery recovery() {
        return recovery;
    }

    TransportParameters transportParameters() {
        return transportParameters;
    }

    QuicAeadLimits.Confidentiality confidentialityLimits() {
        return confidentialityLimits;
    }

    record Endpoint(QuicEndpoint.ChannelType channelType,
                    QuicSelectorThreading selectorThreading,
                    boolean pollerUsePlatformThreads,
                    int maxEndpoints,
                    boolean sendAsync,
                    int maxBufferedHigh,
                    int maxBufferedLow,
                    boolean useDirectBufferPool,
                    int defaultDatagramSize) {
        Endpoint {
            Objects.requireNonNull(channelType, "channelType");
            Objects.requireNonNull(selectorThreading, "selectorThreading");
            if (maxEndpoints < 1) {
                throw new IllegalArgumentException("maxEndpoints must be greater than 0: " + maxEndpoints);
            }
            if (maxBufferedHigh < 1) {
                throw new IllegalArgumentException("maxBufferedHigh must be greater than 0: " + maxBufferedHigh);
            }
            if (maxBufferedLow < 0 || maxBufferedLow >= maxBufferedHigh) {
                throw new IllegalArgumentException("maxBufferedLow must be between 0 and maxBufferedHigh: "
                                                           + maxBufferedLow);
            }
            if (defaultDatagramSize < QuicConfigSupport.MINIMUM_DATAGRAM_SIZE
                    || defaultDatagramSize > QuicConfigSupport.MAXIMUM_DATAGRAM_SIZE) {
                throw new IllegalArgumentException("defaultDatagramSize must be between 1200 and 65527: "
                                                           + defaultDatagramSize);
            }
        }
    }

    record Recovery(int maxPtoBackoffExponent,
                    Duration maxPtoBackoffTimeout,
                    Duration minPtoBackoffTimeout,
                    Duration initialRtt,
                    long timerFrequencyHz) {
        Recovery {
            Objects.requireNonNull(maxPtoBackoffTimeout, "maxPtoBackoffTimeout");
            Objects.requireNonNull(minPtoBackoffTimeout, "minPtoBackoffTimeout");
            Objects.requireNonNull(initialRtt, "initialRtt");
            if (maxPtoBackoffExponent < 2 || maxPtoBackoffExponent > 20) {
                throw new IllegalArgumentException("maxPtoBackoffExponent must be between 2 and 20: "
                                                           + maxPtoBackoffExponent);
            }
            if (maxPtoBackoffTimeout.isNegative() || maxPtoBackoffTimeout.isZero()) {
                throw new IllegalArgumentException("maxPtoBackoffTimeout must be positive: " + maxPtoBackoffTimeout);
            }
            if (minPtoBackoffTimeout.isNegative()) {
                throw new IllegalArgumentException("minPtoBackoffTimeout must not be negative: " + minPtoBackoffTimeout);
            }
            if (minPtoBackoffTimeout.compareTo(maxPtoBackoffTimeout) > 0) {
                throw new IllegalArgumentException("minPtoBackoffTimeout must not exceed maxPtoBackoffTimeout: "
                                                           + minPtoBackoffTimeout);
            }
            if (initialRtt.isNegative() || initialRtt.isZero()) {
                throw new IllegalArgumentException("initialRtt must be positive: " + initialRtt);
            }
            if (timerFrequencyHz < 1) {
                throw new IllegalArgumentException("timerFrequencyHz must be greater than 0: " + timerFrequencyHz);
            }
        }
    }

    record TransportParameters(int ackDelayExponent,
                               Duration maxAckDelay,
                               long activeConnectionIdLimit) {
        TransportParameters {
            Objects.requireNonNull(maxAckDelay, "maxAckDelay");
            if (ackDelayExponent < 0
                    || ackDelayExponent > QuicTransportParametersConfigSupport.MAX_ACK_DELAY_EXPONENT) {
                throw new IllegalArgumentException("ackDelayExponent must be between 0 and 20: " + ackDelayExponent);
            }
            if (maxAckDelay.isNegative()
                    || maxAckDelay.compareTo(
                            Duration.ofMillis(QuicTransportParametersConfigSupport.MAX_MAX_ACK_DELAY_MILLIS)) >= 0) {
                throw new IllegalArgumentException("maxAckDelay must be between 0 and 16383 milliseconds: "
                                                           + maxAckDelay);
            }
            if (activeConnectionIdLimit < 2
                    || activeConnectionIdLimit > QuicTransportParametersConfigSupport.MAX_ACTIVE_CONNECTION_ID_LIMIT) {
                throw new IllegalArgumentException(
                        "activeConnectionIdLimit must be between 2 and 1024: "
                                + activeConnectionIdLimit);
            }
        }
    }

    private static final class OperatingSystemHolder {
        private static final String OS_NAME = ManagementFactory.getOperatingSystemMXBean()
                .getName()
                .toLowerCase(Locale.ROOT);

        private OperatingSystemHolder() {
        }
    }
}
