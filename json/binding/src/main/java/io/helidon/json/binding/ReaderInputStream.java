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

package io.helidon.json.binding;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Objects;

class ReaderInputStream extends InputStream {

    private final Reader reader;
    private final char[] charArray = new char[512];
    private final byte[] leftovers = new byte[3];
    private final byte[] singleByte = new byte[1];
    private int charPosition = 0;
    private int charLength = 0;
    private int leftoversPosition = 0;
    private int leftoversLength = 0;
    private boolean eof = false;

    ReaderInputStream(Reader reader) {
        this.reader = reader;
    }

    @Override
    public int read() throws IOException {
        int numberOfBytes = fillByteArray(singleByte, 0, 1);
        if (numberOfBytes == 0) {
            return -1;
        }
        return singleByte[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.requireNonNull(b);
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IllegalStateException("Invalid offset/length arguments");
        }
        if (len == 0) {
            return 0;
        }
        if (charPosition >= charLength && eof) {
            return -1;
        }
        return fillByteArray(b, off, len);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private int fillByteArray(byte[] bytes, int off, int len) throws IOException {
        if (charLength == 0) {
            readMoreChars(false);
        }
        int bytesIndex = off;
        int readBytes = 0;
        if (leftoversLength > 0) {
            while (leftoversPosition < leftoversLength && readBytes < len) {
                bytes[bytesIndex++] = leftovers[leftoversPosition++];
            }
            if (leftoversPosition == leftoversLength) {
                leftoversLength = 0;
                leftoversPosition = 0;
            }
            if (readBytes >= len) {
                return readBytes;
            }
        }
        while (true) {
            boolean expectHighSurrogate = false;
            while (charPosition < charLength && readBytes < len) {
                char character = charArray[charPosition++];
                if (character < 0x80) {
                    // 1-byte (ASCII)
                    bytes[bytesIndex++] = (byte) character;
                    readBytes++;
                } else if (character < 0x800) {
                    // 2-byte
                    bytes[bytesIndex++] = (byte) (0xC0 | (character >> 6));
                    readBytes++;
                    if (readBytes < len) {
                        bytes[bytesIndex++] = (byte) (0x80 | (character & 0x3F));
                        readBytes++;
                    } else {
                        leftovers[leftoversLength++] = (byte) (0x80 | (character & 0x3F));
                        return readBytes;
                    }
                } else if (Character.isHighSurrogate(character)) {
                    // 4-byte (surrogate pair), we need to obtain low surrogate.
                    // If not available, buffer more chars, but do not discard the high one.
                    if (charPosition >= charLength) {
                        //keep this high surrogate and buffer more chars
                        expectHighSurrogate = true;
                        break;
                    }
                    int codePoint = Character.toCodePoint(character, charArray[charPosition++]);
                    bytes[bytesIndex++] = (byte) (0xF0 | (codePoint >> 18));
                    readBytes++;
                    byte b = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
                    if (readBytes < len) {
                        bytes[bytesIndex++] = b;
                        readBytes++;
                    } else {
                        leftovers[leftoversLength++] = b;
                    }
                    b = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
                    if (readBytes < len) {
                        bytes[bytesIndex++] = b;
                        readBytes++;
                    } else {
                        leftovers[leftoversLength++] = b;
                    }
                    b = (byte) (0x80 | (codePoint & 0x3F));
                    if (readBytes < len) {
                        bytes[bytesIndex++] = b;
                        readBytes++;
                    } else {
                        leftovers[leftoversLength++] = b;
                        return readBytes;
                    }
                } else {
                    // 3-byte
                    bytes[bytesIndex++] = (byte) (0xE0 | (character >> 12));
                    readBytes++;
                    byte b = (byte) (0x80 | ((character >> 6) & 0x3F));
                    if (readBytes < len) {
                        bytes[bytesIndex++] = b;
                        readBytes++;
                    } else {
                        leftovers[leftoversLength++] = b;
                    }
                    b = (byte) (0x80 | (character & 0x3F));
                    if (readBytes < len) {
                        bytes[bytesIndex++] = b;
                        readBytes++;
                    } else {
                        leftovers[leftoversLength++] = b;
                        return readBytes;
                    }
                }
            }
            if (readBytes < len) {
                if (eof) {
                    return readBytes;
                }
                readMoreChars(expectHighSurrogate);
            } else {
                return readBytes;
            }
        }
    }

    private void readMoreChars(boolean keepTheLastChar) throws IOException {
        int offset = 0;
        if (keepTheLastChar) {
            charArray[0] = charArray[charLength - 1];
            offset = 1;
        }
        charLength = reader.read(charArray, offset, charArray.length - offset);
        if (charLength != charArray.length - offset) {
            eof = true;
        }
        charPosition = 0;
    }
}
