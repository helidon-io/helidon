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

package io.helidon.webserver.benchmark.jmh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriQueryWriteable;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class UriQueryJmhTest {
    @Param({"1", "8", "64", "256", "1024"})
    private int count;

    @Param({"pairs", "flags", "encoded", "duplicates", "emptysegments", "unicode"})
    private String shape;

    private String query;
    private String rawName;

    @Setup
    public void setup() {
        if (count <= 0) {
            throw new IllegalArgumentException("Query parameter count must be positive: " + count);
        }
        var text = new StringBuilder();
        Map<String, List<String>> expectedRaw = new HashMap<>();
        Map<String, List<String>> expectedDecoded = new HashMap<>();
        Map<String, List<String>> expectedWriteableRaw = new HashMap<>();
        Map<String, List<String>> expectedWriteableDecoded = new HashMap<>();
        boolean emptySegments = shape.equals("emptysegments");
        boolean flags = shape.equals("flags");

        if (emptySegments) {
            text.append("&&");
        }
        for (int i = 0; i < count; i++) {
            if (i != 0) {
                text.append(emptySegments ? "&&" : "&");
            }
            String name = switch (shape) {
                case "encoded" -> "%6B" + i;
                case "duplicates" -> "k";
                case "unicode" -> "klíč" + i;
                case "pairs", "flags", "emptysegments" -> "k" + i;
                default -> throw new IllegalArgumentException("Unknown query shape: " + shape);
            };
            String value = switch (shape) {
                case "flags" -> "";
                case "encoded" -> "a%20b%26c%3Dd";
                case "unicode" -> "žluťoučký😀";
                default -> "value" + i;
            };
            String decodedName = shape.equals("encoded") ? "k" + i : name;
            String decodedValue = shape.equals("encoded") ? "a b&c=d" : value;
            text.append(name);
            if (!flags) {
                text.append('=').append(value);
            }
            if (i == 0) {
                rawName = name;
            }
            addExpected(expectedRaw, name, value, !flags);
            addExpected(expectedDecoded, decodedName, decodedValue, !flags);
            addExpected(expectedWriteableRaw, name, value, true);
            addExpected(expectedWriteableDecoded, decodedName, decodedValue, true);
        }
        if (emptySegments) {
            // Two leading and trailing delimiters and doubled separators retain count + 3 empty segments.
            text.append("&&");
            expectedRaw.put("", List.of());
            expectedDecoded.put("", List.of());
            expectedWriteableRaw.put("", Collections.nCopies(count + 3, ""));
            expectedWriteableDecoded.put("", Collections.nCopies(count + 3, ""));
        }
        query = text.toString();

        UriQuery raw = immutableRaw();
        verify("raw text", query, raw.rawValue());
        verifyQuery("immutable raw", raw, expectedRaw, expectedDecoded);
        verifyQuery("immutable decoded", immutableDecoded(), expectedRaw, expectedDecoded);
        verifyQuery("writeable", writeable(), expectedWriteableRaw, expectedWriteableDecoded);
    }

    @Benchmark
    public UriQuery immutableRaw() {
        UriQuery parsed = UriQuery.create(query);
        parsed.getAllRaw(rawName);
        return parsed;
    }

    @Benchmark
    public UriQuery immutableDecoded() {
        UriQuery parsed = UriQuery.create(query);
        parsed.size();
        return parsed;
    }

    @Benchmark
    public UriQueryWriteable writeable() {
        UriQueryWriteable parsed = UriQueryWriteable.create();
        parsed.fromQueryString(query);
        return parsed;
    }

    private static void addExpected(Map<String, List<String>> expected, String name, String value, boolean hasValue) {
        List<String> values = expected.computeIfAbsent(name, _ -> new ArrayList<>());
        if (hasValue) {
            values.add(value);
        }
    }

    private void verifyQuery(String path,
                             UriQuery parsed,
                             Map<String, List<String>> expectedRaw,
                             Map<String, List<String>> expectedDecoded) {
        expectedRaw.forEach((name, values) -> verify(path + " raw " + name, values, parsed.getAllRaw(name)));
        verify(path + " names", expectedDecoded.keySet(), parsed.names());
        verify(path + " size", expectedDecoded.size(), parsed.size());
        expectedDecoded.forEach((name, values) -> verify(path + " decoded " + name, values, parsed.all(name)));
    }

    private void verify(String description, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(description + " for " + shape + " with " + count
                                                    + " parameters: expected " + expected + ", actual " + actual);
        }
    }
}
