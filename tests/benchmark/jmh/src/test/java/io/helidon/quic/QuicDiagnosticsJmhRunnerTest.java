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

import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import io.helidon.quic.QuicDiagnosticsJmhBenchmark.DeadlineWorkload;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.LogLevel;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.ReceivePath;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.ReceiverKind;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class QuicDiagnosticsJmhRunnerTest {
    private static final String PREFIX = "quic.diagnostics.jmh.";
    private static final Set<String> METHODS = Set.of("timerOffer", "timerOfferAllocation",
                                                      "timerReschedule", "timerRescheduleAllocation",
                                                      "packetDeadline", "packetDeadlineAllocation",
                                                      "outgoingBuffer", "outgoingBufferAllocation",
                                                      "synchronousDatagram", "synchronousDatagramAllocation",
                                                      "queuedDatagrams", "queuedDatagramsAllocation",
                                                      "receiveDispatch", "receiveDispatchAllocation");

    static Options options(Properties properties) {
        String[] methods = values(properties, "methods",
                                  "timerOffer,timerReschedule,packetDeadline,outgoingBuffer,"
                                          + "synchronousDatagram,queuedDatagrams");
        for (String method : methods) {
            if (!METHODS.contains(method)) {
                throw new IllegalArgumentException("Unknown diagnostic benchmark: " + method);
            }
        }
        String[] depths = values(properties, "queueDepth", "0,64");
        for (String value : depths) {
            int depth = Integer.parseInt(value);
            if (depth < 0 || depth > QuicDiagnosticsJmhBenchmark.MAX_QUEUE_DEPTH) {
                throw new IllegalArgumentException("queueDepth must be between 0 and "
                                                           + QuicDiagnosticsJmhBenchmark.MAX_QUEUE_DEPTH);
            }
        }
        String[] levels = values(properties, "logLevel", "OFF,TRACE");
        for (String value : levels) {
            LogLevel.valueOf(value);
        }
        String[] workloads = values(properties, "deadlineWorkload", "IDLE,ACK,PTO");
        for (String value : workloads) {
            DeadlineWorkload.valueOf(value);
        }
        String[] receivePaths = values(properties, "receivePath", "PACKET,STATELESS_RESET");
        for (String value : receivePaths) {
            ReceivePath.valueOf(value);
        }
        String[] receiverKinds = values(properties, "receiverKind", "SOCKET_CONTEXT,FALLBACK");
        for (String value : receiverKinds) {
            ReceiverKind.valueOf(value);
        }
        String[] pools = values(properties, "bufferPool", "true,false");
        for (String value : pools) {
            if (!Set.of("true", "false").contains(value)) {
                throw new IllegalArgumentException("bufferPool must be true or false");
            }
        }
        if (Boolean.parseBoolean(properties.getProperty(PREFIX + "gcProfiler", "false"))) {
            throw new IllegalArgumentException("GCProfiler includes fixture and writer allocation; "
                                                       + "use the Allocation methods");
        }
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include("^" + Pattern.quote(QuicDiagnosticsJmhBenchmark.class.getName())
                                 + "\\.(?:" + String.join("|", methods) + ")$")
                .threads(integer(properties, "threads", 1, 1, 1))
                .forks(integer(properties, "forks", 1, 1, 4))
                .warmupIterations(integer(properties, "warmupIterations", 3, 1, 10))
                .measurementIterations(integer(properties, "measurementIterations", 5, 1, 10))
                .warmupTime(TimeValue.milliseconds(integer(properties, "warmupMillis", 1000, 500, 10_000)))
                .measurementTime(TimeValue.milliseconds(integer(properties, "measurementMillis", 1000, 500, 10_000)))
                .param("queueDepth", depths)
                .param("logLevel", levels)
                .param("deadlineWorkload", workloads)
                .param("bufferPool", pools)
                .param("receivePath", receivePaths)
                .param("receiverKind", receiverKinds)
                .resultFormat(ResultFormatType.JSON)
                .result(properties.getProperty(PREFIX + "result", "./target/quic-diagnostics-jmh-result.json"))
                .shouldFailOnError(true);
        String output = properties.getProperty(PREFIX + "output");
        if (output != null && !output.isBlank()) {
            builder.output(output);
        }
        return builder.build();
    }

    @Test
    void run() throws RunnerException {
        new Runner(options(System.getProperties())).run();
    }

    private static String[] values(Properties properties, String name, String defaultValue) {
        String[] values = Arrays.stream(properties.getProperty(PREFIX + name, defaultValue).split(",", -1))
                .map(String::trim)
                .toArray(String[]::new);
        for (String value : values) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Empty value in " + PREFIX + name);
            }
        }
        return values;
    }

    private static int integer(Properties properties, String name, int defaultValue, int minimum, int maximum) {
        int value = Integer.parseInt(properties.getProperty(PREFIX + name, Integer.toString(defaultValue)));
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
