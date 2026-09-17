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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.helidon.quic.QuicTransportParameters.ParameterId.ack_delay_exponent;
import static io.helidon.quic.QuicTransportParameters.ParameterId.active_connection_id_limit;
import static io.helidon.quic.QuicTransportParameters.ParameterId.disable_active_migration;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_source_connection_id;
import static io.helidon.quic.QuicTransportParameters.ParameterId.max_idle_timeout;
import static io.helidon.quic.QuicTransportParameters.ParameterId.max_udp_payload_size;
import static io.helidon.quic.QuicTransportParameters.ParameterId.preferred_address;
import static io.helidon.quic.QuicTransportParameters.ParameterId.version_information;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTransportParametersTest {
    private static final long MAX_INITIAL_STREAM_COUNT = 1L << 60;

    @Test
    void shouldRenderTransportParameterText() {
        assertThat(max_udp_payload_size.text(), is("max_udp_payload_size"));
    }

    @Test
    void shouldReturnDefaultValuesForAbsentParameters() {
        QuicTransportParameters parameters = new QuicTransportParameters();

        assertThat(parameters.intParameter(max_udp_payload_size), is(65527L));
        assertThat(parameters.intParameter(active_connection_id_limit), is(2L));
        assertThat(parameters.intParameter(ack_delay_exponent), is(3L));
        assertThat(parameters.booleanParameter(disable_active_migration), is(false));
    }

    @Test
    void shouldRoundTripEncodedParameters() throws Exception {
        QuicTransportParameters parameters = new QuicTransportParameters();
        byte[] resetToken = new byte[16];
        resetToken[0] = 0x21;

        parameters.intParameter(max_idle_timeout, 1234);
        parameters.intParameter(active_connection_id_limit, 4);
        parameters.booleanParameter(disable_active_migration, true);
        parameters.parameter(initial_source_connection_id, new byte[] {0x01, 0x02, 0x03});
        parameters.versionInformationParameter(version_information,
                                                  QuicTransportParameters.buildVersionInformation(
                                                          QuicVersion.QUIC_V1,
                                                          List.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2)));
        parameters.preferredAddressParameter(preferred_address,
                                                (Inet4Address) InetAddress.getByAddress(new byte[] {127, 0, 0, 1}),
                                                443,
                                                (Inet6Address) InetAddress.getByAddress(new byte[] {
                                                        0x20, 0x01, 0x0d, (byte) 0xb8,
                                                        0, 0, 0, 0,
                                                        0, 0, 0, 0,
                                                        0, 0, 0, 1
                                                }),
                                                8443,
                                                BufferData.create(new byte[] {0x11, 0x22, 0x33, 0x44}),
                                                BufferData.create(resetToken));

        BufferData buffer = BufferData.create(parameters.size());
        int encoded = parameters.encode(buffer);
        QuicTransportParameters decoded = QuicTransportParameters.decode(buffer);

        assertThat(encoded, is(parameters.size()));
        assertThat(decoded.intParameter(max_idle_timeout), is(1234L));
        assertThat(decoded.intParameter(active_connection_id_limit), is(4L));
        assertThat(decoded.booleanParameter(disable_active_migration), is(true));
        assertThat(decoded.matches(initial_source_connection_id, new PeerConnectionId(new byte[] {0x01, 0x02, 0x03})),
                   is(true));
        QuicTransportParameters.VersionInformation versionInfo =
                decoded.versionInformationParameter(version_information).orElseThrow();
        byte[] preferredAddressValue = decoded.parameter(preferred_address).orElseThrow();
        assertThat(versionInfo.chosenVersion(),
                   is(QuicVersion.QUIC_V1.versionNumber()));
        assertThat(versionInfo.availableVersions(),
                   is(new int[] {QuicVersion.QUIC_V1.versionNumber(), QuicVersion.QUIC_V2.versionNumber()}));
        assertThat(QuicTransportParameters.preferredAddress(preferred_address,
                                                               preferredAddressValue),
                   contains(new InetSocketAddress("127.0.0.1", 443),
                            new InetSocketAddress("2001:db8:0:0:0:0:0:1", 8443)));
        BufferData preferredConnectionId = QuicTransportParameters.preferredConnectionIdData(preferredAddressValue);
        assertThat(preferredConnectionId.readBytes(), is(new byte[] {0x11, 0x22, 0x33, 0x44}));
        assertThat(QuicTransportParameters.preferredStatelessResetToken(preferredAddressValue),
                   is(resetToken));
    }

    @Test
    void shouldRejectNullPreferredAddressValues() {
        assertThrows(NullPointerException.class,
                     () -> QuicTransportParameters.preferredAddress(preferred_address, null));
        assertThrows(NullPointerException.class,
                     () -> QuicTransportParameters.preferredConnectionIdData(null));
        assertThrows(NullPointerException.class,
                     () -> QuicTransportParameters.preferredStatelessResetToken(null));
        assertThrows(NullPointerException.class,
                     () -> QuicTransportParameters.preferredConnectionId(null));
    }

    @Test
        //= https://www.rfc-editor.org/rfc/rfc9000#section-18.2
        //# Values below 1200 are invalid.
    void shouldRejectMaxUdpPayloadSizeBelow1200() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> new QuicTransportParameters().intParameter(max_udp_payload_size,
                                                                                                       1199));

        assertThat(ex.getMessage(), containsString("value must be at least 1200"));
    }

    @Test
    void shouldAcceptFullVarintRangeForMaxUdpPayloadSize() throws Exception {
        for (long value : List.of(65_528L, VariableLengthEncoder.MAX_ENCODED_INTEGER)) {
            QuicTransportParameters parameters = new QuicTransportParameters();
            parameters.intParameter(max_udp_payload_size, value);
            BufferData encoded = BufferData.create(parameters.size());
            parameters.encode(encoded);

            QuicTransportParameters decoded = QuicTransportParameters.decode(encoded);

            assertThat(decoded.intParameter(max_udp_payload_size), is(value));
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuicTransportParameters.ParameterId.class,
                names = {"initial_max_streams_bidi", "initial_max_streams_uni"})
    void shouldRoundTripInitialStreamCountsThroughInclusiveMaximum(QuicTransportParameters.ParameterId id) throws Exception {
        for (long value : List.of(MAX_INITIAL_STREAM_COUNT - 1, MAX_INITIAL_STREAM_COUNT)) {
            QuicTransportParameters parameters = QuicTransportParameters.create();
            parameters.intParameter(id, value);
            assertThat(parameters.intParameter(id), is(value));
            BufferData bufferData = BufferData.create(parameters.size());
            ByteBuffer byteBuffer = ByteBuffer.allocate(parameters.size());
            assertThat(parameters.encode(bufferData), is(parameters.size()));
            assertThat(parameters.encode(byteBuffer), is(parameters.size()));
            byteBuffer.flip();

            assertAll(id.text() + "=" + value,
                      () -> assertThat(QuicTransportParameters.decode(bufferData).intParameter(id), is(value)),
                      () -> assertThat(QuicTransportParameters.decode(byteBuffer).intParameter(id), is(value)));
            assertThat(bufferData.available(), is(0));
            assertThat(byteBuffer.remaining(), is(0));
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuicTransportParameters.ParameterId.class,
                names = {"initial_max_streams_bidi", "initial_max_streams_uni"})
    void shouldDecodePeerInitialStreamCountsThroughInclusiveMaximum(QuicTransportParameters.ParameterId id) {
        for (long value : List.of(MAX_INITIAL_STREAM_COUNT - 1, MAX_INITIAL_STREAM_COUNT)) {
            byte[] encoded = rawInitialStreamCount(id, value);

            assertAll(id.text() + "=" + value,
                      () -> assertThat(QuicTransportParameters.decode(BufferData.create(encoded)).intParameter(id), is(value)),
                      () -> assertThat(QuicTransportParameters.decode(ByteBuffer.wrap(encoded)).intParameter(id), is(value)));
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuicTransportParameters.ParameterId.class,
                names = {"initial_max_streams_bidi", "initial_max_streams_uni"})
    void shouldRejectInitialStreamCountsAboveInclusiveMaximum(QuicTransportParameters.ParameterId id) {
        long value = MAX_INITIAL_STREAM_COUNT + 1;
        byte[] encoded = rawInitialStreamCount(id, value);
        String expectedReason = id.text() + ": value out of range [0,2^60]; found " + value;

        IllegalArgumentException localFailure = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTransportParameters.create().intParameter(id, value));
        QuicTransportException bufferDataFailure = assertThrows(
                QuicTransportException.class,
                () -> QuicTransportParameters.decode(BufferData.create(encoded)));
        QuicTransportException byteBufferFailure = assertThrows(
                QuicTransportException.class,
                () -> QuicTransportParameters.decode(ByteBuffer.wrap(encoded)));

        assertAll(
                () -> assertThat(localFailure.getMessage(), is(expectedReason)),
                () -> assertThat(bufferDataFailure.errorCode(), is(QuicTransportErrors.TRANSPORT_PARAMETER_ERROR.code())),
                () -> assertThat(bufferDataFailure.reason(), containsString(expectedReason)),
                () -> assertThat(byteBufferFailure.errorCode(), is(QuicTransportErrors.TRANSPORT_PARAMETER_ERROR.code())),
                () -> assertThat(byteBufferFailure.reason(), containsString(expectedReason)));
    }

    @Test
        //= https://www.rfc-editor.org/rfc/rfc9000#section-18.2
        //# The value of the
        //# active_connection_id_limit parameter MUST be at least 2.
        //# An
        //# endpoint that receives a value less than 2 MUST close the
        //# connection with an error of type TRANSPORT_PARAMETER_ERROR.
    void shouldRejectActiveConnectionIdLimitBelowTwo() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> new QuicTransportParameters().intParameter(active_connection_id_limit,
                                                                                                       1));

        assertThat(ex.getMessage(), containsString("value out of range [2...]"));
    }

    @Test
        //= https://www.rfc-editor.org/rfc/rfc9000#section-7.4.2
        //# An endpoint MUST ignore transport parameters that it does
        //# not support.
    void shouldIgnoreUnsupportedTransportParameterOnDecode() throws Exception {
        BufferData buffer = BufferData.create(16);

        VariableLengthEncoder.encode(buffer, 0x173e);
        VariableLengthEncoder.encode(buffer, 1);
        buffer.write(0x01);

        QuicTransportParameters decoded = QuicTransportParameters.decode(buffer);

        assertThat(decoded.size(), is(0));
    }

    @Test
        //= https://www.rfc-editor.org/rfc/rfc9000#section-7.4
        //# An endpoint SHOULD treat receipt of
        //# duplicate transport parameters as a connection error of type
        //# TRANSPORT_PARAMETER_ERROR.
    void shouldRejectDuplicateTransportParameterOnDecode() {
        BufferData buffer = BufferData.create(16);

        VariableLengthEncoder.encode(buffer, disable_active_migration.idx());
        VariableLengthEncoder.encode(buffer, 0);
        VariableLengthEncoder.encode(buffer, disable_active_migration.idx());
        VariableLengthEncoder.encode(buffer, 0);

        QuicTransportException ex = assertThrows(QuicTransportException.class,
                                                 () -> QuicTransportParameters.decode(buffer));

        assertThat(ex.errorCode(), is(QuicTransportErrors.TRANSPORT_PARAMETER_ERROR.code()));
        assertThat(ex.reason(), containsString("Duplicate transport parameter disable_active_migration"));
    }

    private static byte[] rawInitialStreamCount(QuicTransportParameters.ParameterId id, long value) {
        // These boundary values use an eight-byte QUIC varint; construct the peer TLV without a local setter.
        return ByteBuffer.allocate(2 + Long.BYTES)
                .put((byte) id.idx())
                .put((byte) Long.BYTES)
                .putLong(0xc000000000000000L | value)
                .array();
    }
}
