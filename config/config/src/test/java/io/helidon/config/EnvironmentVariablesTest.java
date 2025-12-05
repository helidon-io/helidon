/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static io.helidon.config.EnvironmentVariables.shouldAlias;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Unit test for class {@link EnvironmentVariables}.
 */
public class EnvironmentVariablesTest {

    static Map<String, String> env(final String... additionalPairs) {
        // never use the current real environment
        Map<String, String> env = new HashMap<>();
        env.put("PATH", "/Users/theUser/bin");
        env.put("NO_PROXY", "localhost");
        env.put("COLORTERM", "truecolor");
        env.put("LOGNAME", "theUser");
        env.put("HOMEBREW.REPOSITORY", "/opt/homebrew");
        env.put("TERM_PROGRAM_VERSION", "465");
        env.put("PWD", "/Users/theUser/helidon");
        env.put("INFOPATH", "/opt/homebrew/share/info:");
        env.put("SHELL", "/bin/zsh");
        env.put("OLDPWD", "/Users/theUser/helidon");
        env.put("HOMEBREW_CELLAR", "/opt/homebrew/Cellar");
        env.put("TMPDIR", "/var/folders/0d/urgarhs/T/");
        env.put("XPC_FLAGS", "0x0");
        env.put("TERM_SESSION_ID", "ABCDEF");
        env.put("JAVA_HOME", "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home");
        env.put("TERM", "xterm-256color");
        env.put("HTTP_PROXY", "example.com:80");
        env.put("HOMEBREW_PREFIX", "/opt/homebrew");
        env.put("LANG", "en_US.UTF-8");
        env.put("HTTPS_PROXY", "example.com:80");
        env.put("HOMEBREW_REPOSITORY", "/opt/homebrew");
        env.put("XPC_SERVICE_NAME", "0");
        env.put("MAVEN_CMD_LINE_ARGS", " clean install");
        env.put("TERM_PROGRAM", "Apple_Terminal");
        env.put("MAVEN_PROJECTBASEDIR", "/Users/theUser/Development/GitHub/helidon");
        env.put("USER", "theUser");
        env.put("SSH_AUTH_SOCK", "/private/tmp/com.apple.launchd.asdfasdf/Listeners");
        env.put("OSLogRateLimit", "64");
        env.put("HOME", "/Users/theUser");
        env.put("XMODIFIERS", "@im=ibus");
        env.put("simple", "unmapped-env-value");
        env.put("FOO_BAR", "mapped-env-value");
        env.put("_underscore", "mappend-env-value");
        env.put("com_ACME_size", "mapped-env-value");
        env.put("SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE", "mapped-env-value");
        env.put("CONFIG_SOURCE_TEST_PROPERTY", "This Is My ENV VARS Value.");

        return add(env, additionalPairs);
    }

    static Map<String, String> toMap(final String... pairs) {
        return add(new HashMap<>(), pairs);
    }

    static Map<String, String> add(final Map<String, String> map, final String... additionalPairs) {
        IntStream.range(0, additionalPairs.length / 2)
                 .map(i -> i * 2)
                 .forEach(i -> map.put(additionalPairs[i], additionalPairs[i + 1]));
        return map;
    }

    static String variant(final String key) {
        return key.replace("_dash_", "-").replace("_", ".");
    }

    static String lowerVariant(final String key) {
        return variant(key).toLowerCase();
    }

    @Test
    public void testShouldNotAlias() {
        assertThat(shouldAlias(""), is(false));
        assertThat(shouldAlias("_"), is(false));
        assertThat(shouldAlias("__"), is(false));
        assertThat(shouldAlias("x__y"), is(false));
        assertThat(shouldAlias("_xy"), is(false));
        assertThat(shouldAlias("xy_"), is(false));
        assertThat(shouldAlias("xyz"), is(false));
    }

    @Test
    public void testNotAliased() {
        Map<String, String> env = toMap("", "v",
                                        "_", "v",
                                        "__", "v",
                                        "x__y", "v",
                                        "_xy", "v",
                                        "xy_", "v",
                                        "xyz", "v");
        Map<String, String> mapped = EnvironmentVariables.expand(env);
        assertThat(mapped, is(not(nullValue())));
        assertThat(mapped.size(), is(7));
        assertThat(mapped, hasEntry("", "v"));
        assertThat(mapped, hasEntry("_", "v"));
        assertThat(mapped, hasEntry("__", "v"));
        assertThat(mapped, hasEntry("x__y", "v"));
        assertThat(mapped, hasEntry("_xy", "v"));
        assertThat(mapped, hasEntry("xy_", "v"));
        assertThat(mapped, hasEntry("xyz", "v"));
    }

    @Test
    public void testShouldAlias() {
        assertThat(shouldAlias("x_y"), is(true));
        assertThat(shouldAlias("x_dash_y"), is(true));
    }

    @Test
    public void testCurrentEnvExpansion() {
        Map<String, String> env = env();
        Map<String, String> mapped = EnvironmentVariables.expand(env);
        assertThat(mapped, is(not(nullValue())));

        env.forEach((k, v) -> {
            if (shouldAlias(k)) {
                assertThat(mapped, hasEntry(k, v));
                assertThat(mapped, hasEntry(variant(k), v));
                assertThat(mapped, hasEntry(lowerVariant(k), v));
            }
        });
    }

    @Test
    public void testDashAliases() {
        Map<String, String> env = toMap("SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE", "16",
                                        "APP_PAGE_DASH_SIZE", "17");
        Map<String, String> expanded = EnvironmentVariables.expand(env);
        assertThat(expanded, is(not(nullValue())));
        assertThat(expanded.size(), is(6));

        assertThat(expanded, hasEntry("SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE", "16"));
        assertThat(expanded, hasEntry("SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE", "16"));
        assertThat(expanded, hasEntry("server.executor-service.max-pool-size", "16"));

        assertThat(expanded, hasEntry("APP_PAGE_DASH_SIZE", "17"));
        assertThat(expanded, hasEntry("APP.PAGE-SIZE", "17"));
        assertThat(expanded, hasEntry("app.page-size", "17"));
    }

    @Test
    public void testCamelCaseAliases() {
        Map<String, String> env = toMap("app_someKey", "v");
        Map<String, String> expanded = EnvironmentVariables.expand(env);
        assertThat(expanded, is(not(nullValue())));
        assertThat(expanded.size(), is(3));
        assertThat(expanded, hasEntry("app_someKey", "v"));
        assertThat(expanded, hasEntry("app.someKey", "v"));
        assertThat(expanded, hasEntry("app.somekey", "v"));
    }
}
