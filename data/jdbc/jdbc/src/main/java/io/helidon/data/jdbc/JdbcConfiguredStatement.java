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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.data.jdbc.lexical.JdbcSqlScanHandler;
import io.helidon.data.jdbc.lexical.JdbcSqlScanner;

/**
 * Retains immutable rewritten SQL and physical positions for a configured definition.
 */
final class JdbcConfiguredStatement {

    private final String sql;
    private final int parameterCount;
    private final Map<String, List<Integer>> positions;

    private JdbcConfiguredStatement(String sql, int parameterCount, Map<String, List<Integer>> positions) {
        this.sql = sql;
        this.parameterCount = parameterCount;
        this.positions = Map.copyOf(positions);
    }

    static Map<String, JdbcConfiguredStatement> catalog(Map<String, String> definitions) {
        Map<String, JdbcConfiguredStatement> catalog = new HashMap<>();
        definitions.forEach((name, source) -> {
            Objects.requireNonNull(name, "The configured statement name must not be null.");
            Objects.requireNonNull(source, "The configured SQL statement must not be null.");
            if (name.isBlank() || source.isBlank()) {
                throw new IllegalArgumentException("Configured statement names and SQL must not be blank.");
            }
            PlanBuilder builder = new PlanBuilder(source);
            JdbcSqlScanner.scan(source, builder);
            Map<String, List<Integer>> positions = new HashMap<>();
            builder.positions.forEach((marker, slots) -> positions.put(marker, List.copyOf(slots)));
            catalog.put(name, new JdbcConfiguredStatement(builder.sql.toString(), builder.parameterCount, positions));
        });
        return Map.copyOf(catalog);
    }

    String sql() {
        return sql;
    }

    int parameterCount() {
        return parameterCount;
    }

    List<Integer> positions(String name) {
        List<Integer> slots = positions.get(name);
        if (slots == null) {
            throw new IllegalArgumentException("The bind name does not identify a configured marker.");
        }
        return slots;
    }

    private static final class PlanBuilder implements JdbcSqlScanHandler {

        private final String source;
        private final StringBuilder sql;
        private final Map<String, List<Integer>> positions = new HashMap<>();
        private int parameterCount;

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
                throw new IllegalArgumentException("Configured SQL does not support dotted marker names.");
            }
            positions.computeIfAbsent(source.substring(start + 1, end), _ -> new ArrayList<>()).add(++parameterCount);
            sql.append('?');
        }

        @Override
        public void positionalMarker(int offset) {
            throw new IllegalArgumentException("Configured SQL does not support positional markers.");
        }
    }
}
