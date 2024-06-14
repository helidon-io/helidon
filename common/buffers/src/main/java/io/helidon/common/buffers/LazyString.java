/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.nio.charset.Charset;

/**
 * String that materializes only when requested.
 */
public class LazyString {
    private static final boolean[] IS_OWS = new boolean[256];
    static {
        IS_OWS[Bytes.SPACE_BYTE] = true;
        IS_OWS[Bytes.TAB_BYTE] = true;
    }

    private final byte[] buffer;
    private final int offset;
    private final int length;
    private final Charset charset;

    private String stringValue;
    private String owsLessValue;

    /**
     * New instance.
     *
     * @param buffer buffer to use
     * @param offset offset within the buffer
     * @param length length
     * @param charset character set to construct the string
     */
    public LazyString(byte[] buffer, int offset, int length, Charset charset) {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
        this.charset = charset;
    }

    /**
     * New instance.
     *
     * @param buffer buffer to use (all bytes)
     * @param charset character set to construct the string
     */
    public LazyString(byte[] buffer, Charset charset) {
        this.buffer = buffer;
        this.offset = 0;
        this.length = buffer.length;
        this.charset = charset;
    }

    /**
     * Strip optional whitespace(s) from beginning and end of the String.
     * Defined by the HTTP specification, OWS is a sequence of zero to n space and/or horizontal tab characters.
     *
     * @return string without optional leading and trailing whitespaces
     */
    public String stripOws() {
        if (owsLessValue == null) {
            int newOffset = offset;
            int newLength = length;
            for (int i = offset; i < offset + length; i++) {
                if (IS_OWS[buffer[i]]) {
                    newOffset = i + 1;
                    newLength--;
                } else {
                    // no more white space, go from the end now
                    break;
                }
            }
            // now we need to go from the end of the string
            for (int i = offset + length - 1; i > newOffset; i--) {
                if (IS_OWS[buffer[i]]) {
                    newLength--;
                } else {
                    break;
                }
            }
            newLength = Math.max(newLength, 0);
            owsLessValue = new String(buffer, newOffset, newLength, charset);
        }

        return owsLessValue;
    }

    @Override
    public String toString() {
        if (stringValue == null) {
            stringValue = new String(buffer, offset, length, charset);
        }
        return stringValue;
    }
}
