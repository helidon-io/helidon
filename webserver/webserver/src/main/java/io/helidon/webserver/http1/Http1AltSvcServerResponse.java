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
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Status;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.spi.Sink;
import io.helidon.webserver.http.spi.SinkProvider;

final class Http1AltSvcServerResponse extends Http1ServerResponse {
    private final Header altSvcHeader;
    private boolean switchingProtocols;
    private boolean responsePrepared;
    private boolean generatedAltSvcHeader;

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
        SinkProvider<?> provider = findSinkProvider(sinkType);
        try {
            return createSink(provider);
        } catch (RuntimeException | Error e) {
            resetAltSvcPreparation();
            throw e;
        }
    }

    @Override
    public Http1AltSvcServerResponse status(Status status) {
        super.status(status);
        if (responsePrepared) {
            reconcileAltSvcHeader();
        }
        return this;
    }

    @Override
    public boolean reset() {
        boolean reset = super.reset();
        if (reset) {
            resetAltSvcPreparation();
        }
        return reset;
    }

    @Override
    public boolean resetStream() {
        boolean reset = super.resetStream();
        if (reset) {
            resetAltSvcPreparation();
        }
        return reset;
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
        responsePrepared = true;
        reconcileAltSvcHeader();
    }

    private void reconcileAltSvcHeader() {
        if (switchingProtocols) {
            removeGeneratedAltSvcHeader();
            return;
        }

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

    private void resetAltSvcPreparation() {
        removeGeneratedAltSvcHeader();
        responsePrepared = false;
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
