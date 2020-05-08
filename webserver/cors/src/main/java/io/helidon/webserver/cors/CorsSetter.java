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

/**
 * Defines common behavior between {@code CrossOriginConfig} and {@link CorsSupportBase.Builder} for assigning CORS-related
 * attributes.
 *
 * @param <T> the type of the implementing class so the fluid methods can return the correct type
 */
interface CorsSetter<T> {

    /**
     * Sets whether this config should be enabled or not.
     *
     * @param enabled true for this config to have effect; false for it to be ignored
     * @return updated setter
     */
    T enabled(boolean enabled);

    /**
     * Sets the allowOrigins.
     *
     * @param origins the origin value(s)
     * @return updated setter
     */
    T allowOrigins(String... origins);

    /**
     * Sets the allow headers.
     *
     * @param allowHeaders the allow headers value(s)
     * @return updated setter
     */
    T allowHeaders(String... allowHeaders);

    /**
     * Sets the expose headers.
     *
     * @param exposeHeaders the expose headers value(s)
     * @return updated setter
     */
    T exposeHeaders(String... exposeHeaders);

    /**
     * Sets the allow methods.
     *
     * @param allowMethods the allow method value(s)
     * @return updated setter
     */
    T allowMethods(String... allowMethods);

    /**
     * Sets the allow credentials flag.
     *
     * @param allowCredentials the allow credentials flag
     * @return updated setter
     */
    T allowCredentials(boolean allowCredentials);

    /**
     * Sets the maximum age.
     *
     * @param maxAgeSeconds the maximum age
     * @return updated setter
     */
    T maxAgeSeconds(long maxAgeSeconds);
}
