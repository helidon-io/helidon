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

package io.helidon.nima.webclient.api;

import java.net.CookiePolicy;
import java.net.CookieStore;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

@Prototype.Blueprint
@Configured
interface WebClientCookieManagerConfigBlueprint extends Prototype.Factory<WebClientCookieManager> {
    /**
     * Whether automatic cookie store is enabled or not.
     *
     * @return status of cookie store
     */
    @ConfiguredOption("false")
    boolean automaticStoreEnabled();

    /**
     * Current cookie policy for this client.
     *
     * @return the cookie policy
     */
    @ConfiguredOption("java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER")
    CookiePolicy cookiePolicy();

    /**
     * Map of default cookies to include in all requests if cookies enabled.
     *
     * @return map of default cookies
     */
    @ConfiguredOption
    @Prototype.Singular
    Map<String, String> defaultCookies();

    /**
     * The cookie store where cookies are kept. If not defined, JDK default is used (in memory store).
     *
     * @return cookie store
     */
    Optional<CookieStore> cookieStore();
}
