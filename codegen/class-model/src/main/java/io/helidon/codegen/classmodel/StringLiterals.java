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

package io.helidon.codegen.classmodel;

import java.util.Objects;

final class StringLiterals {
    private StringLiterals() {
    }

    static String escape(String literal) {
        Objects.requireNonNull(literal, "literal is null");

        StringBuilder result = new StringBuilder(literal.length());
        for (int i = 0; i < literal.length(); i++) {
            char ch = literal.charAt(i);
            switch (ch) {
            case '\b' -> result.append("\\b");
            case '\t' -> result.append("\\t");
            case '\n' -> result.append("\\n");
            case '\f' -> result.append("\\f");
            case '\r' -> result.append("\\r");
            case '"' -> result.append("\\\"");
            case '\\' -> result.append("\\\\");
            default -> {
                if (ch < ' ') {
                    result.append(String.format("\\u%04x", (int) ch));
                } else {
                    result.append(ch);
                }
            }
            }
        }
        return result.toString();
    }
}
