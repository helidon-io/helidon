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

package io.helidon.common.features.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A container class for annotations and types related to declaration of Helidon features.
 * A feature must at least have {@link io.helidon.common.features.api.Features.Name} annotation.
 */
public final class Features {
    private Features() {
    }

    /**
     * Feature name.
     */
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Name {
        /**
         * Name of this feature.
         *
         * @return name
         */
        String value();
    }

    /**
     * Feature description.
     */
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Description {
        /**
         * Description of this feature.
         *
         * @return description
         */
        String value();
    }

    /**
     * Since which Helidon version is this feature available.
     */
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Since {
        /**
         * Version of Helidon this feature is first available.
         *
         * @return since
         */
        String value();
    }

    /**
     * Feature path. For top level features, this annotation can be omitted,
     * and {@link io.helidon.common.features.api.Features.Name#value()} will be used instead.
     */
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Path {
        /**
         * Path of this feature.
         *
         * @return path elements
         */
        String[] value();
    }

    /**
     * Flavor this feature is designed for, and for which it will be printed during startup.
     */
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flavor {
        /**
         * Flavor(s) of this feature.
         *
         * @return flavors to print this feature in
         */
        HelidonFlavor[] value();
    }

    /**
     * Flavor this feature must not be present in.
     * <p>
     * For example most of Helidon MP features are not valid in Helidon SE.
     */
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface InvalidFlavor {
        /**
         * Invalid flavor(s) of this feature.
         *
         * @return flavors to print a warning
         */
        HelidonFlavor[] value();
    }

    /**
     * Declaration of support for ahead of time compilation using native image.
     * AOT is considered supported by default (i.e. if this annotation is not present).
     */
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Aot {
        /**
         * Whether AOT is supported by this component or not.
         *
         * @return whether AOT is supported, defaults to {@code true}
         */
        boolean value() default true;

        /**
         * Description of AOT support, such as when AOT is supported, but with limitations.
         *
         * @return description
         */
        String description() default "";
    }

    /**
     * Annotation for incubating feature modules.
     * Incubating features may be changed including backward incompatible changes in between any version of Helidon.
     * Incubating features are NOT production ready features, and may be removed at discretion of Helidon team.
     * Mutually exclusive with {@link io.helidon.common.features.api.Features.Preview}.
     *
     * @see io.helidon.common.features.api.Features.Preview
     */
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Incubating {
    }

    /**
     * Annotation for preview feature modules.
     * Preview feature may be changed including backward incompatible changes in between minor versions of Helidon.
     * Preview features are considered production ready features.
     * Mutually exclusive with {@link io.helidon.common.features.api.Features.Incubating}.
     *
     * @see io.helidon.common.features.api.Features.Incubating
     */
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Preview {
    }
}
