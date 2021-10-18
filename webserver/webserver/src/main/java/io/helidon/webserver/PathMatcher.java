/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.util.Map;

/**
 * URI Path Matcher.
 * It is primary intended for use in Routing implementation.
 * <p>Matched URI path is always <b>decoded</b>, <b>{@link java.net.URI#normalize() normalized}</b> and
 * with <b>removed single ended slash</b> (if any).
 *
 * <h3><a id="pattern">Web Server Path Pattern</a></h3>
 * While user can implement this interface to implement any specific needs the primary construction method is
 * {@link #create(String)} factory method. The method accepts <i>Web Server Path Pattern</i> format.
 *
 * <table class="config">
 * <caption><b>Web Server path pattern description</b></caption>
 *
 * <tr>
 * <th id="construct">Construct Example</th>
 * <th id="description">Description</th>
 * </tr>
 *
 * <tr><td headers="construct">{@code /foo/bar/b,a+z}</td>
 *     <td headers="description">Exact canonical path match. (Including decoded characters.)</td></tr>
 * <tr><td headers="construct">{@code /foo/{var}}</td>
 *     <td headers="description">Named regular expression segment.
 *         Name is <i>var</i> and regexp segment is {@code ([^/]+)}. Use {@link Result#param(String)} method to get value
 *         of the segment.</td></tr>
 * <tr><td headers="construct">{@code /foo/{}}</td>
 *     <td headers="description">Nameless regular expression segment. Regexp segment is {@code ([^/]+)}</td></tr>
 * <tr><td headers="construct">{@code /foo/{var:\d+}}</td>
 *     <td headers="description">Named regular expression segment with specified expression.</td></tr>
 * <tr><td headers="construct">{@code /foo/{:\d+}}</td>
 *     <td headers="description">Nameless regular expression segment with specified expression.</td></tr>
 * <tr><td headers="construct">{@code /foo/{+var}}</td>
 *     <td headers="description">A convenience shortcut for {@code /foo/{var:.+}}.</td></tr>
 * <tr><td headers="construct">{@code /foo/{+}}</td>
 *     <td headers="description">A convenience shortcut for {@code /foo/{:.+}}.</td></tr>
 * <tr><td headers="construct">{@code /foo[/bar]}</td>
 *     <td headers="description">A optional section. Translated to regexp: {@code /foo(/bar)?}</td></tr>
 * <tr><td headers="construct">{@code /* or /foo*}</td>
 *     <td headers="description">Wildcard character can be matched with any number of characters.</td></tr>
 * </table>
 */
public interface PathMatcher {

    /**
     * Creates new instance from provided Web Server path pattern.
     * See class level javadoc for the path pattern description.
     *
     * @param pathPattern Web Server path pattern.
     * @return new instance.
     * @throws NullPointerException if parameter {@code pathPattern} is {@code null}.
     * @throws IllegalPathPatternException if provided pattern is not valid Web Server path pattern.
     */
    static PathMatcher create(String pathPattern) throws NullPointerException, IllegalPathPatternException {
        return PathPattern.compile(pathPattern);
    }

    /**
     * Matches this matcher against a URI path.
     *
     * @param path resolved and normalized URI path to test against.
     * @return a {@link Result} of the test.
     * @throws NullPointerException in case that {@code path} parameter is {@code null}.
     */
    Result match(CharSequence path);

    /**
     * Matches this matcher against a left part (prefix) of URI path with granularity on the path segment.
     * It means that accepted 'prefix' cannot break path segment and 'remaining-part' MUST start with slash '/' character.
     *
     * @param path resolved and normalized URI path to test against.
     * @return a {@link Result} of the test.
     * @throws NullPointerException in case that {@code path} parameter is {@code null}.
     */
    PrefixResult prefixMatch(CharSequence path);

    /**
     * The result of matching a {@link PathMatcher} to a given URI path.
     */
    interface Result {

        /**
         * Whether the tested vector matches the associated path template.
         *
         * @return whether the test was successful
         */
        boolean matches();

        /**
         * Returns the values of parameters that were specified in the associated path template.
         *
         * @return an immutable map of parameters and their values
         */
        Map<String, String> params();

        /**
         * Get value for a given parameter of the associated path matcher.
         *
         * @param name a parameter name to get value.
         * @return a value of the given {@code parameter}.
         * @throws NullPointerException if the specified key is null and this map does not permit null keys.
         */
        String param(String name);
    }

    /**
     * The result of prefix matching a {@link PathMatcher} to a given URI path.
     */
    interface PrefixResult extends Result {

        /**
         * In case of prefix match this returns the reminder that wasn't matched.
         * Remaining must be relative URI to matched part. It means that it must be completed from whole path segments.
         * Result must be also <b>decoded</b> and <b>normalized</b>.
         *
         * @return the reminder in case of a successful prefix match.
         */
        String remainingPart();
    }

}
