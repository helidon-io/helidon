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

package io.helidon.http;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Size;

/**
 * Common configuration of HTTP protocol, regardless of its version.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.IncludeDefaultMethods
interface HttpConfigBlueprint {
    /**
     * Configure the maximum size allowed for an entity that can be explicitly
     * buffered by the application by calling {@code io.helidon.http.media.ReadableEntity.buffer()}.
     *
     * @return maximum size for a buffered entity
     */
    @Option.Configured
    @Option.Default("64 KB")
    default Size maxBufferedEntitySize() {
        return Size.create(64, Size.Unit.KB);
    }

    /**
     * Whether to validate request headers.
     * If set to false, any request header value is accepted, otherwise request headers and known headers are
     * validated by format
     * (content length is always validated as it is part of protocol processing (other headers may be validated if
     * features use them)).
     * <p>
     * Defaults to {@code true}.
     *
     * @return whether to validate request headers
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    default boolean validateRequestHeaders() {
        return true;
    }

    /**
     * Whether to validate response headers.
     * If set to false, any response header value is accepted, otherwise response headers and known headers are
     * validated by format.
     * <p>
     * Defaults to {@code true}. Disabling this setting can allow invalid response header values to be written.
     *
     * @return whether to validate response headers
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    default boolean validateResponseHeaders() {
        return true;
    }

    /**
     * HTTP Log configuration.
     *
     * @return log configuration
     */
    @Option.Configured
    @Option.DefaultMethod("create")
    default HttpLogConfig log() {
        return HttpLogConfig.create();
    }
}
