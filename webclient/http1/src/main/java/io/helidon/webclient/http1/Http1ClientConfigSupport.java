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

package io.helidon.webclient.http1;

import io.helidon.builder.api.Prototype;

class Http1ClientConfigSupport {
    private Http1ClientConfigSupport() {
    }

    @SuppressWarnings("deprecation")
    static int maxHeadersSize(Http1ClientProtocolConfig config) {
        int maxHeadersSize = config.maxHeaderSize();
        if (maxHeadersSize <= 0) {
            throw new IllegalArgumentException("Max headers size must be greater than 0");
        }
        return maxHeadersSize;
    }

    static class ProtocolConfigDecorator
            implements Prototype.BuilderDecorator<Http1ClientProtocolConfig.BuilderBase<?, ?>> {
        @Override
        @SuppressWarnings("deprecation")
        public void decorate(Http1ClientProtocolConfig.BuilderBase<?, ?> target) {
            target.maxHeaderSize(target.maxHeadersSize());
        }
    }

    static class MaxHeaderSizeDecorator
            implements Prototype.OptionDecorator<Http1ClientProtocolConfig.BuilderBase<?, ?>, Integer> {
        @Override
        public void decorate(Http1ClientProtocolConfig.BuilderBase<?, ?> builder, Integer optionValue) {
            builder.maxHeadersSize(optionValue);
        }
    }
}
