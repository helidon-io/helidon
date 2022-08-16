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

package io.helidon.nima.http.media.multipart;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

import io.helidon.common.http.Headers;
import io.helidon.common.http.HeadersWritable;
import io.helidon.nima.http.media.MediaContext;

class WriteablePartStream extends WriteablePartAbstract {
    private final Supplier<InputStream> stream;

    WriteablePartStream(Builder builder, Supplier<InputStream> inputStreamSupplier) {
        super(builder.partName(), builder.fileName(), builder.contentType(), builder.headers());
        this.stream = inputStreamSupplier;
    }

    @Override
    public void writeServerResponse(MediaContext context, OutputStream outputStream, Headers requestHeaders) {
        write(outputStream, HeadersWritable.create(headers()));
    }

    @Override
    public void writeClientRequest(MediaContext context, OutputStream outputStream) {
        write(outputStream, HeadersWritable.create(headers()));
    }

    private void write(OutputStream outputStream, HeadersWritable<?> headers) {
        contentType(headers);

        try (outputStream) {
            sendHeaders(outputStream, headers);
            stream.get().transferTo(outputStream);
            outputStream.write('\r');
            outputStream.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
