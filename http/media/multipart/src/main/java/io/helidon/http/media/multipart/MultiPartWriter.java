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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;

class MultiPartWriter implements EntityWriter<WriteableMultiPart> {
    private final MediaContext context;
    private final Header contentType;
    private final byte[] boundaryPrefix;

    MultiPartWriter(MediaContext context, HttpMediaType mediaType, String boundary) {
        this.context = context;
        this.contentType = HeaderValues.create(HeaderNames.CONTENT_TYPE, false, false, mediaType.text());
        this.boundaryPrefix = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void write(GenericType<WriteableMultiPart> type,
                      WriteableMultiPart multiPart,
                      OutputStream os,
                      Headers requestHeaders,
                      WritableHeaders<?> responseHeaders) {

        try (BufferedOutputStream outputStream = new BufferedOutputStream(os, 512)) {
            responseHeaders.set(contentType);

            while (multiPart.hasNext()) {
                WriteablePart next = multiPart.next();

                outputStream.write(boundaryPrefix);
                outputStream.write('\r');
                outputStream.write('\n');

                next.writeServerResponse(context, new NotClosingOutputStream(outputStream), requestHeaders);
            }

            outputStream.write(boundaryPrefix);
            outputStream.write('-');
            outputStream.write('-');
            outputStream.write('\r');
            outputStream.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(GenericType<WriteableMultiPart> type,
                      WriteableMultiPart multiPart,
                      OutputStream outputStream,
                      WritableHeaders<?> headers) {

        try (outputStream) {
            headers.set(contentType);
            while (multiPart.hasNext()) {
                WriteablePart next = multiPart.next();

                outputStream.write(boundaryPrefix);
                outputStream.write('\r');
                outputStream.write('\n');

                next.writeClientRequest(context, new NotClosingOutputStream(outputStream));
            }

            outputStream.write(boundaryPrefix);
            outputStream.write('-');
            outputStream.write('-');
            outputStream.write('\r');
            outputStream.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class NotClosingOutputStream extends OutputStream {
        private final OutputStream delegate;

        private NotClosingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() {
            // do nothing
        }
    }
}
