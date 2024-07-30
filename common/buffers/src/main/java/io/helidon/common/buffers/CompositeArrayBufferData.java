/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

class CompositeArrayBufferData extends ReadOnlyBufferData implements CompositeBufferData {
    private final BufferData[] data;

    CompositeArrayBufferData(BufferData[] data) {
        this.data = data;
    }

    @Override
    public BufferData rewind() {
        for (BufferData datum : data) {
            datum.rewind();
        }
        return this;
    }

    @Override
    public void writeTo(OutputStream out) {
        if (data.length == 1) {
            BufferData datum = data[0];
            if (!(datum instanceof CompositeBufferData)) {
                datum.writeTo(out);
                return;
            }
        }
        copy().writeTo(out);
    }

    @Override
    public int readFrom(InputStream in) {
        for (BufferData datum : data) {
            if (datum.capacity() > 0) {
                return datum.readFrom(in);
            }
        }
        throw new IllegalStateException("The composite buffer is fully written, cannot write additional bytes");
    }

    @Override
    public int read() {
        for (BufferData datum : data) {
            if (!datum.consumed()) {
                return datum.read();
            }
        }

        throw new ArrayIndexOutOfBoundsException("This buffer has no more bytes");
    }

    @Override
    public int read(byte[] buffer, int position, int length) {
        int myPosition = position;
        int remaining = length;

        for (BufferData datum : data) {
            if (datum.available() > 0) {
                int read = datum.read(buffer, myPosition, remaining);
                myPosition += read;
                remaining -= read;
                if (remaining == 0) {
                    break;
                }
            }
        }

        return myPosition - position;
    }

    @Override
    public String readString(int length, Charset charset) {
        if (length > available()) {
            throw new ArrayIndexOutOfBoundsException("Requested " + length + " bytes, but only " + available()
                                                             + " were available\n" + debugDataHex(false));
        }
        byte[] buffer = new byte[length];

        int read = 0;
        int current;
        while (read != length && available() > 0) {
            current = read(buffer, 0, length);
            read += current;
            if (current == 0) {
                throw new IllegalStateException("Read 0 bytes when available: " + available() + "\n" + debugDataHex(false));
            }
        }
        if (read != length) {
            throw new ArrayIndexOutOfBoundsException("Requested " + length + " bytes, but only " + read + " were available");
        }

        return new String(buffer, charset);
    }

    @Override
    public boolean consumed() {
        boolean consumed = true;

        for (BufferData datum : data) {
            if (!datum.consumed()) {
                consumed = false;
            }
        }

        return consumed;
    }

    @Override
    public int writeTo(ByteBuffer writeBuffer, int limit) {
        int written = 0;

        for (BufferData datum : data) {
            if (datum.consumed()) {
                continue;
            }
            int datumWrote = datum.writeTo(writeBuffer, limit - written);
            if (datumWrote == 0) {
                // not consumed and wrote 0 -> full
                break;
            }
            written += datumWrote;
        }

        return written;
    }

    @Override
    public String debugDataBinary() {
        StringBuilder result = new StringBuilder();

        for (BufferData datum : data) {
            result.append(datum.debugDataBinary());
        }

        return result.toString();
    }

    @Override
    public String debugDataHex(boolean fullBuffer) {
        StringBuilder result = new StringBuilder();

        for (BufferData datum : data) {
            result.append(datum.debugDataHex(fullBuffer));
        }

        return result.toString();
    }

    @Override
    public int available() {
        int available = 0;
        for (BufferData datum : data) {
            available += datum.available();
        }
        return available;
    }

    @Override
    public void skip(int length) {
        int remaining = length;
        for (BufferData datum : data) {
            int toSkip = Math.min(datum.available(), remaining);
            datum.skip(toSkip);
            remaining -= toSkip;
            if (remaining <= 0) {
                return;
            }
        }
    }

    @Override
    public int indexOf(byte aByte) {
        int index;
        int indexPrefix = 0;

        for (BufferData datum : data) {
            index = datum.indexOf(aByte);
            if (index > -1) {
                return indexPrefix + index;
            }
            indexPrefix += datum.available();
        }

        return -1;
    }

    @Override
    public int lastIndexOf(byte aByte, int length) {
        int index;
        int lengthRemaining = length;

        for (int i = data.length - 1; i >= 0; i--) {
            BufferData datum = data[i];
            index = datum.lastIndexOf(aByte, Math.min(lengthRemaining, datum.available()));
            if (index > -1) {
                return index;
            }
            lengthRemaining -= datum.available();
            if (lengthRemaining <= 0) {
                break;
            }
        }
        return -1;
    }

    @Override
    public BufferData trim(int x) {
        if (available() < x) {
            throw new IllegalArgumentException("Trimming more bytes than available");
        }
        int toRemove = x;
        for (int i = data.length - 1; i > -1; i--) {
            if (toRemove == 0) {
                return this;
            }
            BufferData datum = data[i];
            if (datum.available() > 0) {
                int removed = Math.min(datum.available(), toRemove);
                toRemove -= removed;
                datum.trim(removed);
            }
        }
        if (toRemove == 0) {
            return this;
        }

        throw new IllegalStateException("Could not trim buffer by " + x + " bytes");
    }

    @Override
    public int get(int index) {
        int inDataIndex = index;

        for (BufferData datum : data) {
            int available = datum.available();

            if (available <= inDataIndex) {
                inDataIndex -= available;
                continue;
            }
            return datum.get(inDataIndex);
        }
        throw new ArrayIndexOutOfBoundsException("Invalid index to get: " + index);
    }

    @Override
    public String toString() {
        return "comp-array: a=" + available();
    }

    @Override
    public CompositeBufferData add(BufferData bufferData) {
        throw new UnsupportedOperationException("Add not supported for " + getClass().getSimpleName() + " buffer");
    }
}
