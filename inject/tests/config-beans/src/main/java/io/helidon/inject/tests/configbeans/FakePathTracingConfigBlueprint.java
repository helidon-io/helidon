/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.configbeans;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * aka PathTracing.
 *
 * Traced system configuration for web server for a specific path.
 */
@Prototype.Configured
@Prototype.Blueprint
interface FakePathTracingConfigBlueprint {

    /**
     * Path this configuration should configure.
     *
     * @return path on the web server
     */
    String path();

    /**
     * Method(s) this configuration should be valid for. This can be used to restrict the configuration
     * only to specific HTTP methods (such as {@code GET} or {@code POST}).
     *
     * @return list of methods, if empty, this configuration is valid for any method
     */
    @Option.Singular("method")
    // Builder::addMethod(String method);
    List<String> methods();

    @Option.Configured
    @Option.Required
    FakeTracingConfig tracedConfig();

}
