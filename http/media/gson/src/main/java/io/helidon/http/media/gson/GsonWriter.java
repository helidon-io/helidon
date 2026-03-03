/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriterBase;

import com.google.gson.Gson;

class GsonWriter<T> extends EntityWriterBase<T> {
    private final Gson gson;

    GsonWriter(GsonSupportConfig config, Gson gson) {
        super(config);

        this.gson = gson;
    }

    @Override
    public void write(GenericType<T> type, T object,
                      OutputStream outputStream,
                      Headers requestHeaders,
                      WritableHeaders<?> responseHeaders) {

        var charset = serverResponseContentTypeAndCharset(requestHeaders, responseHeaders);

        if (charset.isPresent()) {
            write(type, object, new OutputStreamWriter(outputStream, charset.get()));
        } else {
            write(type, object, outputStream);
        }
    }

    @Override
    public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
        var charset = clientRequestContentTypeAndCharset(headers);

        if (charset.isPresent()) {
            write(type, object, new OutputStreamWriter(outputStream, charset.get()));
        } else {
            write(type, object, outputStream);
        }
    }

    private void write(GenericType<T> type, T object, OutputStreamWriter outputStreamWriter) {
        try (outputStreamWriter) {
            gson.toJson(object, type.type(), outputStreamWriter);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write object to output stream", e);
        }
    }

    private void write(GenericType<T> type, T object, OutputStream outputStream) {
        // using UTF-8 as a default to be consistent with the GsonReader
        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            gson.toJson(object, type.type(), writer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write object to output stream", e);
        }
    }
}
