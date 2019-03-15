/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Provides environment variables and mapped variants. Mapping enables setting or overriding configuration with keys that are
 * unlikely to be legal as environment variables.
 * <p>
 * From the <a href="https://github.com/eclipse/microprofile-config/blob/master/spec/src/main/asciidoc/configsources.asciidoc">
 * specification</a>:
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
 * The spec assumes the mapping takes place during search, where the desired key is known, but Helidon config does not follow
 * that pattern. This implementation therefore produces additional KV pairs with variant keys for any variable whose name:
 * <ol>
 * <li>does <em>not</em> begin with a '_' <em>and</em></li>
 * <li>contains one or more '_' characters</li>
 * </ol>
 * Since Helidon supports many configuration keys that contain '-' (e.g. "server.executor-service.max-pool-size"), an additional
 * mapping is required to produce a matching variant. Given that it must map from legal environment variable names, and should
 * reduce the chances of inadvertent mappings, "_dash_" substrings are are first replaced by '-'.
 * <p>
 * The following mappings are applied to any environment variable name matching the above rules:
 * <ol>
 * <li>Replace "_dash_" by '-', e.g. "SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE" becomes
 * "SERVER_EXECUTOR-SERVICE_MAX-POOL-SIZE".</li>
 * <li>Replace '_' by '.' and add as an alternate, e.g. "com_ACME_size" becomes "com.ACME.size" and
 * "SERVER_EXECUTOR-SERVICE_MAX-POOL-SIZE" becomes "SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE".</li>
 * <li>Convert the result of step 2 to lowercase and add as an alternate, e.g. "com.ACME.size" becomes "com.acme.size" and
 * "SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE" becomes "server.executor-service.max-pool-size".
 * </li>
 * </ol>
 */
public class EnvironmentVariables {
    private static final Pattern DASH_PATTERN = Pattern.compile("_dash_", Pattern.LITERAL);
    private static final String UNDERSCORE = "_";
    private static final String DASH = "-";
    private static final char UNDERSCORE_CHAR = '_';
    private static final char DOT_CHAR = '.';

    /**
     * Returns the environment variables and their mapped variants.
     *
     * @return The map.
     */
    public static Map<String, String> expand() {
        return expand(System.getenv());
    }

    /**
     * Returns the environment variables and their mapped variants.
     *
     * @param env The environment variables.
     * @return The map.
     */
    public static Map<String, String> expand(final Map<String, String> env) {
        final Map<String, String> result = new HashMap<>(env.size());
        env.forEach((key, value) -> {
            if (shouldMap(key)) {
                String alternateKey = DASH_PATTERN.matcher(key).replaceAll(DASH);
                alternateKey = alternateKey.replace(UNDERSCORE_CHAR, DOT_CHAR);
                result.put(alternateKey, value);
                result.put(alternateKey.toLowerCase(), value);
            }
            result.put(key, value);
        });

        return result;
    }

    /**
     * Tests whether mappings should be created for the given key.
     *
     * @param key The key.
     * @return {@code true} if mappings should be created.
     */
    public static boolean shouldMap(final String key) {
        return key.length() > 1 && key.charAt(0) != UNDERSCORE_CHAR && key.contains(UNDERSCORE);
    }

    private EnvironmentVariables() {
    }
}
