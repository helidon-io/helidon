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

package io.helidon.builder.config.testsubjects.fakes;

import java.util.Map;

import io.helidon.builder.Singular;
import io.helidon.builder.config.ConfigBean;

/**
 * aka ComponentTracing.
 *
 * A component is a single "layer" of the application that can trace.
 * Component examples:
 * <ul>
 *     <li>web-server: webServer adds the root tracing span + two additional spans (content-read and content-write)</li>
 *     <li>security: security adds the overall request security span, a span for authentication ("security:atn"), a span for
 *          authorization "security:atz", and a span for response processing ("security:response")</li>
 *     <li>jax-rs: JAX-RS integration adds spans for overall resource invocation</li>
 * </ul>
 */
@ConfigBean
public interface FakeComponentTracingConfig extends FakeTraceableConfig {

    @Singular("span") // Builder::addSpan(String span, FakeSpanLogTracingConfigBean val), Impl::getSpan(String span), etc.
    Map<String, FakeSpanTracingConfig> spanLogMap();

}
