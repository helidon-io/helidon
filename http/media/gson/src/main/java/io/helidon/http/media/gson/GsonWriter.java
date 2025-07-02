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
package io.helidon.http.media.gson;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;

import com.google.gson.Gson;

class GsonWriter<T> implements EntityWriter<T> {
    private final Gson gson;

    GsonWriter(Gson gson) {
        this.gson = gson;
    }

    @Override
    public void write(GenericType<T> type, T object,
                      OutputStream outputStream,
                      Headers requestHeaders,
                      WritableHeaders<?> responseHeaders) {
        responseHeaders.setIfAbsent(HeaderValues.CONTENT_TYPE_JSON);
        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()){
            if (acceptedType.test(MediaTypes.APPLICATION_JSON)) {
                Optional<String> charset = acceptedType.charset();
                if (charset.isPresent()) {
                    Charset characterSet = Charset.forName(charset.get());
                    write(type, object, new OutputStreamWriter(outputStream, characterSet));
                } else {
                    write(type, object, outputStream);
                }
                return;
            }
        }
    }

    private void write(GenericType<T> type, T object, OutputStreamWriter outputStreamWriter) {
        try {
            gson.toJson(object, type.type(), outputStreamWriter);
            outputStreamWriter.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to write object to output stream", e);
        }
    }

    @Override
    public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
        headers.setIfAbsent(HeaderValues.CONTENT_TYPE_JSON);
        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, Charset.defaultCharset())) {
            gson.toJson(object, type.type(), writer);
            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to write object to output stream", e);
        }
    }

    private void write(GenericType<T> type, T object, OutputStream outputStream) {
        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, Charset.defaultCharset())) {
            gson.toJson(object, type.type(), writer);
            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to write object to output stream", e);
        }
    }
}
