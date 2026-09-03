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
package io.helidon.data.jdbc.codegen;

import java.util.ArrayList;
import java.util.List;

import io.helidon.data.jdbc.lexical.JdbcSqlScanHandler;
import io.helidon.data.jdbc.lexical.JdbcSqlScanner;

/**
 * Converts declarative named markers to JDBC positional markers.
 * <p>
 * The shared scanner recognizes SQL regions and marker boundaries. This
 * adapter preserves the SQL source, records marker names in encounter order,
 * and rejects declarative marker combinations which cannot produce one JDBC
 * bind plan.
 */
final class JdbcSqlMarkerLexer implements JdbcSqlScanHandler {

    private final String source;
    private final StringBuilder jdbcSql;
    private final List<String> markers = new ArrayList<>();
    private boolean named;
    private boolean positional;

    private JdbcSqlMarkerLexer(String source) {
        this.source = source;
        this.jdbcSql = new StringBuilder(source.length());
    }

    /**
     * Parses one statement and returns its positional JDBC form.
     *
     * @param sql SQL statement
     * @return marker plan
     */
    static Result parse(String sql) {
        JdbcSqlMarkerLexer lexer = new JdbcSqlMarkerLexer(sql);
        JdbcSqlScanner.scan(sql, lexer);
        if (lexer.named && lexer.positional) {
            throw malformed("Declarative SQL cannot mix named and positional markers", sql.length());
        }
        MarkerStyle style = lexer.named
                ? MarkerStyle.NAMED
                : lexer.positional ? MarkerStyle.POSITIONAL : MarkerStyle.NONE;
        return new Result(lexer.jdbcSql.toString(), List.copyOf(lexer.markers), style);
    }

    @Override
    public void ordinary(int start, int end) {
        jdbcSql.append(source, start, end);
    }

    @Override
    public void protectedRegion(RegionKind kind, int start, int end) {
        jdbcSql.append(source, start, end);
    }

    @Override
    public void namedMarker(int start, int end) {
        if (end < source.length() && source.charAt(end) == '.') {
            throw malformed("Dotted named parameters are not supported", start);
        }
        // The source substring is kept before rewriting so diagnostics and binding still use the Java parameter name.
        markers.add(source.substring(start + 1, end));
        named = true;
        jdbcSql.append('?');
    }

    @Override
    public void positionalMarker(int offset) {
        // Positional statements only need a physical marker count, so an empty entry avoids storing SQL text.
        markers.add("");
        positional = true;
        jdbcSql.append('?');
    }

    private static IllegalArgumentException malformed(String problem, int offset) {
        return new IllegalArgumentException(problem + ". The lexical profile is PORTABLE"
                                                    + ", and the SQL offset is " + offset + ".");
    }

    /**
     * Marker syntax used by a statement.
     */
    enum MarkerStyle {

        NONE,
        NAMED,
        POSITIONAL
    }

    /**
     * Rewritten SQL and ordered marker information.
     *
     * @param sql positional JDBC SQL
     * @param markers named markers or empty positional entries
     * @param style marker style
     */
    record Result(String sql, List<String> markers, MarkerStyle style) {
    }
}
