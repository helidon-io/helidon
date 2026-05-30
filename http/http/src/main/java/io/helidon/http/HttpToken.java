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
 * Token is defined by the HTTP specification and must not contain a set of characters.
 */
public final class HttpToken {
    private HttpToken() {
    }

    /**
     * Validate if this is a good HTTP token.
     *
     * @param token token to validate
     * @throws IllegalArgumentException in case the token is not valid
     */
    public static void validate(String token) throws IllegalArgumentException {
        Objects.requireNonNull(token);
        for (int i = 0; i < token.length(); i++) {
            char aChar = token.charAt(i);
            if (aChar > 254) {
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
            switch (aChar) {
            case '(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']', '?', '=', '{', '}' -> {
                throw new IllegalArgumentException("Token contains illegal character at position "
                                                           + hex(i)
                                                           + "\n"
                                                           + debugToken(token));
            }
            default -> {
                // this is a valid character
            }
            }
        }
    }

    /**
     * Whether this is a valid HTTP token.
     *
     * @param token token to check
     * @return whether the token is valid
     */
    public static boolean isValid(String token) {
        Objects.requireNonNull(token);
        return isValid(token, 0, token.length());
    }

    /**
     * Whether this is a valid HTTP token.
     *
     * @param token token to check
     * @param start token start offset, inclusive
     * @param end token end offset, exclusive
     * @return whether the token is valid
     */
    public static boolean isValid(String token, int start, int end) {
        Objects.requireNonNull(token);
        Objects.checkFromToIndex(start, end, token.length());
        for (int i = start; i < end; i++) {
            if (!isTokenCharacter(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String hex(int i) {
        return Integer.toHexString(i);
    }

    private static boolean isTokenCharacter(char aChar) {
        if (aChar > 254 || Character.isISOControl(aChar) || Character.isWhitespace(aChar)) {
            return false;
        }
        return switch (aChar) {
        case '(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']', '?', '=', '{', '}' -> false;
        default -> true;
        };
    }

    private static String debugToken(String token) {
        return BufferData.create(token.getBytes(StandardCharsets.US_ASCII))
                .debugDataHex();
    }
}
