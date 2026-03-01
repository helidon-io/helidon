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

package io.helidon.http.media.json;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriterBase;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonValue;

class JsonValueWriter<T extends JsonValue> extends EntityWriterBase<T> {

    JsonValueWriter(JsonSupportConfig config) {
        super(config);
    }

    @Override
    public void write(GenericType<T> type,
                      T object,
                      OutputStream outputStream,
                      Headers requestHeaders,
                      WritableHeaders<?> responseHeaders) {

        var charset = serverResponseContentTypeAndCharset(requestHeaders, responseHeaders);

        if (charset.isPresent()) {
            write(object, new OutputStreamWriter(outputStream, charset.get()));
        } else {
            write(object, outputStream);
        }
    }

    @Override
    public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
        var charset = clientRequestContentTypeAndCharset(headers);

        if (charset.isPresent()) {
            write(object, new OutputStreamWriter(outputStream, charset.get()));
        } else {
            write(object, outputStream);
        }
    }

    private void write(T object, OutputStream out) {
        JsonGenerator.create(out)
                .write(object)
                .close();
        try {
            out.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(T object, Writer out) {
        JsonGenerator.create(out)
                .write(object)
                .close();
        try {
            out.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
