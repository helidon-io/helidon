/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.ArrayList;
import java.util.List;

/**
 * Headers utility methods.
 */
final class HeaderHelper {
    private static final char QUOTE = '"';

    private HeaderHelper() {
    }

    /**
     * Tokenize provide {@code text} by {@code separator} char respecting quoted sub-sequences. Quoted sub-sequences are
     * parts of {@code text} which starts and ends by {@code "} character.
     * Empty tokens are not returned.
     *
     * @param separator a token separator.
     * @param text      a text to be tokenized.
     * @return A list of tokens without separator characters.
     */
    public static List<String> tokenize(char separator, String text) {
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
                    if (!token.isEmpty()) {
                        result.add(token.toString());
                    }
                    token.setLength(0);
                } else {
                    if (ch == QUOTE) {
                        quoted = true;
                        lastQuoteCharacter = ch;
                    }
                    token.append(ch);
                }
            }
        }
        if (!token.isEmpty()) {
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
        if (str.length() >= 2 && QUOTE == str.charAt(0) && QUOTE == str.charAt(str.length() - 1)) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }
}
