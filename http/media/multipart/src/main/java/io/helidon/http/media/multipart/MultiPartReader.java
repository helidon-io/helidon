/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.io.InputStream;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.MediaContext;

class MultiPartReader implements EntityReader<MultiPart> {
    private final MediaContext context;
    private final String boundary;

    MultiPartReader(MediaContext context, String boundary) {
        this.context = context;
        this.boundary = boundary;
    }

    @Override
    public MultiPart read(GenericType<MultiPart> type, InputStream stream, Headers headers) {
        return new MultiPartImpl(context, boundary, stream);
    }

    @Override
    public MultiPart read(GenericType<MultiPart> type,
                          InputStream stream,
                          Headers requestHeaders,
                          Headers responseHeaders) {
        return new MultiPartImpl(context, boundary, stream);
    }
}
