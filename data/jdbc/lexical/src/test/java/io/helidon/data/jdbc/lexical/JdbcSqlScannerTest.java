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
package io.helidon.data.jdbc.lexical;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcSqlScannerTest {

    /**
     * Verifies that the scanner reports each marker while preserving every
     * source character through ordinary and protected regions.
     */
    @Test
    void reportsMarkersAndPreservesSourceRegions() {
        String sql = "select ':ignored', \"question?\", :name, ? /* :ignored ? */";
        RecordingHandler handler = new RecordingHandler(sql);

        JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, handler);

        assertThat(handler.source(), is(sql));
        assertThat(handler.namedMarkers(), is(List.of("name")));
        assertThat(handler.positionalMarkers(), is(List.of(39)));
        assertThat(handler.protectedRegions(),
                   is(List.of(JdbcSqlScanHandler.RegionKind.SINGLE_QUOTE,
                              JdbcSqlScanHandler.RegionKind.DOUBLE_QUOTE,
                              JdbcSqlScanHandler.RegionKind.BLOCK_COMMENT)));
    }

    /**
     * Verifies the portable punctuation and comment rules which distinguish
     * active question marks from protected question marks.
     */
    @Test
    void appliesPortablePunctuationAndCommentRules() {
        String sql = """
                select [question?], `question?`, ?, ??
                from T
                where BALANCE - -? > 0 and ID = ?
                -- ? ignored
                /* ? ignored */
                """;
        RecordingHandler handler = new RecordingHandler(sql);

        JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, handler);

        assertThat(handler.positionalMarkers().size(), is(4));
        assertThat(handler.protectedRegions(),
                   is(List.of(JdbcSqlScanHandler.RegionKind.BACKTICK_IDENTIFIER,
                              JdbcSqlScanHandler.RegionKind.LINE_COMMENT,
                              JdbcSqlScanHandler.RegionKind.BLOCK_COMMENT)));
    }

    /**
     * Verifies that PostgreSQL escape strings protect marker-shaped text and
     * retain backslash escaping across newline-separated string segments.
     */
    @Test
    void protectsPostgreSqlEscapeStrings() {
        String sql = "select E'first \\' ? :ignored'\n"
                + "       'second \\' ? :ignored', ?";
        RecordingHandler handler = new RecordingHandler(sql);

        JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, handler);

        assertThat(handler.source(), is(sql));
        assertThat(handler.namedMarkers(), is(List.of()));
        assertThat(handler.positionalMarkers(), is(List.of(sql.lastIndexOf('?'))));
        assertThat(handler.protectedRegions(), is(List.of(JdbcSqlScanHandler.RegionKind.SINGLE_QUOTE)));
    }

    /**
     * Verifies that a backslash does not change quote termination when an
     * ordinary string has no PostgreSQL escape prefix.
     */
    @Test
    void keepsOrdinarySingleQuoteSemantics() {
        String sql = "select 'ends with \\', ?";
        RecordingHandler handler = new RecordingHandler(sql);

        JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, handler);

        assertThat(handler.source(), is(sql));
        assertThat(handler.positionalMarkers(), is(List.of(sql.lastIndexOf('?'))));
    }

    /**
     * Verifies that dialect-dependent double-dash input fails at its source
     * offset without exposing the SQL text to the diagnostic.
     */
    @Test
    void rejectsAmbiguousDoubleDashSequences() {
        String sql = "select PRIVATE_VALUE--comment";

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, new RecordingHandler(sql)));

        assertThat(failure.getMessage(), containsString("Ambiguous double-dash SQL sequence"));
        assertThat(failure.getMessage(), containsString("PORTABLE"));
        assertThat(failure.getMessage(), endsWith("offset is 20."));
        assertThat(failure.getMessage(), not(containsString("PRIVATE_VALUE")));
    }

    /**
     * Verifies that complete PostgreSQL and Oracle quoted values are protected
     * only when their opening delimiters occur at a token boundary.
     */
    @Test
    void protectsCompleteVendorQuotedValues() {
        String sql = "select $$?$$, ($tag$:$name ?$tag$), q'[? :name]', identifier$tag$ where ID = ?";
        RecordingHandler handler = new RecordingHandler(sql);

        JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, handler);

        assertThat(handler.namedMarkers(), is(List.of()));
        assertThat(handler.positionalMarkers().size(), is(1));
        assertThat(handler.protectedRegions(),
                   is(List.of(JdbcSqlScanHandler.RegionKind.DOLLAR_QUOTE,
                              JdbcSqlScanHandler.RegionKind.DOLLAR_QUOTE,
                              JdbcSqlScanHandler.RegionKind.ALTERNATIVE_QUOTE)));
    }

    /**
     * Verifies every case variant of Oracle's national alternative-quote
     * prefix protects embedded apostrophes and marker-shaped text as part of
     * one complete region.
     */
    @Test
    void protectsOracleNationalAlternativeQuotedValues() {
        String sql = "select nq'[Oracle's ? :lower]', NQ'{Oracle's ? :upper}', "
                + "nQ'<Oracle's ? :mixed>', Nq'!Oracle's ? :mixed!', ?";
        RecordingHandler handler = new RecordingHandler(sql);

        JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, handler);

        assertThat(handler.source(), is(sql));
        assertThat(handler.namedMarkers(), is(List.of()));
        assertThat(handler.positionalMarkers(), is(List.of(sql.lastIndexOf('?'))));
        assertThat(handler.protectedRegions(),
                   is(List.of(JdbcSqlScanHandler.RegionKind.ALTERNATIVE_QUOTE,
                              JdbcSqlScanHandler.RegionKind.ALTERNATIVE_QUOTE,
                              JdbcSqlScanHandler.RegionKind.ALTERNATIVE_QUOTE,
                              JdbcSqlScanHandler.RegionKind.ALTERNATIVE_QUOTE)));
    }

    /**
     * Verifies an {@code nq} sequence embedded in an identifier is not
     * promoted to an Oracle alternative-quote opener.
     */
    @Test
    void appliesTokenBoundaryToOracleNationalAlternativeQuotes() {
        String sql = "select identifiernq'[first' ? 'second]'";
        RecordingHandler handler = new RecordingHandler(sql);

        JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, handler);

        assertThat(handler.positionalMarkers(), is(List.of(sql.indexOf('?'))));
        assertThat(handler.protectedRegions(),
                   is(List.of(JdbcSqlScanHandler.RegionKind.SINGLE_QUOTE,
                              JdbcSqlScanHandler.RegionKind.SINGLE_QUOTE)));
    }

    /**
     * Verifies that casts and assignment operators remain ordinary SQL while a
     * Java identifier after a colon is reported as a named marker.
     */
    @Test
    void distinguishesNamedMarkersFromColonOperators() {
        String sql = "select :value::jsonb where VERSION := VERSION and ID = :id";
        RecordingHandler handler = new RecordingHandler(sql);

        JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, handler);

        assertThat(handler.namedMarkers(), is(List.of("value", "id")));
        assertThat(handler.source(), is(sql));
    }

    /**
     * Verifies that malformed protected regions report a safe profile and
     * source offset without including the SQL source.
     */
    @Test
    void reportsMalformedRegionsWithoutSqlText() {
        String sql = "select /* outer /* nested */ outer */";

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, new RecordingHandler(sql)));

        assertThat(failure.getMessage(), containsString("Nested block comments are not supported"));
        assertThat(failure.getMessage(), containsString("PORTABLE"));
        assertThat(failure.getMessage(), endsWith("offset is 16."));
        assertThat(failure.getMessage(), not(containsString(sql)));
    }

    private static final class RecordingHandler implements JdbcSqlScanHandler {

        private final String source;
        private final StringBuilder reconstructed = new StringBuilder();
        private final List<String> namedMarkers = new ArrayList<>();
        private final List<Integer> positionalMarkers = new ArrayList<>();
        private final List<RegionKind> protectedRegions = new ArrayList<>();

        private RecordingHandler(String source) {
            this.source = source;
        }

        @Override
        public void ordinary(int start, int end) {
            reconstructed.append(source, start, end);
        }

        @Override
        public void protectedRegion(RegionKind kind, int start, int end) {
            protectedRegions.add(kind);
            reconstructed.append(source, start, end);
        }

        @Override
        public void namedMarker(int start, int end) {
            namedMarkers.add(source.substring(start + 1, end));
            reconstructed.append(source, start, end);
        }

        @Override
        public void positionalMarker(int offset) {
            positionalMarkers.add(offset);
            reconstructed.append(source.charAt(offset));
        }

        private String source() {
            return reconstructed.toString();
        }

        private List<String> namedMarkers() {
            return List.copyOf(namedMarkers);
        }

        private List<Integer> positionalMarkers() {
            return List.copyOf(positionalMarkers);
        }

        private List<RegionKind> protectedRegions() {
            return List.copyOf(protectedRegions);
        }
    }
}
