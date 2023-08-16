/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.lra.coordinator.client.narayana;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.MediaSupport;

import org.eclipse.microprofile.lra.annotation.LRAStatus;

class LraStatusSupport implements MediaSupport {

    private static final GenericType<LRAStatus> LRA_STATUS_TYPE = GenericType.create(LRAStatus.class);
    private final EntityReader reader = new LRAStatusReader();

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers headers) {
        if (type.equals(LRA_STATUS_TYPE)) {
            return new ReaderResponse<>(SupportLevel.SUPPORTED, this::reader);
        }
        return ReaderResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders, Headers responseHeaders) {
        if (type.equals(LRA_STATUS_TYPE)) {
            return new ReaderResponse<>(SupportLevel.SUPPORTED, this::reader);
        }
        return ReaderResponse.unsupported();
    }

    private <T> EntityReader<T> reader(){
        return reader;
    }

    @Override
    public String name() {
        return LRAStatus.class.getName();
    }

    @Override
    public String type() {
        return LRAStatus.class.getName();
    }

    private static class LRAStatusReader implements EntityReader<LRAStatus> {
        @Override
        public LRAStatus read(GenericType<LRAStatus> type, InputStream stream, Headers headers) {
            return read(stream, headers.contentType());
        }

        @Override
        public LRAStatus read(GenericType<LRAStatus> type, InputStream stream, Headers requestHeaders, Headers responseHeaders) {
            return read(stream, responseHeaders.contentType());
        }

        private LRAStatus read(InputStream stream, Optional<HttpMediaType> contentType) {
            Charset charset = contentType
                    .flatMap(HttpMediaType::charset)
                    .map(Charset::forName)
                    .orElse(StandardCharsets.UTF_8);
            try (stream) {
                String stringStatus = new String(stream.readAllBytes(), charset);
                return LRAStatus.valueOf(stringStatus);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
