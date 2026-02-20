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

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonValue;

class JsonValueWriter<T extends JsonValue> implements EntityWriter<T> {

    private final JsonSupportConfig config;
    private final Header contentTypeHeader;

    JsonValueWriter(JsonSupportConfig config) {
        this.config = config;
        this.contentTypeHeader = HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                     config.contentType().text());
    }

    @Override
    public void write(GenericType<T> type,
                      T object,
                      OutputStream outputStream,
                      Headers requestHeaders,
                      WritableHeaders<?> responseHeaders) {
        responseHeaders.setIfAbsent(contentTypeHeader);

        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            for (MediaType acceptedMediaType : config.acceptedMediaTypes()) {
                if (acceptedType.test(acceptedMediaType)) {
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
        }

        write(object, outputStream);
    }

    @Override
    public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
        headers.setIfAbsent(contentTypeHeader);
        write(object, outputStream);
    }

    private void write(T object, OutputStream out) {
        JsonGenerator.create(out)
                .write(object)
                .close();
    }

    private void write(T object, Writer out) {
        JsonGenerator.create(out)
                .write(object)
                .close();
    }
}
