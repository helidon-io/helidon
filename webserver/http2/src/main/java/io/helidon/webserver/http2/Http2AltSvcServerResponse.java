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

package io.helidon.webserver.http2;

import java.util.Objects;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;

final class Http2AltSvcServerResponse extends Http2ServerResponse {
    private final Header altSvcHeader;
    private boolean generatedAltSvcHeader;

    Http2AltSvcServerResponse(Http2ServerStream stream,
                             Http2ServerRequest request,
                             boolean validateResponseHeaders,
                             Header altSvcHeader) {
        super(stream, request, validateResponseHeaders);
        this.altSvcHeader = Objects.requireNonNull(altSvcHeader);
    }

    @Override
    public boolean reset() {
        boolean reset = super.reset();
        if (reset) {
            generatedAltSvcHeader = false;
        }
        return reset;
    }

    @Override
    public boolean resetStream() {
        boolean reset = super.resetStream();
        if (reset) {
            removeGeneratedAltSvcHeader();
        }
        return reset;
    }

    @Override
    protected void beforeSend() {
        super.beforeSend();
        switch (status().family()) {
        case SUCCESSFUL:
        case REDIRECTION:
            if (!ownsCurrentAltSvcHeader()) {
                generatedAltSvcHeader = false;
            }
            if (!headers().contains(HeaderNames.ALT_SVC)) {
                headers().set(altSvcHeader);
                generatedAltSvcHeader = true;
            }
            break;
        default:
            removeGeneratedAltSvcHeader();
            break;
        }
    }

    private void removeGeneratedAltSvcHeader() {
        if (ownsCurrentAltSvcHeader()) {
            headers().remove(HeaderNames.ALT_SVC);
        }
        generatedAltSvcHeader = false;
    }

    private boolean ownsCurrentAltSvcHeader() {
        return generatedAltSvcHeader
                && headers().contains(HeaderNames.ALT_SVC)
                && headers().get(HeaderNames.ALT_SVC) == altSvcHeader;
    }
}
