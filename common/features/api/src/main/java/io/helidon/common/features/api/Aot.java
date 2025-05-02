/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.features.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declaration of support for ahead of time compilation using native image.
 * @deprecated use {@link io.helidon.common.features.api.Features.Aot} instead.
 */
@Deprecated(forRemoval = true, since = "4.3.0")
@Target(ElementType.MODULE)
@Retention(RetentionPolicy.SOURCE)
public @interface Aot {
    /**
     * Whether AOT is supported by this component or not.
     * @return whether AOT is supported, defaults to {@code true}
     */
    boolean value() default true;

    /**
     * Description of AOT support, such as when AOT is supported, but with limitations.
     * @return description
     */
    String description() default "";
}
