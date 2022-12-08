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

package io.helidon.microprofile.tyrus;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

class EchoListener implements WebSocket.Listener {
    private static final int WAIT_MILLIS = 5000;
    private static final int INVOCATION_COUNTER = 10;

    private final CompletableFuture<String> echoFuture = new CompletableFuture<>();

    @Override
    public void onOpen(java.net.http.WebSocket webSocket) {
        webSocket.request(INVOCATION_COUNTER);
    }

    @Override
    public CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
        echoFuture.complete(String.valueOf(data));
        return null;
    }

    String awaitEcho() throws Exception {
        return echoFuture.get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
    }
}

