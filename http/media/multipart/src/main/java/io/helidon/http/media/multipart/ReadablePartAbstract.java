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

package io.helidon.http.media.multipart;

import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.http.ContentDisposition;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.HttpMediaTypes;
import io.helidon.http.media.ReadableEntity;

abstract class ReadablePartAbstract implements ReadablePart {
    private final Headers headers;
    private final int index;

    private HttpMediaType mediaType;
    private ContentDisposition contentDisposition;

    protected ReadablePartAbstract(Headers headers, int index) {
        this.headers = headers;
        this.index = index;
    }

    @Override
    public String name() {
        if (contentDisposition == null) {
            contentDisposition();
        }
        return contentDisposition.contentName().orElseGet(() -> "part_" + index);
    }

    @Override
    public Optional<String> fileName() {
        if (contentDisposition == null) {
            contentDisposition();
        }
        return contentDisposition.filename();
    }

    @Override
    public HttpMediaType contentType() {
        if (mediaType == null) {
            mediaType = headers.contentType().orElse(HttpMediaTypes.PLAINTEXT_UTF_8);
        }
        return mediaType;
    }

    @Override
    public Headers partHeaders() {
        return headers;
    }

    @Override
    public ReadableEntity copy(Runnable entityProcessedRunnable) {
        throw new UnsupportedOperationException("Cannot copy a multi-part content.");
    }

    @Override
    public boolean hasEntity() {
        return true;
    }


    @Override
    public <T> Optional<T> asOptional(GenericType<T> type) {
        // there is always an entity (see #hasEntity())
        return Optional.of(as(type));
    }

    protected abstract void finish();

    private void contentDisposition() {
        if (headers.contains(HeaderNames.CONTENT_DISPOSITION)) {
            this.contentDisposition = ContentDisposition.parse(headers.get(HeaderNames.CONTENT_DISPOSITION).get());
        } else {
            this.contentDisposition = ContentDisposition.empty();
        }
    }
}
