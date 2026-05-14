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

package io.helidon.webserver.http1;

import io.helidon.builder.api.Prototype;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.RequestedUriDiscoveryContext;

class Http1BuilderDecorator implements Prototype.BuilderDecorator<Http1Config.BuilderBase<?, ?>> {
    @Override
    public void decorate(Http1Config.BuilderBase<?, ?> target) {
        log(target);
        receiveListeners(target);
        sentListeners(target);

        if (target.name().isEmpty()) {
            target.name("@default");
        }

        if (target.requestedUriDiscovery().isEmpty()) {
            target.requestedUriDiscovery(RequestedUriDiscoveryContext.builder()
                                                 .socketId(target.name().orElse("@default"))
                                                 .build());
        }
    }

    @SuppressWarnings("removal")
    private void log(Http1Config.BuilderBase<?, ?> target) {
        // handle backward compatible logging configuration - if set to false, make sure it is disabled
        if (!target.sendLog()) {
            if (target.log().sendLog()) {
                target.log(HttpLogConfig.builder(target.log())
                                   .sendLog(false)
                                   .build());
            }
        }
        if (!target.receiveLog()) {
            if (target.log().receiveLog()) {
                target.log(HttpLogConfig.builder(target.log())
                                   .receiveLog(false)
                                   .build());
            }
        }
    }

    private void sentListeners(Http1Config.BuilderBase<?, ?> target) {
        var listeners = target.sendListeners();
        if (listeners.isEmpty() && target.log().sendLog()) {
            target.addSendListener(Http1LoggingConnectionListener.create(target.log(),
                                                                         "send"));
        }
        listeners = target.sendListeners();
        target.compositeSendListener(Http1ConnectionListener.create(listeners));
    }

    private void receiveListeners(Http1Config.BuilderBase<?, ?> target) {
        var listeners = target.receiveListeners();
        if (listeners.isEmpty() && target.log().receiveLog()) {
            target.addReceiveListener(Http1LoggingConnectionListener.create(target.log(),
                                                                            "recv"));
        }
        listeners = target.receiveListeners();
        target.compositeReceiveListener(Http1ConnectionListener.create(listeners));
    }
}
