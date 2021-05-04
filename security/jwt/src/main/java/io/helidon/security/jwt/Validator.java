/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.jwt;

import io.helidon.common.Errors;

/**
 * A generic validator, has a method to validate the object and add messages to a {@link Errors.Collector}.
 *
 * Simple example:
 * <pre>
 *     Validator&lt;String&gt; validator = (str, collector) -&gt; {
 *          if (null == str) {
 *              collector.fatal("String must not be null");
 *          }
 *      };
 *      Errors.Collector collector = Errors.collector();
 *      validator.validate("string", collector);
 *      // would throw an exception if invalid
 *      collector.collect().checkValid();
 * </pre>
 *
 * @param <T> type of the object to be validated
 * @see Jwt#validate(java.util.List)
 */
@FunctionalInterface
public interface Validator<T> {
    /**
     * Validate the object against this class's configuration.
     *
     * @param object    object to validate
     * @param collector collector of error messages to add problems to. Use {@link Errors.Collector#fatal(Object, String)}
     *                  to mark the validation as a failure
     */
    void validate(T object, Errors.Collector collector);
}
