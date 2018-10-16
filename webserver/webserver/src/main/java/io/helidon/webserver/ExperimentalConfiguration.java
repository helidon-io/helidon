/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
     * Default value for max content length.
     */
    int DEFAULT_MAX_CONTENT_LENGTH = 64 * 1024;

    /**
     * Config property to enable HTTP/2 support.
     *
     * @return Value of property.
     */
    boolean enableHttp2();

    /**
     * Default HTTP/2 content length. Streaming is currently not supported for HTTP/2,
     * so this is largest payload acceptable.
     *
     * @return Max HTTP/2 buffer size.
     */
    int http2MaxContentLength();

    /**
     * Builder for {@link ExperimentalConfiguration}.
     */
    final class Builder implements io.helidon.common.Builder<ExperimentalConfiguration> {

        private boolean enableHttp2 = false;
        private int http2MaxContentLength = DEFAULT_MAX_CONTENT_LENGTH;

        /**
         * Sets value to enable HTTP/2 support.
         *
         * @param enableHttp2 New value.
         * @return
         */
        public Builder enableHttp2(boolean enableHttp2) {
            this.enableHttp2 = enableHttp2;
            return this;
        }

        /**
         * Sets max content length for HTTP/2.
         *
         * @param http2MaxContentLength New value for max content length.
         * @return
         */
        public Builder http2MaxContentLength(int http2MaxContentLength) {
            this.http2MaxContentLength = http2MaxContentLength;
            return this;
        }

        @Override
        public ExperimentalConfiguration build() {
            return new ServerBasicConfig.ExperimentalConfig(enableHttp2, http2MaxContentLength);
        }
    }
}
