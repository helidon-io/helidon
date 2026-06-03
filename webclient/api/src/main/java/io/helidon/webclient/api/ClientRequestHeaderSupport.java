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

package io.helidon.webclient.api;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;

final class ClientRequestHeaderSupport {
    private ClientRequestHeaderSupport() {
    }

    static void validate(ClientRequestHeaders headers) {
        if (headers.contains(HeaderNames.HOST)) {
            validateSingleValue(headers.get(HeaderNames.HOST));
        }
    }

    static String hostAuthority(ClientUri uri, ClientRequestHeaders headers) {
        if (!headers.contains(HeaderNames.HOST)) {
            return uri.authority();
        }

        Header host = headers.get(HeaderNames.HOST);
        validateSingleValue(host);
        return host.get();
    }

    private static void validateSingleValue(Header header) {
        int valueCount = header.valueCount();
        if (valueCount != 1) {
            throw new IllegalArgumentException("Request Host header must be single-valued, but found "
                                                       + valueCount
                                                       + " values");
        }
    }
}
