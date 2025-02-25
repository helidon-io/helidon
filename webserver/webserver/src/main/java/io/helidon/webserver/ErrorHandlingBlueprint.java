/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Configured
@Prototype.Blueprint
interface ErrorHandlingBlueprint {

    /**
     * Whether to include a response entity when mapping a {@link io.helidon.http.RequestException}
     * using a {@link io.helidon.http.DirectHandler}. Response entities may include data that
     * is reflected back from the original request, albeit escaped to prevent potential attacks.
     *
     * @return include entity flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean includeEntity();

    /**
     * Whether to log all messages in a {@link io.helidon.http.RequestException} or not.
     * If set to {@code false}, only those that return {@code true} for
     * {@link io.helidon.http.RequestException#safeMessage()} are logged.
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean logAllMessages();
}
