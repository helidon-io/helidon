/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.config.metadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A configured class can load its values from configuration.
 * There are two options for a supported type - either it is an interface/class
 * with a {@code public static X create(Config)}, or a builder class with
 * a method {@code public Builder config(Config)}.
 *
 * This annotation is used to provide IDE autocompletion, and to build documentation metadata
 * using an annotation processor.
 */
@Target(ElementType.TYPE)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface Configured {
    /**
     * Whether this is a root configuration object.
     * If set to {@code false} (default), the object is considered to be nested in another
     * configuration.
     *
     * For example WebServer is a standalone configuration object, as it can be read directly
     * from configuration. ThreadPoolSupplier is not standalone, as it can be used only as part of another
     * object.
     *
     * @return whether this is a standalone configuration object
     */
    boolean root() default false;

    /**
     * Standalone configuration objects use a prefix to mark where Helidon expects them
     * in the configuration tree.
     *
     * @return prefix of all configuration options
     */
    String prefix() default "";

    /**
     * This is a helper to workaround issues where multiple builders build the same type and they do not make sense
     * standalone.
     * This will force the builder to be a separate configuration type and not part of the built type.
     *
     * @return {@code true} if build method should be ignored
     */
    boolean ignoreBuildMethod() default false;

    /**
     * Additional types this type provides.
     * For example security expects a {@code SecurityProvider} - basic authentication provider is such a provider, so it
     * can be marked to provide this type.
     *
     * @return types this configured type provides in addition to its qualified class name
     */
    Class<?>[] provides() default {};
}
