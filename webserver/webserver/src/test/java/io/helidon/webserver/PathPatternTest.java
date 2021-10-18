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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The PathTemplateTest.
 */
public class PathPatternTest {

    private String normalize(String path) {
        String p = URI.create(path).normalize().getPath();
        if (p.charAt(p.length() - 1) == '/') {
            p = p.substring(0, p.length() - 1);
        }
        if (p.isEmpty()) {
            p = "/";
        }
        return p;
    }

    private void assertMatch(String path, String pattern) {
        PathMatcher matcher = PathPattern.compile(pattern);
        String nPath = normalize(path);
        assertThat("Pattern '" + pattern + "' doesn't match path '" + nPath + "'!",
                   matcher.match(nPath).matches(),
                   is(true));
    }

    private void assertNotMatch(String path, String pattern) {
        PathMatcher matcher = PathPattern.compile(pattern);
        String nPath = normalize(path);
        assertThat("Pattern '" + pattern + "' match path '" + nPath + "'!",
                   matcher.match(nPath).matches(),
                   is(false));
    }

    private void assertNotPrefixMatch(String path, String pattern) {
        PathMatcher matcher = PathPattern.compile(pattern);
        String nPath = normalize(path);
        assertThat("Pattern '" + pattern + "' left-match path '" + nPath + "'!",
                   matcher.match(nPath).matches(),
                   is(false));
    }

    private void doAssertMatchWithParams(boolean prefixMatch,
                                         String path,
                                         String pattern,
                                         String remainingPart,
                                         String... nameValue) {
        if ((nameValue.length % 2) > 0) {
            throw new IllegalArgumentException("Parameter 'nameValue' represents pairs of name and expected values. "
                                                       + "It must be provided in even count. But current count is "
                                                       + nameValue.length);
        }
        PathMatcher matcher = PathPattern.compile(pattern);
        String nPath = normalize(path);
        PathMatcher.Result result;
        if (prefixMatch) {
            result = matcher.prefixMatch(nPath);
        } else {
            result = matcher.match(nPath);
        }
        assertThat("Pattern '" + nPath + "' doesn't match path '" + nPath + "'!",
                   result.matches(),
                   is(true));
        Map<String, String> params = new HashMap<>(result.params());
        for (int i = 0; i < nameValue.length; i = i + 2) {
            String paramName = nameValue[i];
            String expectedValue = nameValue[i + 1];
            String value = params.remove(paramName);
            assertThat("Missing expected pattern parameter '" + paramName + "'!", value, notNullValue());
            assertThat("Parameter '" + paramName + "' value isn't expected '" + expectedValue + "' but '" + value + "'!"
                               + " Pattern: '" + pattern + "', path: '" + nPath + "'.",
                       value,
                       is(expectedValue));
        }
        if (!params.isEmpty()) {
            String collected = params.keySet().stream().collect(Collectors.joining(", "));
            throw new AssertionError("Pattern '" + pattern + "' resolves more then expected parameters in '"
                                             + nPath + "': " + collected);
        }
        if (prefixMatch) {
            PathMatcher.PrefixResult lres = (PathMatcher.PrefixResult) result;
            String rp = lres.remainingPart();
            assertThat("Right part isn't expected '" + remainingPart + "' but '" + rp + "'!"
                               + " Pattern: '" + pattern + "', path: '" + nPath + "'.",
                       rp,
                       is(remainingPart));
        }
    }

    private void assertMatchWithParams(String path, String pattern, String... nameValue) {
        doAssertMatchWithParams(false, path, pattern, null, nameValue);
    }

    private void assertPrefixMatchWithParams(String path, String pattern, String remainingPart, String... nameValue) {
        doAssertMatchWithParams(true, path, pattern, remainingPart, nameValue);
    }

    @Test
    public void testSlashesAtBeginning() throws Exception {
        assertMatch("/", "/");
        assertNotMatch("/", "");
        assertMatch("/a", "/a");
        assertNotMatch("/a", "a");
    }

    @Test
    public void testSlashesAtEnd() throws Exception {
        assertMatch("/foo/", "/foo");
        assertNotMatch("/foo/", "/foo/");
    }

    @Test
    public void testMultipliedSlashes() throws Exception {
        assertMatch("/a//b", "/a/b");
        assertNotMatch("/a//b", "/a//b");
    }

    @Test
    public void testNormalization() throws Exception {
        assertMatch("/a/./b", "/a/b");
        assertMatch("/a/b/../c", "/a/c");
    }

    @Test
    public void testUnendedParameter() throws Exception {
        assertThrows(IllegalPathPatternException.class, () -> {
            PathPattern.compile("/foo/{bar");
        });
    }

    @Test
    public void testUnendedOptional() throws Exception {
        assertThrows(IllegalPathPatternException.class, () -> {
            PathPattern.compile("/foo/[bar");
        });
    }

