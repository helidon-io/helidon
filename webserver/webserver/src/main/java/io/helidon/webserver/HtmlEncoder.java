/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

/**
 * HTML encoding of special characters to prevent cross site scripting (XSS) attacks.
 * Any data that is "echoed" back from a request can be used to execute a script in a
 * browser unless properly encoded.
 */
public final class HtmlEncoder {

    private HtmlEncoder() {
    }

    /**
     * Encode HTML string replacing the special characters by their corresponding
     * entities.
     *
     * @param s string to encode.
     * @return encoded string.
     */
    public static String encode(String s) {
        int n = s.length();
        StringBuilder result = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    result.append("&amp;");
                    break;
                case '<':
                    result.append("&lt;");
                    break;
                case '>':
                    result.append("&gt;");
                    break;
                case '"':
                    result.append("&quot;");
                    break;
                case '\'':
                    result.append("&#x27;");
                    break;
                default:
                    result.append(c);
                    break;
            }
        }
        return result.toString();
    }
}
