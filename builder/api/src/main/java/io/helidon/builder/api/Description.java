/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom description. The description is copied from javadoc by default.
 * Use this annotation to customize the description, or when the javadoc is not available within
 * the current module (e.g. when inherited from super type that is not part of the module).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
@Retention(RetentionPolicy.CLASS)
public @interface Description {
    /**
     * Custom description.
     *
     * @return description of the type or property
     */
    String value();
}
