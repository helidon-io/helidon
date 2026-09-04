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

package io.helidon.webserver.http3;

import io.helidon.builder.api.Prototype;
import io.helidon.http.http3.Http3Settings;

final class Http3BlueprintSupport {
    static final String CONFIG_NAME = "http_3";
    static final int DEFAULT_RESPONSE_DISPATCH_WINDOW_SIZE = 64 * 1024;

    private Http3BlueprintSupport() {
    }

    static final class Decorator implements Prototype.BuilderDecorator<Http3Config.BuilderBase<?, ?>> {
        @Override
        public void decorate(Http3Config.BuilderBase<?, ?> target) {
            Http3Settings.createConfigured(target.maxFieldSectionSize(),
                                           target.qpackMaxTableCapacity(),
                                           target.qpackBlockedStreams());
            if (target.responseDispatchWindowSize() <= 0) {
                throw new IllegalArgumentException("responseDispatchWindowSize must be greater than 0: "
                                                           + target.responseDispatchWindowSize());
            }
        }
    }
}
