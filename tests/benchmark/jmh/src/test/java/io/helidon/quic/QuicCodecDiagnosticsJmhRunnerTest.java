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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import io.helidon.quic.QuicCodecDiagnosticsJmhBenchmark.LogLevel;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class QuicCodecDiagnosticsJmhRunnerTest {
    private static final String PREFIX = "quic.codec.diagnostics.jmh.";
    private static final Set<String> METHODS = Set.of("encodeOneRtt", "decodeOneRtt",
                                                      "encodeOneRttAllocation", "decodeOneRttAllocation");

    static Options options(Properties properties) {
        String[] methods = values(properties, "methods", "");
        for (String method : methods) {
            if (!METHODS.contains(method)) {
                throw new IllegalArgumentException("Unknown codec diagnostic benchmark: " + method);
            }
        }
        String[] payloads = values(properties, "payloadSize", "64,1200");
        for (String value : payloads) {
            if (!Set.of("64", "1200").contains(value)) {
                throw new IllegalArgumentException("payloadSize must be 64 or 1200");
            }
        }
        String[] levels = values(properties, "logLevel", "OFF,DEBUG");
        for (String value : levels) {
            LogLevel.valueOf(value);
        }
        if (!"false".equalsIgnoreCase(properties.getProperty(PREFIX + "gcProfiler", "false"))) {
            throw new IllegalArgumentException("Use the Allocation methods; GCProfiler includes excluded fixture work");
        }
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include("^" + Pattern.quote(QuicCodecDiagnosticsJmhBenchmark.class.getName())
                                 + "\\.(?:" + String.join("|", methods) + ")$")
                .threads(integer(properties, "threads", 1, 1, 1))
                .forks(integer(properties, "forks", 1, 1, 4))
                .warmupForks(0)
                .warmupIterations(integer(properties, "warmupIterations", 3, 1, 10))
                .measurementIterations(integer(properties, "measurementIterations", 5, 1, 10))
                .warmupTime(TimeValue.milliseconds(integer(properties, "warmupMillis", 1000, 500, 10_000)))
                .measurementTime(TimeValue.milliseconds(integer(properties, "measurementMillis", 1000, 500, 10_000)))
                .param("payloadSize", payloads)
                .param("logLevel", levels)
                .resultFormat(ResultFormatType.JSON)
                .result(properties.getProperty(PREFIX + "result", "./target/quic-codec-diagnostics-1.json"))
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
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Explicit nonempty values are required for " + PREFIX + name);
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException("Duplicate value in " + PREFIX + name + ": " + value);
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
