/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.regex.Pattern;

import static java.util.Collections.unmodifiableMap;

/**
 * Provides environment variables that include aliases enabling setting or overriding configuration with keys that are
 * unlikely to be legal as environment variables.
 * <p>
 * The <a href="https://github.com/eclipse/microprofile-config/blob/master/spec/src/main/asciidoc/configsources.asciidoc">
 * MP config specification</a> describes the environment variables {@code ConfigSource} as follows:
 * <pre>
 * Some operating systems allow only alphabetic characters or an underscore, _, in environment variables. Other
 * characters such as ., /, etc may be disallowed. In order to set a value for a config property that has a name
 * containing such disallowed characters from an environment variable, the following rules are used.
 *
 * This ConfigSource searches 3 environment variables for a given property name (e.g. com.ACME.size):
 *
 *  1. Exact match (i.e. com.ACME.size)
 *  2. Replace the character that is neither alphanumeric nor _ with _ (i.e. com_ACME_size)
 *  3. Replace the character that is neither alphanumeric nor _ with _ and convert to upper case (i.e. COM_ACME_SIZE)
 *
 * The first environment variable that is found is returned by this ConfigSource.
 * </pre>
 * <p>
 * The spec assumes the mapping takes place during search, where the desired key is known, but Helidon merges
 * {@code ConfigSource}s instead; therefore this implementation produces <em>additional</em> KV pairs with aliases
 * for any variable that can meaningfully be mapped. See {@link #shouldAlias(String)} for the mapping criteria.
 * <p>
 * Since Helidon supports many configuration keys that contain {@code '-'} (e.g. {@code "server.executor-service.max-pool-size"}),
 * an additional mapping is required to produce a matching alias. Given that it must map from legal environment variable names
 * and reduce the chances of inadvertent mappings, a verbose mapping is used: {@code "_dash_"} substrings (upper and lower case)
 * are first replaced by {@code '-'}. See {@link #expand()} for the aliases produced.
 */
public final class EnvironmentVariables {
    private static final Pattern DASH_PATTERN = Pattern.compile("_dash_|_DASH_");
    private static final String UNDERSCORE = "_";
    private static final String DOUBLE_UNDERSCORE = "__";
    private static final String DASH = "-";
    private static final char UNDERSCORE_CHAR = '_';
    private static final char DOT_CHAR = '.';

    /**
     * Tests whether aliases should be created for the given environment variable name.
     * <p>
     * To provide a meaningful alias, the name must meet <em>all</em> of the following criteria:
     * <ol>
     * <li>does not begin or end with a {@code '_'} character</li>
     * <li>does not contain {@code "__"}</li>
     * <li>contains one or more {@code '_'} characters</li>
     * </ol>
     *
     * @param name The environment variable name.
     * @return {@code true} if aliases should be created.
     */
    public static boolean shouldAlias(final String name) {
        final int length = name.length();
        return length > 2
               && name.charAt(0) != UNDERSCORE_CHAR
               && name.charAt(length - 1) != UNDERSCORE_CHAR
               && name.contains(UNDERSCORE)
               && !name.contains(DOUBLE_UNDERSCORE);
    }

    /**
     * Returns the environment variables and their aliases.
     * <p>
     * The following mappings are applied to any environment variable name for which {@link #shouldAlias(String)} returns
     * {@code true}:
     * <ol>
     * <li>Replace {@code "_dash_"} by {@code '-'}, e.g. {@code "SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE"} becomes
     * {@code "SERVER_EXECUTOR-SERVICE_MAX-POOL-SIZE"}.</li>
     * <li>Replace {@code '_'} by {@code '.'} and add as a alias, e.g. {@code "com_ACME_size"} becomes {@code "com.ACME.size"}
     * and {@code "SERVER_EXECUTOR-SERVICE_MAX-POOL-SIZE"} becomes {@code "SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE"}. This mapping
     * is added primarily to support mixed case config keys such as {@code "app.someCamelCaseKey"}.</li>
     * <li>Convert the result of step 2 to lowercase and add as a alias, e.g. {@code "com.ACME.size"} becomes
     * {@code "com.acme.size"} and {@code "SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE"} becomes
     * {@code "server.executor-service.max-pool-size"}.
     * </li>
     * </ol>
     *
     * @return An unmodifiable copy of {@link System#getenv()} including aliases.
     */
    public static Map<String, String> expand() {
        return expand(System.getenv());
    }

    /**
     * Returns the environment variables and their aliases.
     * <p>
     * The following mappings are applied to any environment variable name for which {@link #shouldAlias(String)} returns
     * {@code true}:
     * <ol>
     * <li>Replace {@code "_dash_"} by {@code '-'}, e.g. {@code "SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE"} becomes
     * {@code "SERVER_EXECUTOR-SERVICE_MAX-POOL-SIZE"}.</li>
     * <li>Replace {@code '_'} by {@code '.'} and add as an alias, e.g. {@code "com_ACME_size"} becomes {@code "com.ACME.size"}
     * and {@code "SERVER_EXECUTOR-SERVICE_MAX-POOL-SIZE"} becomes {@code "SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE"}. This mapping
     * is added primarily to support mixed case config keys such as {@code "app.someCamelCaseKey"}.</li>
     * <li>Convert the result of step 2 to lowercase and add as an alias, e.g. {@code "com.ACME.size"} becomes
     * {@code "com.acme.size"} and {@code "SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE"} becomes
     * {@code "server.executor-service.max-pool-size"}.
     * </li>
     * </ol>
     *
     * @param env The environment variables.
     * @return An unmodifiable copy of {@code env} with aliases added.
     */
    public static Map<String, String> expand(final Map<String, String> env) {
        final Map<String, String> result = new HashMap<>(env.size());
        env.forEach((name, value) -> {
            result.put(name, value);
            if (shouldAlias(name)) {
                String alias = DASH_PATTERN.matcher(name).replaceAll(DASH);
                alias = alias.replace(UNDERSCORE_CHAR, DOT_CHAR);
                result.put(alias, value);
                result.put(alias.toLowerCase(), value);
            }
        });

        return unmodifiableMap(result);
    }

    private EnvironmentVariables() {
    }
}
