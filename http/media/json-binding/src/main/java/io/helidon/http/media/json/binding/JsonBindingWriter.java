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

package io.helidon.http.media.json.binding;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriterBase;
import io.helidon.json.binding.JsonBinding;

import static java.util.function.Predicate.not;

class JsonBindingWriter<T> extends EntityWriterBase<T> {

    private final JsonBinding jsonBinding;

    JsonBindingWriter(JsonBindingSupportConfig config, JsonBinding jsonBinding) {
        super(config);
        this.jsonBinding = jsonBinding;
    }

    @Override
    public void write(GenericType<T> type,
                      T object,
                      OutputStream outputStream,
                      Headers requestHeaders,
                      WritableHeaders<?> responseHeaders) {
        Optional<OutputStreamWriter> writer = serverResponseContentTypeAndCharset(requestHeaders, responseHeaders)
                .filter(not(StandardCharsets.UTF_8::equals)) //We don't need writer to be applied for UTF-8
                .map(it -> new OutputStreamWriter(outputStream, it));

        if (writer.isPresent()) {
            write(type, object, writer.get());
        } else {
            write(type, object, outputStream);
        }
    }

    @Override
    public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
        Optional<OutputStreamWriter> writer = clientRequestContentTypeAndCharset(headers)
                .filter(not(StandardCharsets.UTF_8::equals)) //We don't need writer to be applied for UTF-8
                .map(it -> new OutputStreamWriter(outputStream, it));

        if (writer.isPresent()) {
            write(type, object, writer.get());
        } else {
            write(type, object, outputStream);
        }
    }

    private void write(GenericType<T> type, T object, Writer out) {
        try (out) {
            jsonBinding.serialize(out, object, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(GenericType<T> type, T object, OutputStream out) {
        try (out) {
            jsonBinding.serialize(out, object, type);
        }  catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
