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

package io.helidon.inject.tests.configbeans;

import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * aka TracingConfig.
 * <p>
 * Tracing configuration that contains traced components (such as WebServer, Security) and their traced spans and span logs.
 * Spans can be renamed through configuration, components, spans and span logs may be disabled through this configuration.
 */
@Prototype.Configured("tracing")
@Prototype.Blueprint
interface FakeTracingConfigBlueprint extends FakeTraceableConfigBlueprint {

    // Builder::addComponent(String component); Impl::getComponent(String component);
    @Option.Singular
    Map<String, FakeComponentTracingConfig> components();

}