    @Test
    public void testNestedOptional() throws Exception {
        assertThrows(IllegalPathPatternException.class, () -> {
            PathPattern.compile("/foo/[bar[/baz]/l]");
        });
    }

    @Test
    public void testDecodingAndEscaping() throws Exception {
        assertMatch("/fo%2Bo/b%2Ca%3Br", "/fo+o/b,a;r");
        assertMatch("/fo%5Do", "/fo]o");
        assertMatch("/foo", "/f\\o\\o");
        assertMatch("/fo%5Bo%5D", "/fo\\[o\\]");
        assertMatch("/fo%7Bo", "/fo\\{o");
    }

    @Test
    public void testLeftMatch() throws Exception {
        assertNotMatch("/a/foo/c", "/a");
        assertPrefixMatchWithParams("/a/foo/c", "/a", "/foo/c");
        assertNotMatch("/a/foo/c", "/a/f");
        assertNotPrefixMatch("/a/foo/c", "/a/f"); // Left-match accepts full path segments
    }

    @Test
    public void testParams() throws Exception {
        assertMatchWithParams("/a/b/c", "/a/{var}/c", "var", "b");
        assertMatchWithParams("/foo/bar/baz", "/foo/{var1}/{var2}",
                              "var1", "bar",
                              "var2", "baz");
        assertMatchWithParams("/foo/bar/baz", "/foo/b{var1}/{var2}",
                              "var1", "ar",
                              "var2", "baz");
        assertMatchWithParams("/foo/bar/baz", "/foo/b{var1}r/{var2}",
                              "var1", "a",
                              "var2", "baz");
        assertNotMatch("/foo/car/baz", "/foo/b{var1}/{var2}");
        assertNotMatch("/foo/bar/baz", "/foo/{var}");
        assertPrefixMatchWithParams("/foo/bar/baz", "/foo/{var}", "/baz",
                                    "var", "bar");
        assertPrefixMatchWithParams("/foo/bar/baz", "/foo/{var1}/{var2}", "/",
                                    "var1", "bar",
                                    "var2", "baz");
        assertMatchWithParams("/foo/bar/baz", "/foo/{}/{var2}",
                              "var2", "baz");
    }

    @Test
    public void testCustomizedParams() throws Exception {
        assertMatchWithParams("/foo/b123/baz", "/foo/b{var:\\d+}/baz", "var", "123");
        assertNotMatch("/foo/bar/baz", "/foo/b{var:\\d+}/baz");
        assertMatchWithParams("/foo/b123/baz", "/foo/b{:\\d+}/baz");
    }

    @Test
    public void testGreedyParams() throws Exception {
        assertMatchWithParams("/foo/bar/baz", "/foo/{+var}", "var", "bar/baz");
        assertMatchWithParams("/foo/bar/baz", "/fo{+var}", "var", "o/bar/baz");
        assertMatchWithParams("/foo/bar/baz", "/foo/{+var}az", "var", "bar/b");
        assertPrefixMatchWithParams("/foo/bar/baz", "/foo/{+var}", "/", "var", "bar/baz");
        assertMatchWithParams("/foo/bar/baz/xxx", "/foo/{+var}/xxx", "var", "bar/baz");
        assertMatchWithParams("/foo/bar/baz/xxx", "/foo/{+}/xxx");
    }

    @Test
    public void testWildCard() throws Exception {
        assertMatch("/foo/bar", "/foo*");
        assertMatch("/foo/bar", "/foo/*");
        assertMatch("/foo/bar", "/foo/ba*");
        assertMatch("/foo/bar", "/foo[/*]");
        assertMatch("/foo/bar", "/foo[/ba*]");
        assertMatch("/foo/bar/baz", "/foo/*");
        assertMatch("/foo/bar/baz", "/foo/ba*");
        assertMatch("/foo/bar/baz", "/foo/*/*");
        assertMatch("/foo/bar/baz", "/foo/*/b*");
        assertNotMatch("/foobar", "/foo/*");
        assertMatchWithParams("/foo/bar/baz", "/foo[/{var}]/*", "var", "bar");
        assertMatchWithParams("/foo/bar/baz", "/foo/*[/{var}]", "var", "baz");
    }

    @Test
    public void testOptionals() throws Exception {
        assertMatch("/foo/bar", "/foo[/bar]");
        assertMatch("/foo", "/foo[/bar]");
        assertNotMatch("/foo/ba", "/foo[/bar]");
        assertMatchWithParams("/foo/bar", "/foo[/{var}]", "var", "bar");
        assertMatchWithParams("/foo", "/foo[/{var}]");
        assertNotMatch("/foo/bar/baz", "/foo[/{var}]");
        assertMatchWithParams("/foo/bar/baz", "/foo[/{var}]/baz", "var", "bar");
        assertMatchWithParams("/foo/baz", "/foo[/{var}]/baz");
    }
}
