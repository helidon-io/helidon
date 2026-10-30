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

import java.util.Objects;

import io.helidon.data.jdbc.lexical.JdbcSqlScanHandler;
import io.helidon.data.jdbc.lexical.JdbcSqlScanner;

/**
 * Applies the portable lexical policy without parsing server SQL grammar.
 */
final class JdbcHealthQuery implements JdbcSqlScanHandler {
    private final String query;
    private final StringBuilder visible = new StringBuilder();
    private int separator = -1;

    private JdbcHealthQuery(String query) {
        this.query = query;
    }

    static String validate(String query) {
        JdbcHealthQuery handler = new JdbcHealthQuery(query);
        JdbcSqlScanner.scan(query, handler);
        String text = handler.visible.toString().strip();
        if (text.length() < 6 || !text.regionMatches(true, 0, "SELECT", 0, 6)
                || (text.length() > 6 && Character.isJavaIdentifierPart(text.codePointAt(6)))) {
            throw new IllegalArgumentException("The probe query must begin with SELECT after whitespace and comments.");
        }
        if (handler.separator >= 0 && !text.endsWith(";")) {
            throw new IllegalArgumentException("The probe query permits only one trailing statement separator.");
        }
        // Remove the optional terminator because some JDBC drivers reject it in a prepared statement.
        return handler.separator < 0 ? query : query.substring(0, handler.separator) + query.substring(handler.separator + 1);
    }

    @Override
    public void ordinary(int start, int end) {
        for (int index = start; index < end; index++) {
            char value = query.charAt(index);
            if (value == ';') {
                if (separator >= 0) {
                    throw new IllegalArgumentException("The probe query permits only one trailing statement separator.");
                }
                separator = index;
            }
            visible.append(value);
        }
    }

    @Override
    public void protectedRegion(RegionKind kind, int start, int end) {
        Objects.requireNonNull(kind, "The protected region kind must not be null.");
        visible.append(kind == RegionKind.LINE_COMMENT || kind == RegionKind.BLOCK_COMMENT ? ' ' : '#');
    }

    @Override
    public void namedMarker(int start, int end) {
        throw new IllegalArgumentException("The probe query must not contain bind markers.");
    }

    @Override
    public void positionalMarker(int offset) {
        throw new IllegalArgumentException("The probe query must not contain bind markers.");
    }
}
