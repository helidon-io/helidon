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

package io.helidon.webserver.http3;

import java.util.Optional;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;

final class Http3Headers {
    private Http3Headers() {
    }

    static ServerRequestHeaders requestHeaders(String authority,
                                               Headers http3Headers,
                                               Optional<String> tlsCommonName) {
        WritableHeaders<?> writable = WritableHeaders.create(http3Headers);
        writable.remove(HeaderNames.X_HELIDON_CN);
        tlsCommonName.ifPresent(cn -> writable.set(HeaderValues.create(HeaderNames.X_HELIDON_CN, cn)));
        if (authority != null && !authority.isEmpty()) {
            writable.set(HeaderValues.create(HeaderNames.HOST, authority));
        }
        return ServerRequestHeaders.create(writable);
    }
}
