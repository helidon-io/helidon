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

package io.helidon.common.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inject query parameter into a method parameter.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface QueryParam {
    /**
     * Configured value meaning the annotation has a default value.
     */
    String NO_DEFAULT_VALUE = "io.helidon.nima.htp.api.QueryParam_NO_DEFAULT_VALUE";

    /**
     * Name of the parameter.
     * @return name of the query parameter
     */
    String value();

    /**
     * Default value to use if the query parameter is not available.
     *
     * @return default value, if none specified, the query parameter is considered required
     */
    String defaultValue() default NO_DEFAULT_VALUE;
}
