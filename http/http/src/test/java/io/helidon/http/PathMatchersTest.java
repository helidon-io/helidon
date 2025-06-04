/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.common.uri.UriPath;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class PathMatchersTest {
    static Stream<Arguments> subpathData() {
        return Stream.of(
                // DOCS: `/foo/{+}` - Convenience shortcut for unnamed segment with regular expression `{:.+}`
                Arguments.arguments(named("Requires subpath with at least one character",
                                          new TestData("/foo/{+}",
                                                       "/foo",
                                                       false,
                                                       false,
                                                       true,
                                                       true,
                                                       false))),
                arguments(named("Regular expression - any string with optional slash",
                                new TestData("/foo[/{:.*}]",
                                             "/foo",
                                             true,
                                             true,
                                             true,
                                             true,
                                             false))),
                arguments(named("Subpath placeholder {*} with optional slash",
                                new TestData("/foo[/{*}]",
                                             "/foo",
                                             true,
                                             true,
                                             true,
                                             true,
                                             false))),
                arguments(named("Subpath wildcard /*",
                                new TestData("/foo/*",
                                             "/foo",
                                             true,
                                             true,
                                             true,
                                             true,
                                             false)))

        );
    }

    @ParameterizedTest
    @MethodSource("subpathData")
    void testSubpathMatching(TestData testData) {
        PathMatcher matcher = PathMatchers.create(testData.pattern);

        String pathPrefix = testData.pathPrefix;
        PathMatchers.MatchResult match = matcher.match(UriPath.create(pathPrefix));
        assertThat("no trailing slash", match.accepted(), is(testData.noTrailing));

        match = matcher.match(UriPath.create(pathPrefix + "/"));
        assertThat("trailing slash", match.accepted(), is(testData.trailing));

        match = matcher.match(UriPath.create(pathPrefix + "/first"));
        assertThat("subpath", match.accepted(), is(testData.subpath));

        match = matcher.match(UriPath.create(pathPrefix + "/first/second"));
        assertThat("nested", match.accepted(), is(testData.nested));

        match = matcher.match(UriPath.create(pathPrefix + "suffix"));
        assertThat("suffix without slash", match.accepted(), is(testData.pathStartingWithString));
    }

    @Test
    void testRegexQuantifier() {
        var matcher = PathMatchers.pattern("/{id:\\w{2}}/name");
        var patternMatcher = (PathMatchers.PatternPathMatcher) matcher;

        var actualPattern = patternMatcher.pattern();

        assertThat(actualPattern.pattern(), is("/(?<gfXdbHQlk0>\\w{2})/name"));
    }

    @Test
    void testNormalization() {
        assertThat("/a/./b", pathMatcherMatches("/a/b"));
        assertThat("/a/b/../c", pathMatcherMatches("/a/c"));
    }

    @Test
    void testRootPrefixMatch() {
        // service on root path
        PathMatcher pathMatcher = PathMatchers.create("/");
        PathMatchers.PrefixMatchResult matched = pathMatcher.prefixMatch(UriPath.create("/greet/me"));
        assertThat(matched.accepted(), is(true));
        assertThat(matched.matchedPath().path(), is("/"));
        UriPath unmatchedPath = matched.unmatchedPath();
        assertThat(unmatchedPath.path(), is("/greet/me"));

        // inner routing of the service
        PathMatcher innerMatcher = PathMatchers.create("/greet/me");
        PathMatchers.MatchResult match = innerMatcher.match(unmatchedPath);
        assertThat(match.accepted(), is(true));
    }

    @Test
    void testPatternSimpleVar() {
        String pathVarPattern = "(?<var1>[^/]+)";

        patternTest(true, "a/" + pathVarPattern + "/c", "a/b/c", Map.of("var1", "b"));
        patternTest(false, "a/" + pathVarPattern + "/d", "a/b/c/d");

        patternTest(false, pathVarPattern + "/c", "a/b/c");
        patternTest(false, pathVarPattern, "a/b/c");
        patternTest(false, pathVarPattern, "a/b");
        patternTest(true, pathVarPattern, "a", Map.of("var1", "a"));
    }

    @Test
    void testSlashesAtBeginning() {
        assertThat("/", pathMatcherMatches("/"));
        // as we always match complete segments, pattern without starting slash is automatically updated to include it
        assertThat("/", pathMatcherMatches(""));
        assertThat("/a", pathMatcherMatches("/a"));
        // as we always match complete segments, pattern without starting slash is automatically updated to include it
        assertThat("/a", pathMatcherMatches("a"));
    }

    @Test
    void testMultipliedSlashes() {
        assertThat("/a//b", pathMatcherMatches("/a/b"));
        // this works, as we support matching on encoded unresolved path (only exact match)
        assertThat("/a//b", pathMatcherMatches("/a//b"));
        // prefixed works with resolved (decoded and normalized) path
        assertThat("/a//b", not(pathMatcherMatches("/a//*")));
    }

    @Test
    void testOptionals() {
        assertThat("/foo/bar", pathMatcherMatches("/foo[/bar]"));
        assertThat("/foo", pathMatcherMatches("/foo[/bar]"));
        assertThat("/foo/ba", not(pathMatcherMatches("/foo[/bar]")));
        assertThat("/foo/bar", pathMatcherMatches("/foo[/{var}]"));
        assertThat("/foo", pathMatcherMatches("/foo[/{var}]"));
        assertThat("/foo/bar/baz", not(pathMatcherMatches("/foo[/{var}]")));
        assertThat("/foo/bar/baz", pathMatcherMatches("/foo[/{var}]/baz"));
        assertThat("/foo/baz", pathMatcherMatches("/foo[/{var}]/baz"));
    }

    private static org.hamcrest.Matcher<String> pathMatcherMatches(String pattern) {
        PathMatcher matcher = PathMatchers.create(pattern);
        return new TypeSafeMatcher<>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("path pattern ").appendValue(pattern);
            }

            @Override
            protected boolean matchesSafely(String item) {
                UriPath uriPath = UriPath.create(item);
                return matcher.match(uriPath).accepted();
            }

            @Override
            protected void describeMismatchSafely(String item, Description mismatchDescription) {
                mismatchDescription.appendText("Pattern ")
                        .appendValue(pattern)
                        .appendText(" did not match path ")
                        .appendValue(item);
            }
        };
    }

    private void patternTest(boolean matches, String patternString, String input) {
        patternTest(matches, patternString, input, Map.of());
    }

    private void patternTest(boolean matches, String patternString, String input, Map<String, String> stringMap) {

        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(input);
        assertThat("", matcher.matches(), Is.is(matches));

        if (!matches) {
            return;
        }

        for (Map.Entry<String, String> entry : stringMap.entrySet()) {
            assertThat(matcher.group(entry.getKey()), Is.is(entry.getValue()));
        }
    }

    record TestData(String pattern,
                    String pathPrefix,
                    boolean noTrailing,
                    boolean trailing,
                    boolean subpath,
                    boolean nested,
                    boolean pathStartingWithString) {
    }
}