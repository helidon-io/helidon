/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder;

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
 *     <li>{@link Annotated} - in order to add custom annotations on the implementation.</li>
 *     <li>{@link Singular} - when using lists, maps, and sets on getter methods.</li>
 *     <li>io.helidon.config.metadata.ConfiguredOption - for handling default values, policy constraints, etc.</li>
 * </ul>
 *
 * @see Annotated
 * @see Singular
 */
@SuppressWarnings("rawtypes")
@Target(ElementType.TYPE)
// note: class retention needed for cases when derived builders are inherited across modules
@Retention(RetentionPolicy.CLASS)
@BuilderTrigger
public @interface Builder {

    /**
     * The default prefix appended to the generated class name.
     */
    String DEFAULT_IMPL_PREFIX = "Default";

    /**
     * The default prefix appended to the generated abstract class name (the parent for the {@link #DEFAULT_IMPL_PREFIX}).
     */
    String DEFAULT_ABSTRACT_IMPL_PREFIX = "Abstract";

    /**
     * The default suffix appended to the generated class name(s).
     */
    String DEFAULT_SUFFIX = "";

    /**
     * The default value for {@link #allowNulls()}.
     */
    boolean DEFAULT_ALLOW_NULLS = false;

    /**
     * The default value for {@link #allowPublicOptionals()}.
     */
    boolean DEFAULT_ALLOW_PUBLIC_OPTIONALS = false;

    /**
     * The default value for {@link #includeGeneratedAnnotation()}.
     */
    boolean DEFAULT_INCLUDE_GENERATED_ANNOTATION = true;

    /**
     * The default value for {@link #defineDefaultMethods()}.
     */
    boolean DEFAULT_DEFINE_DEFAULT_METHODS = false;

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
     * The package name to use for the generated class. If the package name starts with "." then the package name will be
     * relative to the target type. If left undefined (i.e., an empty string) it will default to the target type's
     * package name.
     *
     * @return the package name to use for the generated class
     */
    String packageName() default "";

    /**
     * The prefix name that will be assigned to the implementation class that is code generated.
     * Default is {@link #DEFAULT_IMPL_PREFIX}.
     * <p>
     * Note also that if your application uses a super builder inheritance scheme (i.e., A extends B extends C) then it is
     * expected that all the Builder annotations for this attribute is set uniformly to the same value.
     *
     * @return the prefix name
     */
    String implPrefix() default DEFAULT_IMPL_PREFIX;

    /**
     * The prefix name that will be assigned to the abstract implementation class that is code generated.
     * Default is {@link #DEFAULT_ABSTRACT_IMPL_PREFIX}.
     * <p>
     * Note also that if your application uses a super builder inheritance scheme (i.e., A extends B extends C) then it is
     * expected that all the Builder annotations for this attribute is set uniformly to the same value.
     *
     * @return the prefix name
     */
    String abstractImplPrefix() default DEFAULT_ABSTRACT_IMPL_PREFIX;

    /**
     * The suffix name that will be assigned to the implementation class that is code generated. Default is {@link #DEFAULT_SUFFIX}.
     * <p>
     * Note also that if your application uses a super builder inheritance scheme (i.e., A extends B extends C) then it is
     * expected that all the Builder annotations for this attribute is set uniformly to the same value.
     *
     * @return the suffix name
     */
    String implSuffix() default DEFAULT_SUFFIX;

    /**
     * Should the code-generated source(s) require a runtime dependency on Helidon libraries? The default is {@code true}.
     * <p>
     * When set to {@code true}, the generated Builder class will rely on common libraries from Helidon as the basis for
     * {@code Builder}, {@code AttributeVisitor}, etc. This would therefore require your consumer application to have a
     * compile/runtime dependency on the {@code builder/runtime-tools} module (having an additional transitive dependency on
     * {@code common/common}.
     * <p>
     * When set to {@code false}, the generated source(s) will self-contain all the supporting types, and thereby avoids any
     * dependencies on any extra Helidon libraries. The cost, however, is code duplication since each builder generated will
     * replicate these types.
     * <p>
     * Note also that if your application uses a super builder inheritance scheme (i.e., A extends B extends C) then it is
     * expected that all the Builder annotations for this attribute is set uniformly to the same value.
     *
     * @return true to extend and use supporting libraries to avoid duplication of generated types
     */
    boolean requireLibraryDependencies() default true;

