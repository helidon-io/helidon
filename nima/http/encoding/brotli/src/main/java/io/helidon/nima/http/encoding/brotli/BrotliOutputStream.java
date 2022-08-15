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

package io.helidon.nima.http.encoding.brotli;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger.Level;

/**
 * BrotliOutputStream compress input data and write it to the
 * provided OutputStream.
 */
public class BrotliOutputStream extends OutputStream {

    private static final System.Logger LOGGER = System.getLogger(BrotliOutputStream.class.getName());

    private final State state = new State();

    public BrotliOutputStream(OutputStream outputStream) {
        State.initState(state, outputStream);
    }

    public BrotliOutputStream(OutputStream outputStream, BrotliEncoderParams param) {
        this(outputStream);
        BrotliEncoderParams.parseParam(state, param);
    }

    @Override
    public void write(int b) throws IOException {
        if (b > 255) {
            LOGGER.log(Level.WARNING, "Trying to write too big value, will be ignored");
            return;
        }

        if (state.availableIn == Constant.INPUTBUFFER_SIZE - 1) {
            flush();
        }

        Utils.put(state, b);
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        write(buffer, 0, buffer.length);
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        if (offset + length > buffer.length) {
            LOGGER.log(Level.ERROR, "Wrong param value");
            return;
        }

        for (int i = 0; i < length; i++) {
            write(buffer[offset + i]);
        }
    }

    @Override
    public void flush() {
        try {
            Encoder.compress(state, state.eof);
            if (state.availableIn != 0) {
                LOGGER.log(Level.ERROR, "Flushing is not properly done : " + state.availableIn);
            }
        } catch (BrotliException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        if (!state.eof) {
            state.eof = true;
            flush();
            Encoder.close(state);
        }
    }
}
