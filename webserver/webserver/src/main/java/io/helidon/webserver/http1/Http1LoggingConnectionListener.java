/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Status;
import io.helidon.webserver.ConnectionContext;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Connection listener that logs all exchanged information.
 */
public class Http1LoggingConnectionListener implements Http1ConnectionListener {
    private final String prefix;
    private final System.Logger logger;

    /**
     * Create a new listener with a prefix (such as {@code send} and {@code recv}).
     *
     * @param prefix prefix to use when logging, also used as a suffix of logger name, to enable separate configuration
     */
    public Http1LoggingConnectionListener(String prefix) {
        this.prefix = prefix;
        this.logger = System.getLogger(Http1LoggingConnectionListener.class.getName() + "." + prefix);
    }

    @Override
    public void data(ConnectionContext ctx, BufferData data) {
        if (logger.isLoggable(TRACE)) {
            ctx.log(logger, TRACE, "%s data:%n%s",
                    prefix,
                    data.debugDataHex(true));
        }
    }

    @Override
    public void data(ConnectionContext ctx, byte[] bytes, int offset, int length) {
        if (logger.isLoggable(TRACE)) {
            data(ctx, BufferData.create(bytes, offset, length));
        }
    }

    @Override
    public void prologue(ConnectionContext ctx, HttpPrologue prologue) {
        ctx.log(logger, DEBUG, "%s prologue: %s",
                prefix,
                prologue);
    }

    @Override
    public void headers(ConnectionContext ctx, Headers headers) {
        ctx.log(logger, DEBUG, "%s headers: %n%s",
                prefix,
                headers);
    }

    @Override
    public void status(ConnectionContext ctx, Status status) {
        ctx.log(logger, DEBUG, "%s status: %s",
                prefix,
                status);
    }
}
