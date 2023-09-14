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

package io.helidon.http.media.multipart;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.NoSuchElementException;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.HeaderNames;
import io.helidon.http.Http1HeadersParser;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;

class MultiPartImpl extends MultiPart {
    private final String boundary;
    private final String endBoundary;
    private final int maxNewLine;
    private final DataReader dataReader;
    private MediaContext context;
    private ReadablePartAbstract next;
    private ReadablePartAbstract inProgress;
    private boolean finished;
    private int index;

    MultiPartImpl(MediaContext context, String boundary, InputStream stream) {
        this.context = context;
        this.boundary = "--" + boundary;
        this.endBoundary = "--" + boundary + "--";
        this.maxNewLine = this.boundary.length() + 6;
        byte[] readBuffer = new byte[1024];
        this.dataReader = new DataReader(() -> {
            try {
                int r = stream.read(readBuffer);
                if (r == -1) {
                    return null;
                }
                return Arrays.copyOf(readBuffer, r);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, true);
    }

    @Override
    public boolean hasNext() {
        if (finished) {
            return false;
        }
        if (next != null) {
            return true;
        }
        if (inProgress != null) {
            inProgress.finish();
            inProgress = null;
        }
        // we need to find the next boundary\r\n for first, \r\nboundary\r\n for others
        if (dataReader.startsWithNewLine()) {
            dataReader.skip(2);
        }
        int newLine = dataReader.findNewLine(maxNewLine);
        if (newLine == maxNewLine) {
            return false;
        }

        String probablyBoundary = dataReader.readAsciiString(newLine);
        if (probablyBoundary.equals(boundary)) {
            dataReader.skip(2); // skip the new line after boundary
            WritableHeaders<?> headers = Http1HeadersParser.readHeaders(dataReader, 1024, true);
            if (headers.contains(HeaderNames.CONTENT_LENGTH)) {
                next = new ReadablePartLength(context,
                                              headers,
                                              dataReader,
                                              index++,
                                              headers.get(HeaderNames.CONTENT_LENGTH).get(long.class));
                return true;
            } else {
                next = new ReadablePartNoLength(context, headers, dataReader, index++, boundary, endBoundary);
                return true;
            }
        } else if (probablyBoundary.equals(endBoundary)) {
            finished = true;
        }
        return false;
    }

    @Override
    public ReadablePart next() {
        if (hasNext()) {
            inProgress = next;
            next = null;
            return inProgress;
        } else {
            throw new NoSuchElementException("No more parts");
        }
    }
}
