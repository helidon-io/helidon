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

package io.helidon.webserver.http1;

import java.util.Objects;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.DataWriter;
import io.helidon.http.Header;
import io.helidon.http.Headers;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.spi.Sink;

final class Http1AltSvcServerResponse extends Http1ServerResponse {
    private final Header altSvcHeader;
    private boolean switchingProtocols;

    Http1AltSvcServerResponse(ConnectionContext ctx,
                             Http1ConnectionListener sendListener,
                             DataWriter dataWriter,
                             Http1ServerRequest request,
                             boolean keepAlive,
                             boolean validateHeaders,
                             Header altSvcHeader) {
        super(ctx, sendListener, dataWriter, request, keepAlive, validateHeaders);
        this.altSvcHeader = Objects.requireNonNull(altSvcHeader);
    }

    @Override
    public <X extends Sink<?>> X sink(GenericType<X> sinkType) {
        beforeSend();
        return super.sink(sinkType);
    }

    @Override
    public void sendSwitchingProtocols(Headers requiredHeaders) {
        switchingProtocols = true;
        try {
            super.sendSwitchingProtocols(requiredHeaders);
        } finally {
            switchingProtocols = false;
        }
    }

    @Override
    protected void beforeSend() {
        super.beforeSend();
        if (!switchingProtocols) {
            switch (status().family()) {
            case SUCCESSFUL:
            case REDIRECTION:
                headers().setIfAbsent(altSvcHeader);
                break;
            default:
                break;
            }
        }
    }
}
