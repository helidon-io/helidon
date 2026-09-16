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
import java.util.regex.Pattern;

import io.helidon.quic.QuicPathJmhBenchmark.AckPacketSpaceLifecycle;
import io.helidon.quic.QuicPathJmhBenchmark.AckState;
import io.helidon.quic.QuicPathJmhBenchmark.AckWorkload;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class QuicPathJmhRunnerTest {
    private static final String PREFIX = "quic.path.jmh.";
    private static final String ACK_BENCHMARK = QuicPathJmhBenchmark.class.getName() + ".establishedPathAckRanges";

    static Options options(Properties properties) {
        String include = properties.getProperty(PREFIX + "include", ".*QuicPathJmhBenchmark.*");
        String result = properties.getProperty(PREFIX + "result", "./target/quic-path-jmh-result.json");
        Pattern includePattern = Pattern.compile(include);
        boolean ackSelected = includePattern.matcher(ACK_BENCHMARK).find()
                || includePattern.matcher(ACK_BENCHMARK + "Allocation").find();

        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.parseInt(properties.getProperty(PREFIX + "forks", "1")))
                .threads(Integer.parseInt(properties.getProperty(PREFIX + "threads", "4")))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.parseInt(properties.getProperty(PREFIX + "warmupIterations", "3")))
                .warmupTime(TimeValue.milliseconds(Long.parseLong(properties.getProperty(PREFIX + "warmupMillis",
                                                                                       "500"))))
                .measurementIterations(Integer.parseInt(properties.getProperty(PREFIX + "measurementIterations", "5")))
                .measurementTime(TimeValue.milliseconds(Long.parseLong(properties.getProperty(PREFIX + "measurementMillis",
                                                                                            "1000"))))
                .shouldFailOnError(true);

        String output = properties.getProperty(PREFIX + "output");
        if (output != null && !output.isBlank()) {
            optionsBuilder.output(output);
        }
        for (String name : new String[] {"connectionCount", "sendAsync", "inFlightPackets", "ackRangeCount",
                "ackPacketSpaceLifecycle", "ackWorkload"}) {
            if (properties.containsKey(PREFIX + name)) {
                optionsBuilder.param(name, parameterValues(properties, name, ""));
            }
        }
        if (ackSelected) {
            for (String flight : parameterValues(properties, "inFlightPackets", "64")) {
                for (String ranges : parameterValues(properties, "ackRangeCount", "1")) {
                    for (String lifecycle : parameterValues(properties, "ackPacketSpaceLifecycle", "REUSED")) {
                        for (String workload : parameterValues(properties, "ackWorkload", "NEW_ACK")) {
                            AckState.validateParameters(Integer.parseInt(flight),
                                                        Integer.parseInt(ranges),
                                                        AckPacketSpaceLifecycle.valueOf(lifecycle),
                                                        AckWorkload.valueOf(workload));
                        }
                    }
                }
            }
        }
        if (Boolean.parseBoolean(properties.getProperty(PREFIX + "gcProfiler", "false"))) {
            if (ackSelected) {
                throw new IllegalArgumentException("GCProfiler includes ACK fixture allocation; select "
                                                           + ACK_BENCHMARK + "Allocation for isolated ACK allocation");
            }
            optionsBuilder.addProfiler(GCProfiler.class);
        }
        return optionsBuilder.build();
    }

    @Test
    void run() throws RunnerException {
        new Runner(options(System.getProperties())).run();
    }

    private static String[] parameterValues(Properties properties, String name, String defaultValue) {
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
}
