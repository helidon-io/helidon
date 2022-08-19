/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webclient;

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutHandler;

class HelidonReadTimeoutHandler extends ReadTimeoutHandler {
    private final long timeoutMillis;
    private boolean closed;

    HelidonReadTimeoutHandler(long timeout, TimeUnit unit) {
        super(timeout, unit);
        this.timeoutMillis = unit.toMillis(timeout);
    }

    protected void readTimedOut(ChannelHandlerContext ctx) throws Exception {
        if (!this.closed) {
            ctx.fireExceptionCaught(new ReadTimeoutException("Read timeout after " + timeoutMillis
                                                                     + " millis on socket " + ctx.channel()
                    .localAddress()));
            ctx.close();
            this.closed = true;
        }
    }

    static class ReadTimeoutException extends RuntimeException {
        ReadTimeoutException(String message) {
            super(message);
        }
    }
}
