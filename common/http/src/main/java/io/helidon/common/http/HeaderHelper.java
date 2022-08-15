/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.common.http;

import java.util.ArrayList;
import java.util.List;

/**
 * Headers utility methods.
 */
final class HeaderHelper {
    static final Runnable EMPTY_RUNNABLE = () -> {
    };

    private HeaderHelper() {
    }

    /**
     * Tokenize provide {@code text} by {@code separator} char respecting quoted sub-sequences. Quoted sub-sequences are
     * parts of {@code text} which starts and ends by the same {@code quoteChar}.
     *
     * @param separator          a token separator.
     * @param quoteChars         characters which can be used for quoting. Quoted part must start and ends with the same
     *                           character.
     * @param includeEmptyTokens return also tokens with {@code length == 0}.
     * @param text               a text to be tokenized.
     * @return A list of tokens without separator characters.
     */
    public static List<String> tokenize(char separator, String quoteChars, boolean includeEmptyTokens, String text) {
        char[] quotes = quoteChars == null ? new char[0] : quoteChars.toCharArray();
        StringBuilder token = new StringBuilder();
        List<String> result = new ArrayList<>();
        boolean quoted = false;
        char lastQuoteCharacter = ' ';
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == lastQuoteCharacter) {
                    quoted = false;
                }
                token.append(ch);
            } else {
                if (ch == separator) {
                    if (includeEmptyTokens || token.length() > 0) {
                        result.add(token.toString());
                    }
                    token.setLength(0);
                } else {
                    for (char quote : quotes) {
                        if (ch == quote) {
                            quoted = true;
                            lastQuoteCharacter = ch;
                            break;
                        }
                    }
                    token.append(ch);
                }
            }
        }
        if (includeEmptyTokens || token.length() > 0) {
            result.add(token.toString());
        }
        return result;
    }

    /**
     * Unwrap from double-quotes - if exists.
     *
     * @param str string to unwrap.
     * @return unwrapped string.
     */
    public static String unwrap(String str) {
        if (str.length() >= 2 && '"' == str.charAt(0) && '"' == str.charAt(str.length() - 1)) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }
}
