/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.sse.webclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.sse.common.SseEvent;
import io.helidon.nima.webclient.ClientResponse;
import io.helidon.nima.webclient.http.spi.SourceHandler;

/**
 * A handler for SSE sources.
 */
public class SseSourceHandler implements SourceHandler<SseEvent, SseSource> {

    @Override
    public boolean supports(GenericType<SseSource> type, ClientResponse response) {
        return type == SseSource.TYPE && response.headers().contentType()
                .map(ct -> ct.mediaType().equals(MediaTypes.TEXT_EVENT_STREAM)).orElse(false);
    }

    @Override
    public void handle(SseSource source, ClientResponse response) {
        try {
            InputStream is = response.inputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

            String line;
            StringBuilder data = new StringBuilder();
            boolean emit = false;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (emit) {
                        source.onEvent(SseEvent.create(data.toString()));
                        data.setLength(0);
                        emit = false;
                    }
                    continue;
                }
                if (line.startsWith("data:")) {
                    data.append(line.length() > 5 ? line.substring(5) : "");
                    emit = true;
                }
                // todo other type of lines
            }
            source.onClose();
        } catch (IOException e) {
            source.onError(e);
            throw new UncheckedIOException(e);
        }
    }
}
