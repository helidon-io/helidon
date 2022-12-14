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
package io.helidon.nima.http2.webserver;

import io.helidon.config.Config;
import io.helidon.nima.http2.Http2Setting;
import io.helidon.nima.http2.Http2Settings;

/**
 * HTTP/2 server configuration.
 */
class Http2Config {

    private static final String CONFIG_MAX_FRAME_SIZE = "max-frame-size";
    private static final String CONFIG_MAX_HEADER_LIST_SIZE = "max-header-size";

    private final long maxFrameSize;
    private final long maxHeaderListSize;

    private Http2Config(long maxFrameSize, long maxHeaderListSize) {
        this.maxFrameSize = maxFrameSize;
        this.maxHeaderListSize = maxHeaderListSize;
    }

    Long maxFrameSize() {
        return maxFrameSize;
    }

    Long maxHeaderListSize() {
        return maxHeaderListSize;
    }

    // Apply configuration values on HTTP settings frame builder
    Http2Settings.Builder apply(Http2Settings.Builder builder) {
        applySetting(builder, maxFrameSize, Http2Setting.MAX_FRAME_SIZE);
        applySetting(builder, maxHeaderListSize, Http2Setting.MAX_HEADER_LIST_SIZE);
        return builder;
    }

    // Add value to the builder only when differs from default
    private void applySetting(Http2Settings.Builder builder, long value, Http2Setting<Long> settings) {
        if (value != settings.defaultValue()) {
            builder.add(settings, value);
        }
    }

    // Return builder with default values
    static Builder builder() {
        return new Builder();
    }

    // Return builder with values initialized from existing configuration
    static Builder builder(Http2Config config) {
        return new Builder(config);
    }

    static class Builder {

        private long maxFrameSize;
        private long maxHeaderListSize;

        private Builder() {
            maxFrameSize = Http2Setting.MAX_FRAME_SIZE.defaultValue();
            maxHeaderListSize = Http2Setting.MAX_HEADER_LIST_SIZE.defaultValue();
        }

        private Builder(Http2Config config) {
            maxFrameSize = config.maxFrameSize;
            maxHeaderListSize = config.maxHeaderListSize;
        }


        // Get values from config.
        Builder config(Config config) {
            config.get(CONFIG_MAX_FRAME_SIZE).asLong()
                    .ifPresent(value -> maxFrameSize = value);
            config.get(CONFIG_MAX_HEADER_LIST_SIZE).asLong()
                    .ifPresent(value -> maxHeaderListSize = value);
            return this;
        }

        Builder maxFrameSize(long maxFrameSize) {
            this.maxFrameSize = maxFrameSize;
            return this;
        }

        Builder maxHeaderListSize(long maxHeaderListSize) {
            this.maxHeaderListSize = maxHeaderListSize;
            return this;
        }

        Http2Config build() {
            return new Http2Config(maxFrameSize, maxHeaderListSize);
        }

    }

}
