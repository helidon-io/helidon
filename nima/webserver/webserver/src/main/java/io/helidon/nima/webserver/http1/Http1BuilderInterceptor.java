/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http1;

import io.helidon.builder.BuilderInterceptor;

class Http1BuilderInterceptor implements BuilderInterceptor<Http1ConfigDefault.Builder> {
    @Override
    public Http1ConfigDefault.Builder intercept(Http1ConfigDefault.Builder target) {
        receiveListeners(target);
        sentListeners(target);

        return target;
    }

    private void sentListeners(Http1ConfigDefault.Builder target) {
        var listeners = target.sendListeners();
        if (listeners.isEmpty() && target.sendLog()) {
            target.addReceiveListener(new Http1LoggingConnectionListener("send"));
        }
        listeners = target.sendListeners();
        target.compositeSendListener(Http1ConnectionListener.create(listeners));
    }

    private void receiveListeners(Http1ConfigDefault.Builder target) {
        var listeners = target.receiveListeners();
        if (listeners.isEmpty() && target.receiveLog()) {
            target.addReceiveListener(new Http1LoggingConnectionListener("recv"));
        }
        listeners = target.receiveListeners();
        target.compositeReceiveListener(Http1ConnectionListener.create(listeners));
    }
}
