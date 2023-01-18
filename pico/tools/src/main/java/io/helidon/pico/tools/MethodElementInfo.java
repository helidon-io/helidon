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

import io.helidon.builder.Builder;
import io.helidon.pico.ElementInfo;

/**
 * Describes a method element.
 */
@Builder
public interface MethodElementInfo extends ElementInfo {

    /**
     * The list of "throws" that the method throws. Applies only to
     * {@link io.helidon.pico.ElementInfo.ElementKind#METHOD} element types.
     *
     * @return the list of throwable types this method may throw
     */
    List<String> throwableTypeNames();

    /**
     * Provides information for each parameter to the method.
     *
     * @return parameter info
     */
    List<ElementInfo> parameterInfo();

}
