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

package io.helidon.webserver.staticcontent;

import java.time.Instant;

import io.helidon.common.media.type.MediaType;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.ServerResponseHeaders;

record StaticContentMetadata(Header contentTypeHeader,
                             Instant lastModified,
                             Header lastModifiedHeader,
                             long contentLength,
                             Header contentLengthHeader,
                             String etag,
                             Header etagHeader) {

    static StaticContentMetadata create(MediaType mediaType, long contentLength) {
        Header contentTypeHeader = HeaderValues.createCached(HeaderNames.CONTENT_TYPE, mediaType.text());
        Header contentLengthHeader = contentLength < 0
                ? null
                : HeaderValues.createCached(HeaderNames.CONTENT_LENGTH, true, false, String.valueOf(contentLength));
        return new StaticContentMetadata(contentTypeHeader,
                                         null,
                                         null,
                                         contentLength,
                                         contentLengthHeader,
                                         null,
                                         null);
    }

    static StaticContentMetadata create(MediaType mediaType, Instant lastModified, long contentLength) {
        if (lastModified == null) {
            return create(mediaType, contentLength);
        }
        Header contentTypeHeader = HeaderValues.createCached(HeaderNames.CONTENT_TYPE, mediaType.text());
        Header contentLengthHeader = contentLength < 0
                ? null
                : HeaderValues.createCached(HeaderNames.CONTENT_LENGTH, true, false, String.valueOf(contentLength));
        Header lastModifiedHeader = HeaderValues.createCached(HeaderNames.LAST_MODIFIED,
                                                              true,
                                                              false,
                                                              StaticContentHandler.formatLastModified(lastModified));
        String etag = StaticContentHandler.etag(lastModified, contentLength);
        Header etagHeader = HeaderValues.createCached(HeaderNames.ETAG, true, false, '"' + etag + '"');
        return new StaticContentMetadata(contentTypeHeader,
                                         lastModified,
                                         lastModifiedHeader,
                                         contentLength,
                                         contentLengthHeader,
                                         etag,
                                         etagHeader);
    }

    void setContentType(ServerResponseHeaders headers) {
        headers.set(contentTypeHeader);
    }

    void setContentLength(ServerResponseHeaders headers) {
        if (contentLengthHeader != null) {
            headers.set(contentLengthHeader);
        }
    }
}
