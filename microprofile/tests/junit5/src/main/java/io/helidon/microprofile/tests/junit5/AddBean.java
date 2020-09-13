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
package io.helidon.microprofile.tests.junit5;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.context.ApplicationScoped;

/**
 * Add a bean.
 * This is intended for test sources where we do not want to add {@code beans.xml} as this would add
 * all test classes as beans.
 * The bean will be added by default with {@link javax.enterprise.context.ApplicationScoped}.
 * The class will be instantiated using CDI and will be available for injection into test classes and other beans.
 * This annotation can be repeated.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(AddBeans.class)
public @interface AddBean {
    /**
     * Class of the bean.
     * @return the class of a bean
     */
    Class<?> value();

    /**
     * Scope of the bean.
     * Only {@link javax.inject.Singleton}, {@link javax.enterprise.context.ApplicationScoped}
     *   and {@link javax.enterprise.context.RequestScoped} scopes are supported.
     *
     * @return scope of the bean
     */
    Class<? extends Annotation> scope() default ApplicationScoped.class;
}
