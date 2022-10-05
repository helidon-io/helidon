/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.data.annotation;

import java.lang.annotation.*;

/**
 * Defines database specific query string such as SQL that should be executed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
public @interface NativeQuery {

    /**
     * @return The query string configuration key.
     */
    String key() default "";

    /**
     * @return The raw query string.
     */
    String value() default "";

    /**
     * @return JPA ResultSet mapping name.
     */
    String resultSetMapping() default "";

}
