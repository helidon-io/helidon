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
package io.helidon.data.jdbc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.data.jdbc.lexical.JdbcSqlScanHandler;
import io.helidon.data.jdbc.lexical.JdbcSqlScanner;

/**
 * Retains immutable rewritten SQL and physical positions for a configured definition.
 */
final class JdbcConfiguredStatement {

    private final String sql;
    private final int parameterCount;
    private final boolean namedMarkers;
    private final boolean leadingReturn;
    private final Map<String, List<Integer>> positions;

    private JdbcConfiguredStatement(String sql, int parameterCount, Map<String, List<Integer>> positions, boolean namedMarkers,
                                    boolean leadingReturn) {
        this.sql = sql;
        this.parameterCount = parameterCount;
        this.namedMarkers = namedMarkers;
        this.leadingReturn = leadingReturn;
        this.positions = Map.copyOf(positions);
    }

    static JdbcConfiguredStatement create(String source) {
        if (source.isBlank()) {
            throw new IllegalArgumentException("The SQL statement must not be blank.");
        }
        PlanBuilder builder = new PlanBuilder(source);
        JdbcSqlScanner.scan(source, builder);
        boolean leadingReturn = builder.firstPosition >= 0
                && source.substring(0, builder.firstPosition).matches("(?is)\\s*\\{\\s*")
                && source.substring(builder.firstPosition + 1).matches("(?is)\\s*=\\s*call\\b.*");
        if (builder.named && builder.positionalCount > 0 && !(leadingReturn && builder.positionalCount == 1)) {
            throw new IllegalArgumentException("The SQL statement must not mix named and positional markers.");
        }
        if (leadingReturn && builder.positions.putIfAbsent("return", List.of(1)) != null) {
            throw new IllegalArgumentException("A leading callable return reserves the logical name return.");
        }
        Map<String, List<Integer>> positions = new HashMap<>();
        builder.positions.forEach((marker, slots) -> positions.put(marker, List.copyOf(slots)));
        return new JdbcConfiguredStatement(builder.sql.toString(), builder.parameterCount, positions,
                                           builder.named, leadingReturn);
    }

    static JdbcConfiguredStatement create(String source, List<String> inputNames) {
        Set<String> names = new HashSet<>();
        for (String name : inputNames) {
            if (name.isEmpty() || !Character.isJavaIdentifierStart(name.codePointAt(0))
                    || name.codePoints().anyMatch(character -> !Character.isJavaIdentifierPart(character))
                    || !names.add(name)) {
                throw new IllegalArgumentException(
                        "The input names must follow Java identifier character rules and must be unique.");
            }
        }
        JdbcConfiguredStatement definition = create(source);
        if (definition.namedMarkers) {
            if (!names.equals(definition.positions.keySet())) {
                throw new IllegalArgumentException("The SQL marker names must match the required input names exactly.");
            }
            return definition;
        }
        if (definition.leadingReturn && (inputNames.isEmpty() || !inputNames.getFirst().equals("return"))) {
            throw new IllegalArgumentException("A leading callable return requires return as the first layout name.");
        }
        if (definition.parameterCount != inputNames.size()) {
            throw new IllegalArgumentException("The positional marker count must match the required input count.");
        }
        Map<String, List<Integer>> positions = new HashMap<>();
        for (int index = 0; index < inputNames.size(); index++) {
            positions.put(inputNames.get(index), List.of(index + 1));
        }
        return new JdbcConfiguredStatement(definition.sql, definition.parameterCount, positions, false, definition.leadingReturn);
    }

    static JdbcConfiguredStatement createCall(String source, List<String> logicalNames,
                                               boolean namedOnly, boolean leadingReturn) {
        JdbcConfiguredStatement definition = create(source, logicalNames);
        if (namedOnly && !definition.namedMarkers) {
            throw new IllegalArgumentException("The repository parameter contract requires named SQL markers.");
        }
        if (definition.leadingReturn != leadingReturn) {
            throw new IllegalArgumentException(leadingReturn
                    ? "The configured callable SQL requires a leading return slot."
                    : "The configured callable SQL must not contain a leading return slot.");
        }
        return definition;
    }

    String sql() {
        return sql;
    }

    int parameterCount() {
        return parameterCount;
    }

    boolean namedMarkers() {
        return namedMarkers;
    }

    boolean leadingReturn() {
        return leadingReturn;
    }

    void validateOutputPosition(int index) {
        if (positions.values().stream().anyMatch(slots -> slots.size() > 1 && slots.contains(index))) {
            throw new IllegalArgumentException("An OUT or INOUT marker must occur exactly once.");
        }
    }

    List<Integer> positions(String name) {
        List<Integer> slots = positions.get(name);
        if (slots == null) {
            throw new IllegalArgumentException("The bind name does not match a required input.");
        }
        return slots;
    }

    private static final class PlanBuilder implements JdbcSqlScanHandler {

        private final String source;
        private final StringBuilder sql;
        private final Map<String, List<Integer>> positions = new HashMap<>();
        private int parameterCount;
        private boolean named;
        private int positionalCount;
        private int firstPosition = -1;

        private PlanBuilder(String source) {
            this.source = source;
            this.sql = new StringBuilder(source.length());
        }

        @Override
        public void ordinary(int start, int end) {
            sql.append(source, start, end);
        }

        @Override
        public void protectedRegion(RegionKind kind, int start, int end) {
            Objects.requireNonNull(kind, "The protected region kind must not be null.");
            sql.append(source, start, end);
        }

        @Override
        public void namedMarker(int start, int end) {
            if (end < source.length() && source.charAt(end) == '.') {
                throw new IllegalArgumentException("The SQL statement does not support dotted marker names.");
            }
            positions.computeIfAbsent(source.substring(start + 1, end), _ -> new ArrayList<>()).add(++parameterCount);
            named = true;
            sql.append('?');
        }

        @Override
        public void positionalMarker(int offset) {
            if (firstPosition < 0) {
                firstPosition = offset;
            }
            positionalCount++;
            parameterCount++;
            sql.append('?');
        }
    }
}
