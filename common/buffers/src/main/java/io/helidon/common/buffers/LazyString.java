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

import java.nio.charset.Charset;

/**
 * String that materializes only when requested.
 */
public class LazyString {
    private final byte[] buffer;
    private final int offset;
    private final int length;
    private final Charset charset;

    private String stringValue;

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

    @Override
    public String toString() {
        if (stringValue == null) {
            stringValue = new String(buffer, offset, length, charset);
        }
        return stringValue;
    }
}
