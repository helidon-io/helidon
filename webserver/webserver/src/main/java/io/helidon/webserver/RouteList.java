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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import io.helidon.common.http.Http;

/**
 * An immutable {@link java.util.List list} of {@link Route routes} optionally defined on some URI path context.
 */
class RouteList extends ArrayList<Route> implements Route {
    private static final long serialVersionUID = 1L;
    // must declare transient, as ArrayList is Serializable (and we are not)
    private final transient PathMatcher pathContext;
    private final transient HttpMethodPredicate methodPredicate;

    /**
     * Creates new instance.
     *
     * @param pathContext a URI path context of this container.
     * @param records List of sub-records.
     */
    RouteList(PathMatcher pathContext, Collection<Route> records) {
        super(records);

        boolean acceptSomeMethods = false;
        Set<Http.RequestMethod> acceptedMethods = new HashSet<>();

        for (Route record : records) {
            Set<Http.RequestMethod> mtds = record.acceptedMethods();
            if (mtds != null) {
                acceptSomeMethods = true;
                if (mtds.isEmpty()) {
                    acceptedMethods = Collections.emptySet();
                    break; // Some child accepts all, this accepts all.
                } else {
                    acceptedMethods.addAll(mtds);
                }
            }
        }
        if (acceptSomeMethods) {
            this.methodPredicate = new HttpMethodPredicate(acceptedMethods);
        } else {
            this.methodPredicate = null;
        }
        this.pathContext = pathContext;
    }

    /**
     * Creates new instance without URI path context.
     *
     * @param records List of sub-records.
     */
    RouteList(Collection<Route> records) {
        this(null, records);
    }

    PathMatcher getPathContext() {
        return pathContext;
    }

    @Override
    public Set<Http.RequestMethod> acceptedMethods() {
        return methodPredicate == null ? null : methodPredicate.acceptedMethods();
    }

    @Override
    public boolean accepts(Http.RequestMethod method) {
        return methodPredicate != null && methodPredicate.test(method);
    }

    /**
     * Matches path context against a left part (prefix) of URI path with granularity on the path segment.
     * It means that accepted 'prefix' cannot break path segment and 'remaining-part' MUST start with slash '/' character.
     *
     * @param path resolved and normalized URI path to test against.
     * @return a {@link PathMatcher.Result} of the test.
     * @throws NullPointerException in case that {@code path} parameter is {@code null}.
     */
    public PathMatcher.PrefixResult prefixMatch(CharSequence path) {
        return pathContext == null ? EMPTY_PATH_MATCHER.prefixMatch(path) : pathContext.prefixMatch(path);
    }

    // ***********************************
    // ***    Make a list immutable.   ***
    // ***********************************

    @Override
    public void trimToSize() {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public Route set(int index, Route route) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public boolean add(Route route) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public void add(int index, Route route) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public Route remove(int index) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public boolean addAll(Collection c) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public boolean addAll(int index, Collection c) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public boolean removeAll(Collection c) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public boolean retainAll(Collection c) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public boolean removeIf(Predicate filter) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public void replaceAll(UnaryOperator operator) {
        throw new UnsupportedOperationException("An immutable instance!");
    }

    @Override
    public void sort(Comparator c) {
        throw new UnsupportedOperationException("An immutable instance!");
    }
}
