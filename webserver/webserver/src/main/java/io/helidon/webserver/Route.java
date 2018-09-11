/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.common.http.Http;

/**
 * A unit of the {@link Routing}.
 */
interface Route {

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
     * Gets all accepted {@link Http.RequestMethod HTTP methods} <b>or</b> empty set if accepts ANY method <b>or</b> {@code null}
     * if no method (not a method based route).
     *
     * @return accepted methods.
     */
    default Set<Http.RequestMethod> acceptedMethods() {
        return null;
    }

    /**
     * Returns {@code true} if this record accepts provided method.
     *
     * @param method An HTTP method.
     * @return {@code true} if this record accepts provided method.
     */
    default boolean accepts(Http.RequestMethod method) {
        return false;
    }

    /**
     * Abstract parent for {@link Route routes} using {@link Http.RequestMethod HTTP method}.
     */
    class HttpMethodPredicate implements Predicate<Http.RequestMethod> {

        private final boolean allMethods;
        private final EnumSet<Http.Method> standardMethods;
        private final Set<Http.RequestMethod> otherMethods;

        HttpMethodPredicate(Collection<Http.RequestMethod> methods) {
            if (methods == null || methods.isEmpty()) {
                this.allMethods = true;
                this.standardMethods = null;
                this.otherMethods = null;
            } else {
                this.allMethods = false;
                this.otherMethods = new HashSet<>();
                Collection<Http.Method> sms = new ArrayList<>(methods.size());
                for (Http.RequestMethod method : methods) {
                    if (method instanceof Http.Method) {
                        sms.add((Http.Method) method);
                    } else {
                        otherMethods.add(method);
                    }
                }
                if (sms.isEmpty()) {
                    this.standardMethods = EnumSet.noneOf(Http.Method.class);
                } else {
                    this.standardMethods = EnumSet.copyOf(sms);
                }
            }
        }

        @Override
        public boolean test(Http.RequestMethod method) {
            if (allMethods) {
                return true;
            } else if (method instanceof Http.Method) {
                return standardMethods.contains(method);
            } else {
                return otherMethods.contains(method);
            }
        }

        public Set<Http.RequestMethod> acceptedMethods() {
            if (allMethods) {
                return Collections.emptySet();
            } else {
                HashSet<Http.RequestMethod> result = new HashSet<>(standardMethods.size() + otherMethods.size());
                result.addAll(standardMethods);
                result.addAll(otherMethods);
                return result;
            }
        }

    }
}
