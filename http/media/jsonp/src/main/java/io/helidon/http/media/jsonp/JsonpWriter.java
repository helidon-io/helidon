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

package io.helidon.http.media.jsonp;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;

import jakarta.json.JsonStructure;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;

class JsonpWriter<T extends JsonStructure> implements EntityWriter<T> {
    private final JsonWriterFactory writerFactory;

    JsonpWriter(JsonWriterFactory writerFactory) {
        this.writerFactory = writerFactory;
    }

    @Override
    public void write(GenericType<T> type,
                      T object,
                      OutputStream outputStream,
                      Headers requestHeaders,
                      WritableHeaders<?> responseHeaders) {

        responseHeaders.setIfAbsent(HeaderValues.CONTENT_TYPE_JSON);

        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON)) {
                Optional<String> charset = acceptedType.charset();
                if (charset.isPresent()) {
                    Charset characterSet = Charset.forName(charset.get());
                    write(object, new OutputStreamWriter(outputStream, characterSet));
                } else {
                    write(object, outputStream);
                }
                return;
            }
        }

        write(object, outputStream);
    }

    @Override
    public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
        headers.setIfAbsent(HeaderValues.CONTENT_TYPE_JSON);
        write(object, outputStream);
    }

    private void write(T object, Writer out) {
        JsonWriter writer = writerFactory.createWriter(out);
        writer.write(object);
    }

    private void write(T object, OutputStream out) {
        try (out) {
            writerFactory.createWriter(out).write(object);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
