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

import io.helidon.builder.api.Prototype;

import static io.helidon.quic.QuicTransportParameters.ParameterId.ack_delay_exponent;
import static io.helidon.quic.QuicTransportParameters.ParameterId.active_connection_id_limit;
import static io.helidon.quic.QuicTransportParameters.ParameterId.disable_active_migration;
import static io.helidon.quic.QuicTransportParameters.ParameterId.max_ack_delay;

final class QuicTransportParametersConfigSupport {
    static final int DEFAULT_ACK_DELAY_EXPONENT = 3;
    static final Duration DEFAULT_MAX_ACK_DELAY = Duration.ofMillis(25);
    static final long DEFAULT_ACTIVE_CONNECTION_ID_LIMIT = 2;

    static final int MAX_ACK_DELAY_EXPONENT = 20;
    static final long MAX_MAX_ACK_DELAY_MILLIS = 1L << 14;
    static final long MAX_ACTIVE_CONNECTION_ID_LIMIT = 1024;

    private QuicTransportParametersConfigSupport() {
    }

    static QuicTransportParameters createTransportParameters(QuicTransportParametersConfig config) {
        QuicTransportParameters parameters = QuicTransportParameters.create();

        config.ackDelayExponent()
                .ifPresent(value -> parameters.intParameter(ack_delay_exponent, value.longValue()));
        config.maxAckDelay()
                .ifPresent(value -> parameters.intParameter(max_ack_delay, value.toMillis()));
        config.activeConnectionIdLimit()
                .ifPresent(value -> parameters.intParameter(active_connection_id_limit, value));
        parameters.booleanParameter(disable_active_migration, true);

        return parameters;
    }

    static final class Decorator implements Prototype.BuilderDecorator<QuicTransportParametersConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(QuicTransportParametersConfig.BuilderBase<?, ?> target) {
            ackDelayExponent(target);
            maxAckDelay(target);
            activeConnectionIdLimit(target);
        }

        private void ackDelayExponent(QuicTransportParametersConfig.BuilderBase<?, ?> target) {
            target.ackDelayExponent()
                    .ifPresent(value -> {
                        if (value < 0 || value > MAX_ACK_DELAY_EXPONENT) {
                            throw new IllegalArgumentException("ackDelayExponent must be between 0 and 20: " + value);
                        }
                    });
        }

        private void maxAckDelay(QuicTransportParametersConfig.BuilderBase<?, ?> target) {
            target.maxAckDelay()
                    .ifPresent(value -> {
                        long millis = value.toMillis();
                        if (value.isNegative() || millis >= MAX_MAX_ACK_DELAY_MILLIS) {
                            throw new IllegalArgumentException(
                                    "maxAckDelay must be between PT0S and PT16.383S inclusive: " + value);
                        }
                        target.maxAckDelay(Duration.ofMillis(millis));
                    });
        }

        private void activeConnectionIdLimit(QuicTransportParametersConfig.BuilderBase<?, ?> target) {
            target.activeConnectionIdLimit()
                    .ifPresent(value -> {
                        if (value < DEFAULT_ACTIVE_CONNECTION_ID_LIMIT
                                || value > MAX_ACTIVE_CONNECTION_ID_LIMIT) {
                            throw new IllegalArgumentException(
                                    "activeConnectionIdLimit must be between 2 and 1024: " + value);
                        }
                    });
        }
    }
}
