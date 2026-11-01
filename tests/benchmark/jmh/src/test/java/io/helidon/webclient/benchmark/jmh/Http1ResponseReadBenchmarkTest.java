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

package io.helidon.webclient.benchmark.jmh;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1ResponseReadBenchmarkTest {
    @Test
    void closesAcceptedSocketWhenHandlerHandoffIsRejected() throws Exception {
        Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        try (var listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             var peer = new Socket(listener.getInetAddress(), listener.getLocalPort());
             var accepted = listener.accept();
             var handlers = Executors.newVirtualThreadPerTaskExecutor()) {
            handlers.shutdown();

            assertThrows(RejectedExecutionException.class,
                         () -> Http1ResponseReadJmhBenchmark.submitResponseHandler(accepted, handlers, sockets, () -> { }));

            assertThat(accepted.isClosed(), is(true));
            assertThat(sockets, is(Set.of()));
            peer.setSoTimeout(5000);
            assertThat(peer.getInputStream().read(), is(-1));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "64:64:bulk:plain:fixed:identity:reuse",
            "8192:64:entity:plain:fixed:identity:reuse",
            "8192:64:scalar:plain:fixed:identity:reuse",
            "8192:64:mixed:plain:chunked:identity:reuse",
            "1048576:65536:bytes:plain:fixed:identity:reuse",
            "8192:8192:string:plain:fixed:identity:reuse",
            "8192:8192:bulk:plain:fixed:gzip:reuse",
            "8192:64:bulk:tls:fixed:identity:reuse",
            "8192:8192:bulk:tls:chunked:gzip:reuse",
            "64:64:bulk:plain:fixed:identity:new",
            "64:64:bulk:tls:fixed:identity:new"
    })
    void consumesCompleteDecodedBodyAndPreservesConnectionContract(String scenario) throws Exception {
        var benchmark = new Http1ResponseReadJmhBenchmark();
        benchmark.scenario = scenario;
        benchmark.setup();
        try {
            long expectedBytes = Long.parseLong(scenario.substring(0, scenario.indexOf(':')));
            assertThat(benchmark.exchange() >>> 16, is(expectedBytes));
            assertThat(benchmark.exchange() >>> 16, is(expectedBytes));
        } finally {
            benchmark.tearDown();
        }
    }
}
