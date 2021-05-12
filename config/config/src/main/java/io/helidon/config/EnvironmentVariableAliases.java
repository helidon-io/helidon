/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Provides configuration key aliases in an attempt to map to legal environment variable names.
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
 * </pre>
 * <p>
 * Since Helidon supports many hyphenated configuration keys (e.g. {@code "server.executor-service.max-pool-size"}),
 * an additional mapping is required to produce aliases that can be expressed as environment variable names (e.g.
 * "SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE"); see {@link #aliasesOf(String)} for details.
 */
public class EnvironmentVariableAliases {
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[^a-zA-Z0-9_]");
    private static final Pattern DISALLOWED_CHARS_ALLOW_DASH = Pattern.compile("[^a-zA-Z0-9_\\-]");
    private static final Pattern DASH_PATTERN = Pattern.compile("-", Pattern.LITERAL);
    private static final String DASH_ENCODING = "_dash_";
    private static final String UNDERSCORE = "_";
    private static final char DASH_CHAR = '-';

    /**
     * Returns a list of aliases for the given config key.
     * <p>
     * If the key does not contain any {@code '-'} (dash) characters, all disallowed characters are
     * replaced by {@code '_'} (underscore) and this plus the uppercase variant are returned. For example,
     * {@code "app.qualifiedName"} and {@code "app/qualifiedName"} both result in the same two aliases:
     * <ol>
     * <li>"app_qualifiedName"</li>
     * <li>"APP_QUALIFIEDNAME"</li>
     * </ol>
     * If the key does contain any {@code '-'} (dash) characters, they are replaced by {@code "_dash_"} and by the
     * uppercase variant so that, e.g., {@code "app.page-size"} results in three aliases:
     * <ol>
     * <li>"app_page_dash_size"</li>
     * <li>"APP_PAGE_dash_SIZE"</li>
     * <li>"APP_PAGE_DASH_SIZE"</li>
     * </ol>
     *
     * @param key The configuration key.
     * @return The list of aliases.
     */
    public static List<String> aliasesOf(final String key) {
        if (key != null && !key.isEmpty()) {
            if (key.indexOf(DASH_CHAR) < 0) {
                String alias = DISALLOWED_CHARS.matcher(key).replaceAll(UNDERSCORE);
                if (alias.equals(key)) {
                    return singletonList(alias.toUpperCase());
                } else {
                    return asList(alias, alias.toUpperCase());
                }
            } else {
                String baseAlias = DISALLOWED_CHARS_ALLOW_DASH.matcher(key).replaceAll(UNDERSCORE);
                String aliasLowerDash = DASH_PATTERN.matcher(baseAlias).replaceAll(DASH_ENCODING);

                String upperBaseAlias = baseAlias.toUpperCase();
                String upperAliasLowerDash = DASH_PATTERN.matcher(upperBaseAlias).replaceAll(DASH_ENCODING);
                String upperAliasUpperDash = upperAliasLowerDash.toUpperCase();

                return asList(aliasLowerDash, upperAliasLowerDash, upperAliasUpperDash);
            }
        }
        return Collections.emptyList();
    }

    private EnvironmentVariableAliases() {
    }
}
