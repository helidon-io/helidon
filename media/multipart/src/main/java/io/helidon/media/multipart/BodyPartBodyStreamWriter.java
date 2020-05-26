/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.multipart;

import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriterContext;

/**
 * {@link WriteableBodyPart} stream writer.
 */
public final class BodyPartBodyStreamWriter implements MessageBodyStreamWriter<WriteableBodyPart> {

    private final String boundary;

    private BodyPartBodyStreamWriter(String boundary) {
        this.boundary = boundary;
    }

    @Override
    public boolean accept(GenericType<?> type, MessageBodyWriterContext context) {
        return WriteableBodyPart.class.isAssignableFrom(type.rawType());
    }

    @Override
    public Publisher<DataChunk> write(Publisher<? extends WriteableBodyPart> content,
                                      GenericType<? extends WriteableBodyPart> type,
                                      MessageBodyWriterContext context) {

        context.contentType(MediaType.MULTIPART_FORM_DATA);
        MultiPartEncoder encoder = MultiPartEncoder.create(boundary, context);
        content.subscribe(encoder);
        return encoder;
    }

    /**
     * Create a new instance of {@link BodyPartBodyStreamWriter} with the default
     * boundary delimiter.
     * @see MultiPartBodyWriter#DEFAULT_BOUNDARY
     * @return BodyPartStreamWriter
     */
    public static BodyPartBodyStreamWriter create() {
        return new BodyPartBodyStreamWriter(MultiPartBodyWriter.DEFAULT_BOUNDARY);
    }

    /**
     * Create a new instance of {@link BodyPartBodyStreamWriter} with the specified
     * boundary delimiter.
     * @param boundary boundary string
     * @return BodyPartStreamWriter
     */
    public static BodyPartBodyStreamWriter create(String boundary) {
        return new BodyPartBodyStreamWriter(boundary);
    }
}
