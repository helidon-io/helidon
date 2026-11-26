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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.http.media.EntityReaderBase;
import io.helidon.json.JsonDecodingException;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileConfig;
import io.helidon.json.smile.SmileParser;

class SmileReader<T> extends EntityReaderBase<T> {

    private final JsonBinding jsonBinding;
    private final SmileConfig smileConfig;

    SmileReader(JsonBinding jsonBinding, SmileConfig smileConfig) {
        this.jsonBinding = jsonBinding;
        if (smileConfig.maxReplaySize().isPresent()) {
            this.smileConfig = smileConfig;
        } else {
            this.smileConfig = jsonBinding.prototype().maxReplaySize()
                    .map(size -> SmileConfig.builder(smileConfig).maxReplaySize(size).build())
                    .orElse(smileConfig);
        }
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers headers) {
        try {
            return read(type, stream);
        } catch (JsonDecodingException e) {
            throw new HttpException("Failed to deserialize Smile request entity", Status.BAD_REQUEST_400, e);
        }
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers requestHeaders, Headers responseHeaders) {
        return read(type, stream);
    }

    private T read(GenericType<T> type, InputStream in) {
        try (in) {
            return jsonBinding.deserialize(SmileParser.create(in, smileConfig), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
