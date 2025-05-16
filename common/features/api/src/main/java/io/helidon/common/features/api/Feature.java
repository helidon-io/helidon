/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 * A Helidon feature annotation to be placed on module in module-info.java.
 * <p>
 * This feature descriptor can be augmented with additioonal annotations:
 * <ul>
 *     <li>{@link Preview}</li>
 *     <li>{@link Incubating}</li>
 *     <li>{@link java.lang.Deprecated}</li>
 * </ul>
 */
@Target(ElementType.MODULE)
@Retention(RetentionPolicy.SOURCE)
public @interface Feature {
    /**
     * Name of this feature.
     *
     * @return name
     */
    String value();

    /**
     * What is the first version that has seen this feature.
     *
     * @return version of Helidon this feature was introduced in
     */
    String since() default "1.0.0";

    /**
     * Path of this feature (a feature path). If this is a top level feature, it can be omitted and
     * the {@link #value()} would be used instead.
     *
     * @return feature path
     */
    String[] path() default {};

    /**
     * Description of this feature, to be displayed when details are printed during startup.
     * Should be reasonably short
     * @return description
     */
    String description();

    /**
     * Which flavors will this feature be printed in.
     *
     * @return flavors to print this feature, leave empty for any flavor.
     */
    HelidonFlavor[] in() default {};

    /**
     * Flavors not supported by this feature - e.g. the set up is invalid, if this feature is
     * added to classpath of this flavor.
     *
     * @return flavors that are not compatible with this feature
     */
    HelidonFlavor[] invalidIn() default {};
}
