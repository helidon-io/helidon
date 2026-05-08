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

package io.helidon.webserver.hsts;

import io.helidon.http.Header;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

class HstsFilter implements Filter {
    private static final String HTTPS = "https";

    private final Header header;

    HstsFilter(Header header) {
        this.header = header;
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        res.beforeSend(() -> maybeAddHeader(req, res));
        chain.proceed();
    }

    private void maybeAddHeader(RoutingRequest req, RoutingResponse res) {
        String scheme = req.requestedUri().scheme();
        if (scheme.equalsIgnoreCase(HTTPS)) {
            res.headers().setIfAbsent(header);
        }
    }
}
