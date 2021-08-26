/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.util.List;

import io.helidon.common.mapper.ValueProvider;

/**
 * Common attributes of an HTTP Request, that are used both in server requests and in client requests.
 */
public interface HttpRequest {
    /**
     * Returns an HTTP request method. See also {@link Http.Method HTTP standard methods} utility class.
     *
     * @return an HTTP method
     * @see Http.Method
     */
    Http.RequestMethod method();

    /**
     * Returns an HTTP version from the request line.
     * <p>
     * See {@link Http.Version HTTP Version} enumeration for supported versions.
     * <p>
     * If communication starts as a {@code HTTP/1.1} with {@code h2c} upgrade, then it will be automatically
     * upgraded and this method returns {@code HTTP/2.0}.
     *
     * @return an HTTP version
     */
    Http.Version version();

    /**
     * Returns a Request-URI (or alternatively path) as defined in request line.
     *
     * @return a request URI
     */
    URI uri();

    /**
     * Returns an encoded query string without leading '?' character.
     *
     * @return an encoded query string
     */
    String query();

    /**
     * Returns query parameters.
     *
     * @return an parameters representing query parameters
     */
    Parameters queryParams();

    /**
     * Returns a path which was accepted by matcher in actual routing. It is path without a context root
     * of the routing.
     * <p>
     * Use {@link Path#absolute()} method to obtain absolute request URI path representation.
     * <p>
     * Returned {@link Path} also provide access to path template parameters. An absolute path then provides access to
     * all (including) context parameters if any. In case of conflict between parameter names, most recent value is returned.
     *
     * @return a path
     */
    Path path();

    /**
     * Returns a decoded request URI fragment without leading hash '#' character.
     *
     * @return a decoded URI fragment
     */
    String fragment();

    /**
     * Represents requested normalised URI path.
     */
    interface Path {

        /**
         * Returns value of single parameter resolved from path pattern.
         *
         * @param name a parameter name
         * @return a parameter value or {@code null} if not exist
         */
        String param(String name);

        /**
         * Returns value of single parameter resolved from path pattern.
         *
         * @param name a parameter name
         * @return a parameter value provider
         */
        default ValueProvider parameter(String name) {
            String value = param(name);

            String valueName = "PathParam(" + name + ")";

            if (value == null) {
                return ValueProvider.empty(valueName);
            } else {
                return ValueProvider.create(valueName, value);
            }
        }

        /**
         * Returns path as a list of its segments.
         *
         * @return a list of path segments
         */
        List<String> segments();

        /**
         * Returns a path string representation with leading slash.
         *
         * @return a path
         */
        String toString();

        /**
         * Returns a path string representation with leading slash without
         * any character decoding.
         *
         * @return an undecoded path
         */
        String toRawString();

        /**
         * If the instance represents a path relative to some context root then returns absolute requested path otherwise
         * returns this instance.
         * <p>
         * The absolute path also contains access to path parameters defined in context matchers. If there is
         * name conflict then value represents latest matcher result.
         *
         * @return an absolute requested URI path
         */
        Path absolute();
    }
}
