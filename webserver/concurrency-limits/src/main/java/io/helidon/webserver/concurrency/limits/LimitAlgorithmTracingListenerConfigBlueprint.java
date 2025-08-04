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

package io.helidon.webserver.concurrency.limits;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Settings for the tracing {@link io.helidon.common.concurrency.limits.LimitAlgorithmListener}.
 */
@Prototype.Blueprint()
interface LimitAlgorithmTracingListenerConfigBlueprint extends Prototype.Factory<LimitAlgorithmTracingListener> {

    /**
     * Whether the tracing limit algorithm listener is enabled.
     *
     * @return true if the listener is enabled; false otherwise
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enabled();
}
