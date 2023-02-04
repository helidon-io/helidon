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

import java.util.List;
import java.util.Set;

import io.helidon.builder.Builder;
import io.helidon.builder.types.AnnotationAndValue;
import io.helidon.pico.ServiceInfoBasics;

/**
 * Once a service type qualifies for interception, the interception plan will be created describing how the service type
 * should be intercepted.
 */
@Builder
public interface InterceptionPlan {

    /**
     * The intercepted service.
     *
     * @return the intercepted service
     */
    ServiceInfoBasics interceptedService();

    /**
     * Annotations at the service type level.
     *
     * @return annotations at the service type level
     */
    Set<AnnotationAndValue> serviceLevelAnnotations();

    /**
     * All the annotation names that contributed to triggering this interceptor plan.
     *
     * @return all the annotation names that contributed to triggering this interceptor plan
     */
    Set<String> annotationTriggerTypeNames();

    /**
     * The list of elements that should be intercepted.
     *
     * @return the list of elements that should be intercepted
     */
    List<InterceptedElement> interceptedElements();

}
