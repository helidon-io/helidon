/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.nima.webserver.http;

import java.util.Arrays;
import java.util.List;

import io.helidon.common.http.Forwarded;
import io.helidon.common.http.HeadersServerRequest;
import io.helidon.common.http.Http;
import io.helidon.nima.webserver.ConnectionContext;

import static io.helidon.common.http.Http.Header.FORWARDED;
import static io.helidon.common.http.Http.Header.X_FORWARDED_FOR;
import static io.helidon.common.http.Http.Header.X_FORWARDED_HOST;
import static io.helidon.common.http.Http.Header.X_FORWARDED_PROTO;

/**
 * Base of server requests with common features.
 */
public abstract class HttpRequestBase implements HttpRequest {
    private final ConnectionContext ctx;

    // cache if requested at least once
    private String authority;
    private String protocol;

    protected HttpRequestBase(ConnectionContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String usedAuthority() {
        if (authority == null) {
            HeadersServerRequest headers = headers();
            for (Http.HeaderName name : ctx.authorityHeaders()) {
                if (Http.Header.HOST.equals(name)) {
                    authority = host();
                    break;
                }
                if (FORWARDED.equals(name)) {
                    List<Forwarded> forwardedList = Forwarded.create(headers);
                    if (!forwardedList.isEmpty()) {
                        authority = forwardedList.get(0)
                                .host()
                                .orElse(null);
                        if (authority != null) {
                            break;
                        }
                    }
                }
                if (X_FORWARDED_HOST.equals(name)) {
                    if (headers.contains(X_FORWARDED_HOST)) {
                        authority = headers.get(X_FORWARDED_HOST).value();
                        break;
                    }
                }
                if (X_FORWARDED_FOR.equals(name)) {
                    if (headers.contains(X_FORWARDED_FOR)) {
                        // may be comma separated with optional space
                        authority = headers.get(X_FORWARDED_FOR).allValues()
                                .stream()
                                .flatMap(it -> Arrays.stream(it.split(",")))
                                .findFirst()
                                .map(String::trim)
                                .orElse(null);
                        if (authority != null) {
                            break;
                        }
                    }
                }
            }
            if (authority == null) {
                authority = host();
            }
        }

        return authority;
    }

    @Override
    public String usedProtocol() {
        if (protocol == null) {
            String foundProtocol = null;

            HeadersServerRequest headers = headers();
            if (headers.contains(FORWARDED)) {
                List<Forwarded> forwardedList = Forwarded.create(headers);
                if (!forwardedList.isEmpty()) {
                    foundProtocol = forwardedList.get(0)
                            .proto()
                            .orElse(null);
                }
            }

            if (foundProtocol == null) {
                if (headers.contains(X_FORWARDED_PROTO)) {
                    foundProtocol = headers.get(X_FORWARDED_PROTO).value();
                }
            }
            if (foundProtocol == null) {
                foundProtocol = ctx.isSecure() ? "https" : "http";
            }

            this.protocol = foundProtocol;
        }

        return protocol;
    }

    /**
     * The content of the Host header or authority pseudo header.
     *
     * @return host
     */
    protected abstract String host();
}
