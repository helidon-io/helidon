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

package io.helidon.http.media.json.smile;

import java.io.OutputStream;

import io.helidon.common.GenericType;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriterBase;
import io.helidon.json.JsonGenerator;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileConfig;
import io.helidon.json.smile.SmileGenerator;

class SmileWriter<T> extends EntityWriterBase<T> {

    private final JsonBinding jsonBinding;
    private final SmileConfig smileConfig;
    private final Header contentType;

    SmileWriter(SmileSupportConfig config, JsonBinding jsonBinding) {
        super(config);
        this.jsonBinding = jsonBinding;
        this.smileConfig = config.smileConfig();
        this.contentType = HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                               config.contentType().text());
    }

    @Override
    public void write(GenericType<T> type,
                      T object,
                      OutputStream outputStream,
                      Headers requestHeaders,
                      WritableHeaders<?> responseHeaders) {
        responseHeaders.setIfAbsent(contentType);
        write(object, outputStream, type);
    }

    @Override
    public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
        headers.setIfAbsent(contentType);
        write(object, outputStream, type);
    }

    private void write(T object, OutputStream out, GenericType<T> type) {
        try (JsonGenerator generator = SmileGenerator.create(out, smileConfig)) {
            jsonBinding.serialize(generator, object, type);
        }
    }
}
