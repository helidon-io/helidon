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
package io.helidon.examples.webserver.tutorial;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;

class CommentWriter implements EntityWriter<List<Comment>> {

    @Override
    public void write(GenericType<List<Comment>> type,
                      List<Comment> comments,
                      OutputStream os,
                      Headers requestHeaders,
                      WritableHeaders<?> responseHeaders) {

        write(comments, os, responseHeaders);
    }

    @Override
    public void write(GenericType<List<Comment>> type,
                      List<Comment> comments,
                      OutputStream os,
                      WritableHeaders<?> headers) {

        write(comments, os, headers);
    }

    private void write(List<Comment> comments, OutputStream os, Headers headers) {
        String str = comments.stream()
                             .map(Comment::toString)
                             .collect(Collectors.joining("\n"));

        Charset charset = headers.contentType()
                                 .flatMap(HttpMediaType::charset)
                                 .map(Charset::forName)
                                 .orElse(StandardCharsets.UTF_8);

        try {
            os.write(str.getBytes(charset));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
