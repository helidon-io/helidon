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

package io.helidon.http.media;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * {@link InstanceWriter} for bytes already materialized in memory.
 */
public final class ByteArrayInstanceWriter implements InstanceWriter {
    private final byte[] instanceBytes;

    private ByteArrayInstanceWriter(byte[] instanceBytes) {
        this.instanceBytes = Objects.requireNonNull(instanceBytes);
    }

    /**
     * Create a new instance writer for bytes already materialized in memory.
     *
     * @param instanceBytes bytes to write
     * @return byte array instance writer
     */
    public static ByteArrayInstanceWriter create(byte[] instanceBytes) {
        return new ByteArrayInstanceWriter(instanceBytes);
    }

    @Override
    public OptionalLong contentLength() {
        return OptionalLong.of(instanceBytes.length);
    }

    @Override
    public boolean alwaysInMemory() {
        return true;
    }

    @Override
    public void write(OutputStream stream) {
        try (stream) {
            stream.write(instanceBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] instanceBytes() {
        return instanceBytes;
    }
}
