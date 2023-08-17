/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.http.media;

import java.io.OutputStream;
import java.util.OptionalLong;

/**
 * A writer dedicated to a specific instance. Method {@link #write(java.io.OutputStream)} will write the instance
 * to the output stream, method {@link #instanceBytes()} will provide all the bytes of entity.
 * The caller decided which method to call depending on results of {@link #alwaysInMemory()} and {@link #contentLength()}.
 */
public interface InstanceWriter {
    /**
     * If we can determine the number of bytes to be written to the stream, provide the information here.
     * The returned number must be a valid content length (content-length >= 0)
     *
     * @return number of bytes or empty if not possible (or too expensive) to find out
     */
    OptionalLong contentLength();

    /**
     * Whether the byte array is always available. If true {@link #instanceBytes()} will ALWAYS be called.
     *
     * @return whether the bytes will always be materialized in memory
     */
    boolean alwaysInMemory();

    /**
     * Write the instance to the output stream. This method is NEVER called if {@link #alwaysInMemory()} is {@code true},
     * otherwise this method is ALWAYS called if {@link #contentLength()} returns empty.
     * This method MAY be called if {@link #contentLength()} returns a value.
     *
     * @param stream to write to
     */
    void write(OutputStream stream);

    /**
     * Get the instance as byte array. This method is always called if {@link #alwaysInMemory()} returns {@code true}.
     * This method is NEVER called if {@link #contentLength()} returns empty.
     * This method MAY be called if {@link #contentLength()} returns a value.
     *
     * @return bytes of the instance
     */
    byte[] instanceBytes();
}
