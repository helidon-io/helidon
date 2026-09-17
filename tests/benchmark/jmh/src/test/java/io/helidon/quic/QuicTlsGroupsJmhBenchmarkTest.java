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

import java.nio.ByteBuffer;
import java.util.List;

import io.helidon.quic.QuicTlsGroupsJmhBenchmark.AllocationCounters;
import io.helidon.quic.QuicTlsGroupsJmhBenchmark.HandshakeState;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTlsGroupsJmhBenchmarkTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 32, 1024})
    void generatedClientHelloHasTheRequestedWellFormedGroupVector(int unknownGroupCount) {
        var state = new HandshakeState();
        state.unknownGroupCount = unknownGroupCount;
        try {
            state.setUpTrial();
            ByteBuffer input = state.clientHello();
            assertThat(input.remaining(), is(state.baseClientHelloSize() + 2 * unknownGroupCount));
            assertThat("bounded ClientHello size", input.remaining(), lessThan(4096));
            QuicTlsClientHelloMessage hello = QuicTlsClientHelloMessage.decode(input);
            ByteBuffer groups = hello.extension(QuicTlsExtensions.SUPPORTED_GROUPS).orElseThrow().dataBuffer();
            assertThat(groups.remaining(), is(4 + 2 * unknownGroupCount));
            assertThat(Short.toUnsignedInt(groups.getShort()), is(2 * (unknownGroupCount + 1)));
            for (int i = 0; i < unknownGroupCount; i++) {
                assertThat("distinct unknown group " + i, Short.toUnsignedInt(groups.getShort()), is(0x8000 + i));
            }
            assertThat(Short.toUnsignedInt(groups.getShort()), is(QuicTlsNamedGroup.X25519.codePoint()));
            assertThat(groups.hasRemaining(), is(false));
            assertThat(hello.supportedGroups(), is(List.of(QuicTlsNamedGroup.X25519)));
            assertThat(hello.keyShares().stream().map(QuicTlsKeyShareEntry::namedGroup).toList(),
                       is(List.of(QuicTlsNamedGroup.X25519)));
        } finally {
            state.tearDownTrial();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 32, 1024})
    void freshHandshakesSelectTheAdvertisedGroupWithoutChangingInput(int unknownGroupCount) {
        var benchmark = new QuicTlsGroupsJmhBenchmark();
        var state = new HandshakeState();
        state.unknownGroupCount = unknownGroupCount;
        try {
            state.setUpTrial();
            ByteBuffer originalInput = state.clientHello();
            for (int invocation = 0; invocation < 3; invocation++) {
                state.setUpInvocation();
                try {
                    Object result = benchmark.serverFlight(state);
                    assertThat(result, instanceOf(QuicTls13ServerHandshake.ServerFlight.class));
                    var flight = (QuicTls13ServerHandshake.ServerFlight) result;
                    QuicTlsServerHelloMessage hello =
                            QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(flight.serverHello()));
                    assertThat(hello.helloRetryRequest(), is(false));
                    assertThat(hello.keyShare().orElseThrow().namedGroup(), is(QuicTlsNamedGroup.X25519));
                    assertThat(hello.cipherSuite(), is(QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256.codePoint()));
                    assertThat(flight.applicationProtocol(), is("h3"));
                    assertThat(state.clientHello(), is(originalInput));
                } finally {
                    state.tearDownInvocation();
                }
            }
        } finally {
            state.tearDownTrial();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 32, 1024})
    void allocationCountersExcludeFixtureWorkAndResetPerIteration(int unknownGroupCount) {
        var benchmark = new QuicTlsGroupsJmhBenchmark();
        var state = new HandshakeState();
        var counters = new AllocationCounters();
        state.unknownGroupCount = unknownGroupCount;
        try {
            counters.setUpTrial();
            counters.reset();
            state.setUpTrial();
            assertThat(counters.handshakeOperations, is(0L));
            assertThat(counters.allocatedBytes, is(0L));
            for (int invocation = 0; invocation < 2; invocation++) {
                state.setUpInvocation();
                try {
                    assertThat("fixture setup is not counted", counters.handshakeOperations, is((long) invocation));
                    assertThat(benchmark.serverFlightAllocation(state, counters),
                               instanceOf(QuicTls13ServerHandshake.ServerFlight.class));
                } finally {
                    state.tearDownInvocation();
                }
                assertThat(counters.handshakeOperations, is(invocation + 1L));
            }
            assertThat(counters.allocatedBytes, greaterThan(0L));
            counters.reset();
            assertThat(counters.handshakeOperations, is(0L));
            assertThat(counters.allocatedBytes, is(0L));
        } finally {
            state.tearDownTrial();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1025})
    void unsupportedFixtureSizesFailBeforeLoadingTls(int unknownGroupCount) {
        var state = new HandshakeState();
        state.unknownGroupCount = unknownGroupCount;
        try {
            assertThrows(IllegalArgumentException.class, state::setUpTrial);
        } finally {
            state.tearDownTrial();
        }
    }
}
