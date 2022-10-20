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

package io.helidon.pico.builder.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adding this annotation on an interface will cause the Builder's annotation processor to generate an implementation of the
 * interface that supports the builder pattern.
 *<p>
 * Supplemental annotation types that are supported in conjunction with this builder type include:
 * <ul>
 *     <li>{@link io.helidon.pico.builder.api.Annotated} - in order to add custom annotations on the implementation.</li>
 *     <li>{@link io.helidon.pico.builder.api.Singular} - when using lists, maps, and sets on getter methods.</li>
 *     <li>io.helidon.config.metadata.ConfiguredOption - for handling default values, policy constraints, etc.</li>
 * </ul>
 *
 * @see io.helidon.pico.builder.api.Annotated
 * @see io.helidon.pico.builder.api.Singular
 */
@SuppressWarnings("rawtypes")
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@BuilderTrigger
public @interface Builder {

    /**
     * The default prefix appended to the generated class name.
     */
    String DEFAULT_PREFIX = "Default";

    /**
     * The default suffix appended to the generated class name.
     */
    String DEFAULT_SUFFIX = "";

    /**
     * The default list type used for the generated class implementation for any references to {@link java.util.List} is found
     * on the methods of the {@link Builder}-annotation interface.
     */
    Class<? extends List> DEFAULT_LIST_TYPE = ArrayList.class;

    /**
     * The default map type used for the generated class implementation for any references to {@link java.util.Map} is found
     * on the methods of the {@link Builder}-annotation interface.
     */
    Class<? extends Map> DEFAULT_MAP_TYPE = LinkedHashMap.class;

    /**
     * The default set type used for the generated class implementation for any references to {@link java.util.Set} is found
     * on the methods of the {@link Builder}-annotation interface.
     */
    Class<? extends Set> DEFAULT_SET_TYPE = LinkedHashSet.class;

    /**
     * Flag indicating whether meta information should be revealed as a map (defaulting to {@code true}).
     */
    boolean DEFAULT_INCLUDE_META_ATTRIBUTES = true;

    /**
     * The package name to use for the generated class. If the package name starts with "." then the package name will be
     * relative to the target type. If left undefined (i.e., an empty string) it will default to the target type's
     * package name.
     *
     * @return the package name to use for the generated class
     */
    String packageName() default "";

    /**
     * The prefix name that will be assigned to the implementation class that is code generated. Default is {@link #DEFAULT_PREFIX}.
     *
     * @return the prefix name
     */
    String implPrefix() default DEFAULT_PREFIX;

    /**
     * The suffix name that will be assigned to the implementation class that is code generated. Default is {@link #DEFAULT_SUFFIX}.
     *
     * @return the suffix name
     */
    String implSuffix() default DEFAULT_SUFFIX;

    /**
     * Should bean style be enforced. Set to {@code true} to force the use of isX() (for booleans) or getY() (for non booleans) on the
     * target type's methods. Default is {@code false}. When enabled then any violation of this will lead to a compile-time error by the
     * Builder's annotation processor.
     *
     * @return true to enforce bean style
     */
    boolean requireBeanStyle() default false;

    /**
     * The list implementation type to apply, defaulting to {@link #DEFAULT_LIST_TYPE}.
     *
     * @return the list type to apply
     */
    Class<? extends List> listImplType() default ArrayList.class;

    /**
     * The map implementation type to apply, defaulting to {@link #DEFAULT_MAP_TYPE}.
     *
     * @return the map type to apply
     */
    Class<? extends Map> mapImplType() default LinkedHashMap.class;

    /**
     * The set implementation type to apply, defaulting to {@link #DEFAULT_SET_TYPE}.
     *
     * @return the set type to apply
     */
    Class<? extends Set> setImplType() default LinkedHashSet.class;

}
