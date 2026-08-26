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

package io.helidon.webserver.staticcontent;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of one pre-compressed static-content representation.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.CustomMethods(StaticContentConfigSupport.PreCompressedEncodingMethods.class)
interface PreCompressedEncodingConfigBlueprint {
    /**
     * Configured HTTP content coding for this representation; when a request explicitly accepts a recognized alias,
     * the response uses the accepted alias in the {@code Content-Encoding} header.
     *
     * @return configured content coding
     */
    @Option.Configured
    String coding();

    /**
     * File suffix used to locate this representation.
     *
     * @return file suffix
     */
    @Option.Configured
    String suffix();
}