    /**
     * Should the code-generates source(s) be capable of providing meta-related extensions. This includes, but is not limited to,
     * the following:
     * <ul>
     *     <li>access to attribute names, types, and ConfigurationOption attributes in a map-like structure
     *      - avoiding runtime reflection</li>
     *      <li>providing attribute visitor and visitation capabilities</li>
     *      <li>providing ConfigurationOption#required=true validation (based upon the above visitors)</li>
     * </ul>
     * The default is {@code true}. Note also that in some (future) scenarios, Helidon will mandate this attribute be enabled.
     * <p>
     * Note also that if your application uses a super builder inheritance scheme (i.e., A extends B extends C) then it is
     * expected that all the Builder annotations for this attribute is set uniformly to the same value.
     *
     * @return true to support meta-related attributes and extensions
     */
    boolean includeMetaAttributes() default true;

    /**
     * Should bean style be enforced. Set to {@code true} to force the use of isX() (for booleans) or getY() (for non booleans) on the
     * target type's methods. Default is {@code false}. When enabled then any violation of this will lead to a compile-time error by the
     * Builder's annotation processor. Default is {@code false}.
     *
     * @return true to enforce bean style
     */
    boolean requireBeanStyle() default false;

    /**
     * Should the bean and the builder allow for the possibility of nullable non-{@link java.util.Optional} values to be present.
     * Default is {@code false}.
     *
     * @return true to allow for the possibility of nullable non-Optional values to be present
     */
    boolean allowNulls() default DEFAULT_ALLOW_NULLS;

    /**
     * Should any use of {@link java.util.Optional} builder methods be made public. By default this value is {@code false} resulting
     * in these methods taking {@code Optional} to be made package private. Setting this value to {@code true} will result in these
     * same methods to be made public instead.
     *
     * @return true to make {@code Optional} method public and false to make these methods package private
     */
    boolean allowPublicOptionals() default DEFAULT_ALLOW_PUBLIC_OPTIONALS;

    /**
     * Should the code generated types included the {@code Generated} annotation. Including this annotation will require an
     * additional module dependency on your modules to include {@code jakarta.annotation-api}.
     *
     * @return true to include the Generated annotation
     */
    boolean includeGeneratedAnnotation() default DEFAULT_INCLUDE_GENERATED_ANNOTATION;

    /**
     * Default methods are normally skipped. Setting this to true will allow definition for all {@code default} methods from
     * the target type, but only for getter-type methods taking no arguments.
     *
     * @return true to define default methods
     */
    boolean defineDefaultMethods() default DEFAULT_DEFINE_DEFAULT_METHODS;

    /**
     * The interceptor implementation type. See {@link BuilderInterceptor} for further details. Any interceptor applied will be called
     * prior to validation. The interceptor implementation can be any lambda-like implementation for the {@link BuilderInterceptor}
     * functional interface. This means that the implementation should declare a public method that matches the following:
     * <pre>{@code
     *    Builder intercept(Builder builder);
     * }
     * </pre>
     * Note that the method name must be named <i>intercept</i>.
     *
     * @return the interceptor implementation class
     */
    Class<?> interceptor() default Void.class;

    /**
     * The (static) interceptor method to call on the {@link #interceptor()} implementation type in order to create the interceptor.
     * If left undefined then the {@code new} operator will be called on the type. If provided then the method must be public
     * and take no arguments. Example (see the create() method):
     * <pre>{@code
     *  public class CustomBuilderInterceptor { // implements BuilderInterceptor
     *      public CustomBuilderInterceptor() {
     *      }
     *
     *      public static CustomBuilderInterceptor create() {
     *          ...
     *      }
     *
     *      public Builder intercept(Builder builder) {
     *          ...
     *      }
     *  }
     * }
     * </pre>
     * <p>
     * This attribute is ignored if the {@link #interceptor()} class type is left undefined.
     * Note that the method must return an instance of the Builder, and there must be a public method that matches the following:
     * <pre>{@code
     *    public Builder intercept(Builder builder);
     * }
     * </pre>
     * Note that the method name must be named <i>intercept</i>.
     *
     * @return the interceptor create method
     */
    String interceptorCreateMethod() default "";

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
