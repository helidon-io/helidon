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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.helidon.http.ForbiddenException;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.Method;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Evaluates static-content response metadata and is the sole emitter for selected static content.
 */
final class StaticContentResponse {
    private StaticContentResponse() {
    }

    static boolean send(Method method,
                        ServerRequest request,
                        ServerResponse response,
                        PreparedContent content) throws IOException {
        ResponseRepresentation representation = content.representation();
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        StaticContentMetadata metadata = content.metadata();
        String etag = StaticContentHandler.representationEtag(metadata, representation);
        try {
            StaticContentHandler.processPreconditions(metadata, representation, request.headers(), headers);
        } catch (HttpException e) {
            throw responseException(e, headers, representation, etag);
        }

        boolean sendBody = method == Method.GET;
        if (!sendBody && !representation.runtimeEncoded()) {
            metadata.setContentLength(headers);
        }

        PreparedContent.Body body = null;
        if (sendBody && content.bytes() == null) {
            try {
                if (content.bodySource() == null) {
                    throw new IllegalStateException("Selected static content has no body source");
                }
                body = content.bodySource().get();
                if (body == null) {
                    throw new IllegalStateException("Static content body source returned null");
                }
            } catch (ForbiddenException | IOException e) {
                PreparedContent.BodyOpenFailureFallback fallback = content.bodyOpenFailureFallback();
                if (fallback == null) {
                    throw e;
                }
                Optional<PreparedContent> fallbackContent = fallback.prepare(e);
                return fallbackContent.isPresent() && send(method, request, response, fallbackContent.get());
            }
        }

        try (PreparedContent.Body openBody = body) {
            ByteRangeRequest range = null;
            Status status = null;
            long contentLength = metadata.contentLength();
            boolean rangeSupported = content.bytes() != null || openBody != null && openBody.rangeSupported();
            if (sendBody
                    && !representation.runtimeEncoded()
                    && rangeSupported
                    && contentLength >= 0
                    && request.headers().contains(HeaderNames.RANGE)) {
                List<ByteRangeRequest> ranges;
                try {
                    ranges = ByteRangeRequest.parse(request,
                                                    request.headers().get(HeaderNames.RANGE).values(),
                                                    contentLength,
                                                    etag,
                                                    representation.weakEtag());
                } catch (HttpException e) {
                    throw responseException(e, headers, representation, etag);
                }
                if (ranges.size() == 1) {
                    range = ranges.getFirst();
                    headers.set(range.contentRangeHeader());
                    headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, range.length()));
                    status = Status.PARTIAL_CONTENT_206;
                } else {
                    metadata.setContentLength(headers);
                }
            } else if (sendBody && !representation.runtimeEncoded()) {
                metadata.setContentLength(headers);
            }

            metadata.setContentType(headers);

            configureResponse(response, representation, headers, sendBody);
            if (status != null) {
                response.status(status);
            }

            if (!sendBody) {
                response.send();
                return true;
            }

            if (content.bytes() != null && !representation.runtimeEncoded()) {
                if (range == null) {
                    response.send(content.bytes());
                } else {
                    response.send(Arrays.copyOfRange(content.bytes(),
                                                     (int) range.offset(),
                                                     (int) (range.offset() + range.length())));
                }
                return true;
            }

            long offset = range == null ? 0 : range.offset();
            long length = range == null ? -1 : range.length();
            try (OutputStream output = response.outputStream()) {
                if (content.bytes() != null) {
                    output.write(content.bytes());
                } else {
                    openBody.writeTo(output, offset, length);
                }
            }
            return true;
        }
    }

    private static void configureResponse(ServerResponse response,
                                          ResponseRepresentation representation,
                                          ServerResponseHeaders headers,
                                          boolean sendBody) {
        response.automaticContentEncoding(representation.automaticContentEncoding());
        headers.forEach(response.headers()::set);
        representation.apply(response.headers());
        if (representation.runtimeEncoded()) {
            if (sendBody) {
                response.contentEncoder(representation.runtimeEncoder());
            } else {
                representation.runtimeEncoder().headers(response.headers());
            }
        }
    }

    private static HttpException responseException(HttpException exception,
                                                   ServerResponseHeaders headers,
                                                   ResponseRepresentation representation,
                                                   String etag) {
        headers.forEach(exception::header);
        representation.apply(exception);
        if (etag != null) {
            exception.header(representation.etagHeader(etag));
        }
        return exception;
    }
}
