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

package io.helidon.webserver.http;

import java.util.Optional;

import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.webserver.http.spi.ProtocolUpgradeHandler;

class ProtocolUpgradePolicyRoute extends HttpRouteBase implements HttpRoute, Handler {
    private final HttpRoute delegate;
    private final ProtocolUpgradeHandler handler;

    ProtocolUpgradePolicyRoute(HttpRoute delegate, ProtocolUpgradeHandler handler) {
        this.delegate = delegate;
        this.handler = handler;
    }

    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        return delegate.accepts(prologue);
    }

    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue, ServerRequestHeaders headers) {
        return delegate.accepts(prologue, headers);
    }

    @Override
    public Handler handler() {
        return this;
    }

    @Override
    public Optional<PathMatcher> pathMatcher() {
        return delegate.pathMatcher();
    }

    @Override
    public void handle(ServerRequest req, ServerResponse res) throws Exception {
        handler.handleProtocolUpgrade(req, res);
    }

    @Override
    public String toString() {
        return "protocol upgrade policy for " + delegate;
    }
}
