/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriPath;

/**
 * Abstraction of HTTP path supporting routing parameters.
 */
public interface RoutedPath extends UriPath {
    /**
     * Resolved parameters from path template.
     * Path templates do not support multi-valued parameters.
     * <p>
     * Example of path with path parameter: {@code /users/{user}}.
     * {@code user} is the name of the parameter.
     *
     * @return resolved parameters
     */
    Parameters pathParameters();

    /**
     * If this instance represents a path relative to some context root then returns absolute requested path otherwise
     * returns this instance.
     * <p>
     * The absolute path also contains access to path parameters defined in context matchers. If there is
     * name conflict then value represents latest matcher result.
     *
     * @return an absolute requested URI path
     */
    RoutedPath absolute();
}
