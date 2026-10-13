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

package io.helidon.builder.benchmark.jmh;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.Size;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpConfig;
import io.helidon.http.HttpLogConfig;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicCongestionAlgorithm;
import io.helidon.quic.QuicTransportParametersConfig;
import io.helidon.quic.QuicVersion;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Generated config hashing compared with the previous ordered {@link Objects#hash(Object...)} formula.
 * Construction is outside the measured invocation. Both formulas use the same nested config implementations.
 */
@State(Scope.Thread)
public class GeneratedHashJmhBenchmark {
    @Param({"false", "true"})
    private boolean configured;

    private HttpConfig http;
    private HttpLogConfig log;
    private QuicTransportParametersConfig transport;
    private QuicConfig quic;
    private Http3ClientProtocolConfig protocol;

    /**
     * Prepare default or populated config instances.
     */
    @Setup
    public void setup() {
        if (!configured) {
            http = HttpConfig.builder().build();
            log = HttpLogConfig.builder().build();
            transport = QuicTransportParametersConfig.builder().build();
            quic = QuicConfig.builder().build();
            protocol = Http3ClientProtocolConfig.builder().build();
            verifyHashes();
            return;
        }
        log = HttpLogConfig.builder().receiveLog(false).sendLog(false).loggerName("hash-benchmark")
                .safeHeaders(Set.of(HeaderNames.CONTENT_TYPE)).unsafeRawData(true).build();
        http = HttpConfig.builder().maxHeadersSize(49152).maxBufferedEntitySize(Size.create(12345))
                .validateRequestHeaders(false).validateResponseHeaders(false).log(log).build();
        transport = QuicTransportParametersConfig.builder().ackDelayExponent(7)
                .maxAckDelay(Duration.ofMillis(17)).activeConnectionIdLimit(9L).build();
        quic = QuicConfig.builder().availableVersions(List.of(QuicVersion.QUIC_V1))
                .socketReceiveBufferSize(32768).socketSendBufferSize(65536).maxUdpPayloadSize(8192).sendDatagramSize(4096)
                .maxAckRangesPerFrame(17).maxHandshakeMessageSize(65536).initialMaxData(100000L)
                .initialMaxStreamData(20000L).maxBidiStreams(11L).maxUniStreams(13L)
                .transportParameters(transport).idleTimeout(Duration.ofSeconds(47))
                .congestionAlgorithm(QuicCongestionAlgorithm.RENO).maxBytesInFlight(12000L).unsafeRawData(true).build();
        protocol = Http3ClientProtocolConfig.builder().name("hash-benchmark")
                .validateRequestHeaders(false).validateResponseHeaders(false).priorKnowledge(true)
                .maxFieldSectionSize(8192L).qpackMaxTableCapacity(2048L).qpackBlockedStreams(17L).sendErrorDetails(true)
                .initialResponseTimeout(Duration.ofSeconds(2)).handshakeTimeout(Duration.ofSeconds(3))
                .streamOpenTimeout(Duration.ofSeconds(4)).quic(quic).runtimeExecutor(Runnable::run)
                .maxHeadersSize(49152).maxBufferedEntitySize(Size.create(12345)).log(log).build();
        verifyHashes();
    }

    /**
     * Hash the http config with generated code.
     *
     * @return config hash
     */
    @Benchmark
    public int generatedHttp() {
        return http.hashCode();
    }

    /**
     * Hash the http config with the previous formula.
     *
     * @return config hash
     */
    @Benchmark
    public int objectsHttp() {
        return httpHash(http);
    }

    /**
     * Hash the log config with generated code.
     *
     * @return config hash
     */
    @Benchmark
    public int generatedLog() {
        return log.hashCode();
    }

    /**
     * Hash the log config with the previous formula.
     *
     * @return config hash
     */
    @Benchmark
    public int objectsLog() {
        return logHash(log);
    }

    /**
     * Hash the transport config with generated code.
     *
     * @return config hash
     */
    @Benchmark
    public int generatedTransport() {
        return transport.hashCode();
    }

    /**
     * Hash the transport config with the previous formula.
     *
     * @return config hash
     */
    @Benchmark
    public int objectsTransport() {
        return transportHash(transport);
    }

    /**
     * Hash the quic config with generated code.
     *
     * @return config hash
     */
    @Benchmark
    public int generatedQuic() {
        return quic.hashCode();
    }

    /**
     * Hash the quic config with the previous formula.
     *
     * @return config hash
     */
    @Benchmark
    public int objectsQuic() {
        return quicHash(quic);
    }

    /**
     * Hash the protocol config with generated code.
     *
     * @return config hash
     */
    @Benchmark
    public int generatedProtocol() {
        return protocol.hashCode();
    }

    /**
     * Hash the protocol config with the previous formula.
     *
     * @return config hash
     */
    @Benchmark
    public int objectsProtocol() {
        return protocolHash(protocol);
    }

    private void verifyHashes() {
        verifyHash("http", generatedHttp(), objectsHttp());
        verifyHash("log", generatedLog(), objectsLog());
        verifyHash("transport", generatedTransport(), objectsTransport());
        verifyHash("quic", generatedQuic(), objectsQuic());
        verifyHash("protocol", generatedProtocol(), objectsProtocol());
    }

    private static void verifyHash(String family, int generated, int expected) {
        if (generated != expected) {
            throw new IllegalStateException(family + " generated hash " + generated + " differs from formula " + expected);
        }
    }

    private static int httpHash(HttpConfig value) {
        return Objects.hash(value.maxHeadersSize(), value.maxBufferedEntitySize(), value.validateRequestHeaders(),
                            value.validateResponseHeaders(), value.log());
    }

    private static int logHash(HttpLogConfig value) {
        return Objects.hash(value.receiveLog(), value.sendLog(), value.loggerName(), value.safeHeaders(), value.unsafeRawData());
    }

    private static int transportHash(QuicTransportParametersConfig value) {
        return Objects.hash(value.ackDelayExponent(), value.maxAckDelay(), value.activeConnectionIdLimit());
    }

    private static int quicHash(QuicConfig value) {
        return Objects.hash(value.availableVersions(), value.socketReceiveBufferSize(), value.socketSendBufferSize(),
                            value.maxUdpPayloadSize(), value.sendDatagramSize(), value.maxAckRangesPerFrame(),
                            value.maxHandshakeMessageSize(), value.initialMaxData(), value.initialMaxStreamData(),
                            value.maxBidiStreams(), value.maxUniStreams(), value.transportParameters(), value.idleTimeout(),
                            value.congestionAlgorithm(), value.maxBytesInFlight(), value.unsafeRawData());
    }

    private static int protocolHash(Http3ClientProtocolConfig value) {
        int inherited = httpHash(value);
        int own = Objects.hash(value.name(), value.validateRequestHeaders(), value.validateResponseHeaders(),
                               value.priorKnowledge(), value.maxFieldSectionSize(), value.qpackMaxTableCapacity(),
                               value.qpackBlockedStreams(), value.sendErrorDetails(), value.initialResponseTimeout(),
                               value.handshakeTimeout(), value.streamOpenTimeout(), value.quic(), value.runtimeExecutor());
        return 31 * inherited + own;
    }
}
