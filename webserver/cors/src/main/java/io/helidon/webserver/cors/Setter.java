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
 *
 */
package io.helidon.webserver.cors;

import io.helidon.webserver.cors.CORSSupport;

/**
 * Defines common behavior between {@code CrossOriginConfig} and {@link CORSSupport.Builder} for assiging CORS-related
 * attributes.
 *
 * @param <T> the type of the implementing class so the fluid methods can return the correct type
 */
interface Setter<T> {
    /**
     * Sets the allowOrigins.
     *
     * @param origins the origin value(s)
     * @return updated builder
     */
    T allowOrigins(String... origins);

    /**
     * Sets the allow headers.
     *
     * @param allowHeaders the allow headers value(s)
     * @return updated builder
     */
    T allowHeaders(String... allowHeaders);

    /**
     * Sets the expose headers.
     *
     * @param exposeHeaders the expose headers value(s)
     * @return updated builder
     */
    T exposeHeaders(String... exposeHeaders);

    /**
     * Sets the allow methods.
     *
     * @param allowMethods the allow method value(s)
     * @return updated builder
     */
    T allowMethods(String... allowMethods);

    /**
     * Sets the allow credentials flag.
     *
     * @param allowCredentials the allow credentials flag
     * @return updated builder
     */
    T allowCredentials(boolean allowCredentials);

    /**
     * Sets the maximum age.
     *
     * @param maxAge the maximum age
     * @return updated builder
     */
    T maxAge(long maxAge);
}
