/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import java.util.Objects;
import java.util.Optional;

import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.ResolvedClientTarget;

final class Http2ConnectionAttemptResult {
    private final Result result;
    private final Http2ClientStream stream;
    private final HttpClientResponse response;
    private final Http2ClientConnectionHandler handler;
    private final ClientConnectionTarget connectionTarget;
    private final ResolvedClientTarget physicalTarget;
    private final Http2AltSvcCache.Selection selectedAlternative;

    Http2ConnectionAttemptResult(Result result,
                                 Http2ClientStream stream,
                                 HttpClientResponse response,
                                 Http2ClientConnectionHandler handler,
                                 ClientConnectionTarget connectionTarget) {
        this(result, stream, response, handler, connectionTarget, null, null);
    }

    Http2ConnectionAttemptResult(Result result,
                                 Http2ClientStream stream,
                                 HttpClientResponse response,
                                 Http2ClientConnectionHandler handler,
                                 ClientConnectionTarget connectionTarget,
                                 ResolvedClientTarget physicalTarget,
                                 Http2AltSvcCache.Selection selectedAlternative) {
        this.result = Objects.requireNonNull(result, "result");
        this.stream = stream;
        this.response = response;
        this.handler = handler;
        this.connectionTarget = Objects.requireNonNull(connectionTarget, "connectionTarget");
        this.physicalTarget = selectedAlternative == null
                ? physicalTarget
                : Objects.requireNonNull(physicalTarget, "alternative physicalTarget");
        this.selectedAlternative = selectedAlternative;
        if (selectedAlternative != null && !selectedAlternative.originTarget().equals(connectionTarget)) {
            throw new IllegalArgumentException("Alternative selection does not belong to the connection target");
        }
    }

    Result result() {
        return result;
    }

    Http2ClientStream stream() {
        return stream;
    }

    HttpClientResponse response() {
        return response;
    }

    Http2ClientConnectionHandler handler() {
        return handler;
    }

    ClientConnectionTarget connectionTarget() {
        return connectionTarget;
    }

    Optional<ResolvedClientTarget> resolvedTarget() {
        return Optional.ofNullable(physicalTarget);
    }

    Optional<Http2AltSvcCache.Selection> alternative() {
        return Optional.ofNullable(selectedAlternative);
    }

    enum Result {
        HTTP_1,
        HTTP_2,
        UNKNOWN
    }
}
