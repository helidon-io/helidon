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
package io.helidon.webserver.examples.tutorial;

import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.common.http.Headers;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.media.MediaSupport;

class CommentSupport implements MediaSupport {

    private static final GenericType<List<Comment>> COMMENT_TYPE = new GenericType<>() {};

    @Override
    @SuppressWarnings("unchecked")
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {
        if (!COMMENT_TYPE.rawType().isAssignableFrom(type.rawType())) {
            return WriterResponse.unsupported();
        }
        return (WriterResponse<T>) new WriterResponse<>(SupportLevel.SUPPORTED, CommentWriter::new);
    }

    @Override
    public String name() {
        return "comment";
    }

    @Override
    public String type() {
        return "comment";
    }
}
