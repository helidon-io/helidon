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

import java.io.OutputStream;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerResponse;

record ResponseRepresentation(String contentEncoding,
                              boolean varyAcceptEncoding,
                              ContentEncoder runtimeEncoder,
                              boolean automaticContentEncoding) {
    static ResponseRepresentation plain() {
        return new ResponseRepresentation(null, false, null, true);
    }

    static ResponseRepresentation identity(boolean varyAcceptEncoding) {
        return new ResponseRepresentation(null, varyAcceptEncoding, null, false);
    }

    static ResponseRepresentation encoded(String contentEncoding) {
        return new ResponseRepresentation(contentEncoding, true, null, false);
    }

    static ResponseRepresentation runtime(String contentEncoding, ContentEncoder encoder) {
        return new ResponseRepresentation(contentEncoding, true, encoder, false);
    }

    Header etagHeader(String etag) {
        return HeaderValues.create(HeaderNames.ETAG, true, false, (weakEtag() ? "W/" : "") + '"' + etag + '"');
    }

    String etag(String validator, long contentLength) {
        if (contentEncoding == null) {
            return validator;
        }
        if (runtimeEncoded()) {
            return validator + ";encoding=" + contentEncoding;
        }
        return validator + ";encoding=" + contentEncoding + ";length=" + contentLength;
    }

    boolean etagRequiresContentLength() {
        return contentEncoding != null && !runtimeEncoded();
    }

    boolean weakEtag() {
        return runtimeEncoded();
    }

    boolean runtimeEncoded() {
        return runtimeEncoder != null;
    }

    OutputStream outputStream(OutputStream network) {
        return runtimeEncoded() ? runtimeEncoder.apply(network) : network;
    }

    void apply(ServerResponse response) {
        if (response instanceof RoutingResponse routingResponse) {
            routingResponse.automaticContentEncoding(automaticContentEncoding);
        }
        apply(response.headers());
    }

    void apply(ServerResponseHeaders headers) {
        if (varyAcceptEncoding) {
            mergeVary(headers);
        }
        if (runtimeEncoder != null) {
            runtimeEncoder.headers(headers);
            if (!headers.contains(HeaderNames.CONTENT_ENCODING)) {
                headers.set(HeaderValues.create(HeaderNames.CONTENT_ENCODING, true, false, contentEncoding));
            }
            return;
        }
        if (contentEncoding != null) {
            headers.set(HeaderValues.create(HeaderNames.CONTENT_ENCODING, true, false, contentEncoding));
        }
    }

    void apply(HttpException exception) {
        if (varyAcceptEncoding) {
            exception.header(HeaderValues.create(HeaderNames.VARY, true, false, HeaderNames.ACCEPT_ENCODING_NAME));
        }
        if (contentEncoding != null && exception.status() == Status.NOT_MODIFIED_304) {
            exception.header(HeaderValues.create(HeaderNames.CONTENT_ENCODING, true, false, contentEncoding));
        }
    }

    private static void mergeVary(ServerResponseHeaders headers) {
        if (headers.contains(HeaderNames.VARY)) {
            for (String value : headers.get(HeaderNames.VARY).allValues()) {
                String[] values = value.split(",");
                for (String vary : values) {
                    if (HeaderNames.ACCEPT_ENCODING_NAME.equalsIgnoreCase(vary.trim())) {
                        return;
                    }
                }
            }
        }
        headers.add(HeaderValues.create(HeaderNames.VARY, true, false, HeaderNames.ACCEPT_ENCODING_NAME));
    }
}
