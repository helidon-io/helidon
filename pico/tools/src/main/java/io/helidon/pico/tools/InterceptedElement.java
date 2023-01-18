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

package io.helidon.pico.tools;

import java.util.Set;

import io.helidon.builder.Builder;

/**
 * Used in the interception model described by {@link InterceptionPlan}. An intercepted
 * element typically refers to a {@link io.helidon.pico.ElementInfo.ElementKind#CONSTRUCTOR} or
 * {@link io.helidon.pico.ElementInfo.ElementKind#METHOD} that qualifies for interception. If, however,
 * the {@link io.helidon.pico.InterceptedTrigger} is applied on the enclosing service type then all public methods.
 * Note that only public methods on pico-activated services can be intercepted.
 */
@Builder
public interface InterceptedElement {

    /**
     * @return The set of {@link io.helidon.pico.InterceptedTrigger} types that apply to this method/element.
     */
    Set<String> interceptedTriggerTypeNames();

    /**
     * @return The method element info for this intercepted method.
     */
    MethodElementInfo elementInfo();

}
