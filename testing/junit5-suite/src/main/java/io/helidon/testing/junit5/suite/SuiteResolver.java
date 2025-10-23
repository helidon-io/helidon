/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.testing.junit5.suite;

import java.lang.reflect.Type;

/**
 * {@code SuiteResolver} defines API for suite providers
 * to dynamically resolve arguments for constructors and methods at runtime.
 *
 * @deprecated this is a feature in progress of development, there may be backward incompatible changes done to it, so please
 *         use with care
 */
@Deprecated
public interface SuiteResolver {

    /**
     * Determine if this resolver supports resolving of provided parameter {@link java.lang.reflect.Type}.
     *
     * @param type parameter {@link java.lang.reflect.Type} to check
     * @return value of {@code true} if this resolver supports provided type or {@code false} otherwise
     */
    boolean supportsParameter(Type type);

    /**
     * Resolve parameter of provided parameter {@link java.lang.reflect.Type}.
     * This method is only called if {@link #supportsParameter(java.lang.reflect.Type)} previously returned {@code true}
     * for the same {@link java.lang.reflect.Type}.
     * @param type {@link java.lang.reflect.Type} of the parameter to resolve
     * @return resolved parameter value
     */
    Object resolveParameter(Type type);

}
