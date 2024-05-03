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
package io.helidon.microprofile.testing.testng;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Add a CDI bean to the container.
 * <p>
 * This annotation can be repeated.
 * <p>
 * If used on a method, the container will be reset regardless of the test lifecycle.
 * <p>
 * The bean scope is defined as follows:
 * <ul>
 *     <li>If a scope is set with {@link #value()}, it overrides any scope defined on the bean</li>
 *     <li>Otherwise, the scope defined on the bean is used</li>
 *     <li>If the bean does not define a scope, {@link jakarta.enterprise.context.ApplicationScoped ApplicationScoped}
 *     is used</li>
 * </ul>
 * @deprecated Use {@link io.helidon.microprofile.testing.AddBean} instead
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(AddBeans.class)
@Deprecated(since = "4.2.0")
public @interface AddBean {
    /**
     * The bean class.
     *
     * @return bean class
     */
    Class<?> value();

    /**
     * Override the bean scope.
     *
     * @return scope class
     */
    Class<? extends Annotation> scope() default Annotation.class;
}
