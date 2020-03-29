/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

/**
 * Interface ExperimentalConfiguration.
 */
public interface ExperimentalConfiguration {
    /**
     * Config property to set HTTP/2 configuration.
     *
     * @return HTTP/2 configuration.
     */
    Http2Configuration http2();

    /**
     * Create a new fluent API builder.
     *
     * @return a new builder instance.
     */
    @SuppressWarnings("deprecation") // will be changed to private constructor and deprecation removed
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ExperimentalConfiguration}.
     */
    final class Builder implements io.helidon.common.Builder<ExperimentalConfiguration> {
        private Http2Configuration http2;

        /**
         * Do not use this constructor.
         * Will be changed to {@code private} in next release.
         *
         * @deprecated use {@link ExperimentalConfiguration#builder()} instead.
         */
        @Deprecated
        public Builder() {
        }

        /**
         * Sets value for HTTP/2 configuration.
         *
         * @param http2 HTTP/2 configuration.
         * @return The builder.
         */
        public Builder http2(Http2Configuration http2) {
            this.http2 = http2;
            return this;
        }

        @Override
        public ExperimentalConfiguration build() {
            return () -> http2;
        }
    }
}
