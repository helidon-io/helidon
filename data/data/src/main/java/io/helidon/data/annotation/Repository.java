/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.annotation;

//import io.micronaut.aop.Introduction;
//import io.micronaut.context.annotation.Prototype;
//import io.micronaut.context.annotation.Type;
//import io.helidon.data.intercept.DataIntroductionAdvice;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates a type of a data repository. If the type is an interface or abstract
 * class this annotation will attempt to automatically provide implementations at compilation time.
 *
 * @author graemerocher
 * @since 1.0
 */
//@Introduction
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD})
@Documented
//@Type(DataIntroductionAdvice.class)
//@Prototype
public @interface Repository {
    /**
     * The name of the underlying datasource connection name. In a multiple data source scenario this will
     * be the name of a configured datasource or connection.
     *
     * @return The connection name
     */
    String value() default "";
}
