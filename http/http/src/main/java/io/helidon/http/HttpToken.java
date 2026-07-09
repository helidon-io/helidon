/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.helidon.common.buffers.BufferData;

/**
 * HTTP Token utility.
 * A token is defined by the HTTP specification as one or more ASCII {@code tchar} characters.
 */
public final class HttpToken {
    private HttpToken() {
    }

    /**
     * Validate an HTTP token.
     *
     * @param token token to validate
     * @throws IllegalArgumentException in case the token is not valid
     */
    public static void validate(String token) throws IllegalArgumentException {
        Objects.requireNonNull(token);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Token must not be empty");
        }
        for (int i = 0; i < token.length(); i++) {
            char aChar = token.charAt(i);
            if (aChar > 127) {
                throw new IllegalArgumentException("Token contains non-ASCII character at position "
                                                           + hex(i)
                                                           + " \n"
                                                           + debugToken(token));
            }
            if (Character.isISOControl(aChar)) {
                throw new IllegalArgumentException("Token contains control character at position "
                                                           + hex(i)
                                                           + "\n"
                                                           + debugToken(token));
            }
            if (Character.isWhitespace(aChar)) {
                throw new IllegalArgumentException("Token contains whitespace character at position "
                                                           + hex(i)
                                                           + "\n"
                                                           + debugToken(token));
            }
            boolean valid = (aChar >= '0' && aChar <= '9')
                    || (aChar >= 'A' && aChar <= 'Z')
                    || (aChar >= 'a' && aChar <= 'z')
                    || switch (aChar) {
                        case '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~' -> true;
                        default -> false;
                    };
            if (!valid) {
                throw new IllegalArgumentException("Token contains illegal character at position "
                                                           + hex(i)
                                                           + "\n"
                                                           + debugToken(token));
            }
        }
    }

    private static String hex(int i) {
        return Integer.toHexString(i);
    }

    private static String debugToken(String token) {
        return BufferData.create(token.getBytes(StandardCharsets.US_ASCII))
                .debugDataHex();
    }
}
