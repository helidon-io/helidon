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

package io.helidon.tests.benchmark.jmh.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.config.ObjectNodeImpl;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.MergingStrategy;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Merges prebuilt source trees using the fallback strategy. Each source has {@code keyCount} value nodes.
 * Flat sources have disjoint top-level keys. Nested sources share service objects: half their leaves have
 * matching names across sources and half have source-specific names. The first source wins matching values,
 * while source-specific leaves accumulate under the shared objects.
 * Source construction and fixture validation take place before measurement; each operation returns a new merged tree.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
@SuppressWarnings("removal")
public class ConfigNodeMergingJmhBenchmark {
    /**
     * Number of ordered sources; the first has the highest priority.
     */
    @Param({"1", "10", "100"})
    public int sourceCount;

    /**
     * Number of value nodes in each source.
     */
    @Param({"10", "100"})
    public int keyCount;

    /**
     * Disjoint top-level keys or partially overlapping nested service paths.
     */
    @Param({"DISJOINT_FLAT", "OVERLAPPING_NESTED"})
    public String shape;

    private MergingStrategy strategy;
    private List<ObjectNode> sources;

    /**
     * Constructs the source trees and verifies merged cardinality, source priority, and provenance outside measurement.
     */
    @Setup(Level.Trial)
    public void setUp() {
        if (sourceCount < 1 || keyCount < 2 || keyCount % 2 != 0) {
            throw new IllegalArgumentException("Positive sourceCount and positive even keyCount are required");
        }
        if (!shape.equals("DISJOINT_FLAT") && !shape.equals("OVERLAPPING_NESTED")) {
            throw new IllegalArgumentException("Unsupported shape: " + shape);
        }
        strategy = MergingStrategy.fallback();
        List<ObjectNode> prepared = new ArrayList<>(sourceCount);
        for (int source = 0; source < sourceCount; source++) {
            ObjectNode.Builder builder = ObjectNode.builder();
            for (int key = 0; key < keyCount; key++) {
                builder.addValue(key(source, key), "value-" + source + "-" + key);
            }
            prepared.add(ObjectNodeImpl.wrap(builder.build()).initDescription("source-" + source));
        }
        sources = List.copyOf(prepared);

        verifyMerged(strategy.merge(sources));
        for (int source = 0; source < sourceCount; source++) {
            ObjectNode input = sources.get(source);
            if (!input.description().equals("source-" + source)) {
                throw new IllegalStateException("Merge changed source root description: " + source);
            }
            for (int key = 0; key < keyCount; key++) {
                verifyValue(node(input, key(source, key)), source, key);
            }
        }
    }

    /**
     * Merges the prebuilt trees, returning the complete result for JMH consumption.
     *
     * @return merged tree
     */
    @Benchmark
    public ObjectNode merge() {
        return strategy.merge(sources);
    }

    private static ConfigNode node(ObjectNode root, String path) {
        ConfigNode current = root;
        for (String name : path.split("\\.")) {
            current = ((ObjectNode) current).get(name);
        }
        return current;
    }

    private static void verifyValue(ConfigNode node, int source, int key) {
        Optional<String> expectedValue = Optional.of("value-" + source + "-" + key);
        String expectedSource = "source-" + source;
        if (!node.value().equals(expectedValue) || !node.description().equals(expectedSource)) {
            throw new IllegalStateException("Expected " + expectedValue + " from " + expectedSource
                                                    + ", got " + node.value() + " from " + node.description());
        }
    }

    private String key(int source, int key) {
        return switch (shape) {
        case "DISJOINT_FLAT" -> "source-" + source + "-key-" + key;
        case "OVERLAPPING_NESTED" -> "services.service-" + key / 2 + "."
                + (key % 2 == 0 ? "shared" : "source-" + source);
        default -> throw new IllegalArgumentException("Unsupported shape: " + shape);
        };
    }

    private void verifyMerged(ObjectNode merged) {
        String expectedRootSource = sourceCount == 1 ? "source-0" : "N/A";
        if (!merged.description().equals(expectedRootSource)) {
            throw new IllegalStateException("Unexpected merged root description: " + merged.description());
        }
        if (shape.equals("DISJOINT_FLAT")) {
            if (merged.size() != sourceCount * keyCount) {
                throw new IllegalStateException("Unexpected flat output size: " + merged.size());
            }
            for (int source = 0; source < sourceCount; source++) {
                for (int key = 0; key < keyCount; key++) {
                    verifyValue(merged.get(key(source, key)), source, key);
                }
            }
        } else {
            ObjectNode services = (ObjectNode) merged.get("services");
            if (merged.size() != 1 || services.size() != keyCount / 2) {
                throw new IllegalStateException("Unexpected nested service count: " + services.size());
            }
            for (int service = 0; service < keyCount / 2; service++) {
                ObjectNode values = (ObjectNode) services.get("service-" + service);
                if (values.size() != sourceCount + 1) {
                    throw new IllegalStateException("Unexpected service leaf count: " + values.size());
                }
                verifyValue(values.get("shared"), 0, service * 2);
                for (int source = 0; source < sourceCount; source++) {
                    verifyValue(values.get("source-" + source), source, service * 2 + 1);
                }
            }
        }
    }

}
