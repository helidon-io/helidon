/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.lang.annotation.Repeatable;

@Repeatable(Features.class)
public @interface Feature {
    /**
     * Feature path.
     *
     * @return path of this feature in the feature tree. First element is the top level node.
     */
    String[] value();

    /**
     * Whether this feature supports ahead of time compilation using GraalVM native-image,
     * defaults to {@code true}.
     *
     * @return {@code true} if this feature supports native image, {@code false} otherwise
     */
    boolean nativeSupport() default true;

    /**
     * Additional description for native image support.
     * If not empty, it is printed as a warning during native image build
     * and runtime.
     *
     * @return description for native image build and runtime
     */
    String nativeDescription() default "";
}
