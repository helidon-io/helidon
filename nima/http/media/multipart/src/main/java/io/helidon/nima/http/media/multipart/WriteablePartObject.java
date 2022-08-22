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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.http.Headers;
import io.helidon.common.http.HeadersWritable;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;

class WriteablePartObject extends WriteablePartAbstract {
    private final Supplier<Object> objectContent;

    WriteablePartObject(Builder builder, Supplier<Object> objectContent) {
        super(builder.partName(),
              builder.fileName(),
              builder.contentType(),
              builder.headers());

        this.objectContent = objectContent;
    }

    @Override
    public void writeServerResponse(MediaContext context, OutputStream outputStream, Headers requestHeaders) {
        HeadersWritable<?> headers = HeadersWritable.create(headers());
        contentType(headers);

        // I am making an assumption here, that the object fits into memory, otherwise stream should be used
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Object value = objectContent.get();
        GenericType<Object> genericType = GenericType.create(value);
        EntityWriter<Object> writer = context.writer(genericType,
                                                     requestHeaders,
                                                     headers);
        writer.write(genericType, value, baos, requestHeaders, headers);

        send(outputStream, headers, baos.toByteArray());
    }

    @Override
    public void writeClientRequest(MediaContext context, OutputStream outputStream) {
        HeadersWritable<?> headers = HeadersWritable.create(headers());
        contentType(headers);

        // I am making an assumption here, that the object fits into memory, otherwise stream should be used
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Object value = objectContent.get();
        GenericType<Object> genericType = GenericType.create(value);
        EntityWriter<Object> writer = context.writer(genericType,
                                                     headers);
        writer.write(genericType, value, baos, headers);

        send(outputStream, headers, baos.toByteArray());
    }
}
