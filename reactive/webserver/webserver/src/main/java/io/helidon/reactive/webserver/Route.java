/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.helidon.common.http.Http;

/**
 * A unit of the {@link Routing}.
 */
interface Route extends ServerLifecycle {

    /**
     * Path matcher for routing which doesn't specify any other path matcher.
     * Matcher accepts ANY path and a {@code remaining} for {@code prefixMatch} is whole tested path.
     */
    PathMatcher EMPTY_PATH_MATCHER = new PathMatcher() {
        @Override
        public Result match(CharSequence path) {
            return prefixMatch(path);
        }

        @Override
        public PrefixResult prefixMatch(CharSequence path) {
            return new PrefixResult() {
                @Override
                public String remainingPart() {
                    if (path == null) {
                        return null;
                    } else {
                        return path.toString();
                    }
                }

                @Override
                public boolean matches() {
                    return true;
                }

                @Override
                public Map<String, String> params() {
                    return Collections.emptyMap();
                }

                @Override
                public String param(String name) {
                    return null;
                }
            };
        }
    };

    /**
     * Gets all accepted {@link Http.Method HTTP methods} <b>or</b> empty set if accepts ANY method <b>or</b> {@code null}
     * if no method (not a method based route).
     *
     * @return accepted methods.
     */
    default Set<Http.Method> acceptedMethods() {
        return null;
    }

    /**
     * Returns {@code true} if this record accepts provided method.
     *
     * @param method An HTTP method.
     * @return {@code true} if this record accepts provided method.
     */
    default boolean accepts(Http.Method method) {
        return false;
    }
}
