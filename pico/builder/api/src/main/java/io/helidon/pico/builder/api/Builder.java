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
 * Adding this annotation on an interface will cause the builder-processor to create a builder for that interface.
 * <p/>
 * The bean implementation that is generated will not require any "special types" requiring extra modules to be included
 * since the code is a straight Java builder implementation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@BuilderTrigger
public @interface Builder {

    /**
     * The default prefeix appended to the generated class name.
     */
    String DEFAULT_PREFIX = "Default";

    /**
     * The default suffix appended to the generated class name.
     */
    String DEFAULT_SUFFIX = "";

    /**
     * The default list type.
     */
    Class<? extends List> DEFAULT_LIST_TYPE = ArrayList.class;

    /**
     * The default map type.
     */
    Class<? extends Map> DEFAULT_MAP_TYPE = LinkedHashMap.class;

    /**
     * The default set type.
     */
    Class<? extends Set> DEFAULT_SET_TYPE = LinkedHashSet.class;

    /**
     * Whether meta information should be revealed as a map.
     */
    boolean DEFAULT_INCLUDE_META_ATTRIBUTES = true;

    /**
     * @return The package name to use. If starts with "." the package name will be relative to the target type. If
     * left undefined will default to the target type. Default is "", defaulting to the same package as the interface.
     */
    String packageName() default "";

    /**
     * @return The prefix name that will be assigned to the implementation class that is code generated. Default is "Default".
     */
    String implPrefix() default DEFAULT_PREFIX;

    /**
     * @return The suffix name that will be assigned to the implementation class that is code generated. Default is "".
     */
    String implSuffix() default DEFAULT_SUFFIX;

    /**
     * @return True to force the use of isX() (for booleans) or getY() (for non booleans). Default is false.
     */
    boolean requireBeanStyle() default false;

    /**
     * @return True to expose the set of attributes that are used, carried in a map form.
     */
    boolean includeMetaAttributes() default DEFAULT_INCLUDE_META_ATTRIBUTES;

//    /**
//     * @return True if beans are validated according to {@link io.helidon.pico.builder.api.ConfiguredOption} when built.
//     */
//    boolean includeValidation() default true;

    /**
     * @return If the builder uses {@link java.util.List} on the interface then this will be the implementation class.
     */
    Class<? extends List> listImplType() default ArrayList.class;

    /**
     * @return If the builder uses {@link java.util.Map} on the interface then this will be the implementation class.
     */
    Class<? extends Map> mapImplType() default LinkedHashMap.class;

    /**
     * @return If the builder uses {@link java.util.Set} on the interface then this will be the implementation class.
     */
    Class<? extends Set> setImplType() default LinkedHashSet.class;

}
