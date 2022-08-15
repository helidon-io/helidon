/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.buffers;

/**
 * Bytes commonly used in HTTP.
 */
public final class Bytes {
    /**
     * {@code :} byte.
     */
    public static final byte COLON_BYTE = (byte) ':';
    /**
     * {@code  } (space) byte.
     */
    public static final byte SPACE_BYTE = (byte) ' ';
    /**
     * {@code \n} (new line) byte.
     */
    public static final byte LF_BYTE = (byte) '\n';
    /**
     * {@code \r} (carriage return) byte.
     */
    public static final byte CR_BYTE = (byte) '\r';
    /**
     * {@code /} byte.
     */
    public static final byte SLASH_BYTE = (byte) '/';
    /**
     * {@code ;} byte.
     */
    public static final byte SEMICOLON_BYTE = (byte) ';';
    /**
     * {@code ?} byte.
     */
    public static final byte QUESTION_MARK_BYTE = (byte) '?';
    /**
     * {@code #} byte.
     */
    public static final byte HASH_BYTE = (byte) '#';
    /**
     * {@code =} byte.
     */
    public static final byte EQUALS_BYTE = (byte) '=';
    /**
     * {@code &} byte.
     */
    public static final byte AMPERSAND_BYTE = (byte) '&';
    /**
     * {@code %} byte.
     */
    public static final byte PERCENT_BYTE = (byte) '%';

    private Bytes() {
    }
}
