/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.testing;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

/**
 * Copies data sent to an {@link OutputStream} to that stream plus the provided "copy" streams.
 * <p>
 * Intended for unit testing, so performance is not a priority.
 */
public class MultiStream extends PrintStream {

    private final OutputStream[] copies;

    /**
     * Creates a new {@code MultiStream} from an original {@link java.io.OutputStream} and one or more copy streams to receive
     * the same content.
     *
     * @param outputStream original {@code OutputStream}
     * @param copies copy {@code OutputStream}s to receive the same output
     * @return new {@code MultiStream}
     */
    public static MultiStream create(OutputStream outputStream, OutputStream... copies) {
        return new MultiStream(outputStream, copies);
    }

    private MultiStream(OutputStream outputStream, OutputStream... copy) {
        super(outputStream, true, Charset.defaultCharset());
        this.copies = copy;
    }

    @Override
    public void write(int b) {
        super.write(b);
        for (OutputStream copy : copies) {
            try {
                copy.write(b);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void writeBytes(byte[] buf) {
        super.writeBytes(buf);
        for (OutputStream copy : copies) {
            try {
                copy.write(buf);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void flush() {
        super.flush();
        for (OutputStream copy : copies) {
            try {
                copy.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Closes the copy streams without affecting the original stream.
     */
    @Override
    public void close() {
        for (OutputStream copy : copies) {
            try {
                copy.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
