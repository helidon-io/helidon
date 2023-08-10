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

package io.helidon.webclient.http2;

import io.helidon.builder.api.Prototype;
import io.helidon.http.http2.WindowSize;

class Http2ClientConfigSupport {
    static class ProtocolConfigDecorator implements Prototype.BuilderDecorator<Http2ClientProtocolConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(Http2ClientProtocolConfig.BuilderBase<?, ?> target) {
            int maxFrameSize = target.maxFrameSize();
            if (maxFrameSize < WindowSize.DEFAULT_MAX_FRAME_SIZE || maxFrameSize > WindowSize.MAX_MAX_FRAME_SIZE) {
                throw new IllegalArgumentException(
                        "Max frame size needs to be a number between 2^14(16_384) and 2^24-1(16_777_215)"
                );
            }
        }
    }
}
