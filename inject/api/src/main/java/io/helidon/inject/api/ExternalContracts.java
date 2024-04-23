/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Placed on the implementation of a service as an alternative to using a {@link Contract}.
 * <p>
 * Use this annotation when it is impossible to place an annotation on the interface itself - for instance of the interface comes
 * from a 3rd party library provider.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(java.lang.annotation.ElementType.TYPE)
public @interface ExternalContracts {

    /**
     * The advertised contract type(s) for the service class implementation.
     *
     * @return the external contract(s)
     */
    Class<?>[] value();

    /**
     * The optional set of module names where this contract is expected to reside.
     *
     * @return the optional module names
     */
    String[] moduleNames() default {};

}
