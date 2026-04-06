/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import io.helidon.config.spi.ConfigNode;

class EnvVars {
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[^a-zA-Z0-9_]");
    private static final Pattern DISALLOWED_CHARS_ALLOW_DASH = Pattern.compile("[^a-zA-Z0-9_\\-]");
    private static final Pattern DASH_PATTERN = Pattern.compile("_dash_|_DASH_");
    private static final String UNDERSCORE = "_";
    private static final String DOUBLE_UNDERSCORE = "__";
    private static final String DASH = "-";
    private static final char UNDERSCORE_CHAR = '_';
    private static final char DOT_CHAR = '.';
    private static final char DOT_SEPARATOR = '.';

    private EnvVars() {
    }

    static Optional<ConfigNode> node(String key) {
        Optional<ConfigNode> mapped = value(key).map(ConfigNode.ValueNode::create);

        return mapped.or(() -> objectNode(key));
    }

    private static Optional<String> value(String key) {
        /*
            The rules (from most exact to the least exact match):
            1. as is (com.ACME.size)
            2. all special characters (including dashes) replaced with _ (com_ACME_size)
            3. all special characters (except for dashes) replaced with _ and uppercased and dashes converted to `_dash_`
                    (com.ACME.t-shirt.size -> COM_ACME_T_dash_SHIRT_SIZE)
            4. same as above, but uppercased COM_ACME_T_DASH_SHIRT_SIZE
            5. all special characters replaced with _ and uppercased (COM_ACME_SIZE)
        */

        // 1. as is
        String result = System.getenv(key);
        if (result != null) {
            return Optional.of(result);
        }

        /*
        2. Replace special chars (., _-)
        com.ACME.t-shirt.size -> com_ACME_t_shirt_size
         */
        String noSpecials = noSpecials(key);
        result = System.getenv(noSpecials);
        if (result != null) {
            return Optional.of(result);
        }

        /*
        3. Replace special chars except for - (dash), upper case, replace - (dash) with `_dash_`
         */
        String dashReplacement = dashReplacement(key);
        result = System.getenv(dashReplacement);
        if (result != null) {
            return Optional.of(result);
        }

        /*
        4. Replace special chars except for - (dash), replace - (dash) with `_dash_`, upper case
         */
        String dashReplacementUpper = dashReplacement.toUpperCase(Locale.ROOT);
        result = System.getenv(dashReplacementUpper);
        if (result != null) {
            return Optional.of(result);
        }

        /*
        Replace all special chars and uppercase
        5. com.ACME.t-shirt.size -> COM_ACME_T_SHIRT_SIZE
         */
        String noSpecialsUppercase = noSpecials.toUpperCase(Locale.ROOT);
        result = System.getenv(noSpecialsUppercase);
        if (result != null) {
            return Optional.of(result);
        }

        return Optional.empty();
    }

    private static Optional<ConfigNode> objectNode(String key) {
        if (key.isEmpty()) {
            return Optional.empty();
        }

        Set<String> prefixes = prefixes(key);
        Set<String> candidateKeys = new LinkedHashSet<>();

        System.getenv().keySet().stream()
                .filter(envKey -> matchesAnyPrefix(envKey, prefixes))
                .forEach(envKey -> candidateKeys.addAll(candidateKeys(envKey)));

        if (candidateKeys.isEmpty()) {
            return Optional.empty();
        }

        Map<String, String> descendants = new HashMap<>();
        for (String candidateKey : candidateKeys) {
            if (isDescendant(key, candidateKey)) {
                value(candidateKey).ifPresent(value -> descendants.put(relativeKey(key, candidateKey), value));
            }
        }

        if (descendants.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ConfigUtils.mapToObjectNode(descendants, false));
    }

    private static Set<String> prefixes(String key) {
        Set<String> result = new LinkedHashSet<>();
        result.add(key + DOT_SEPARATOR);

        String noSpecials = noSpecials(key);
        String noSpecialsUppercase = noSpecials.toUpperCase(Locale.ROOT);
        String dashReplacement = dashReplacement(key);
        String dashReplacementUpper = dashReplacement.toUpperCase(Locale.ROOT);

        result.add(noSpecials + UNDERSCORE);
        result.add(noSpecialsUppercase + UNDERSCORE);
        result.add(dashReplacement + UNDERSCORE);
        result.add(dashReplacementUpper + UNDERSCORE);
        return result;
    }

    private static boolean matchesAnyPrefix(String envKey, Set<String> prefixes) {
        for (String prefix : prefixes) {
            if (envKey.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> candidateKeys(String envKey) {
        Set<String> result = new LinkedHashSet<>();
        result.add(envKey);

        if (shouldAlias(envKey)) {
            String dotted = DASH_PATTERN.matcher(envKey)
                    .replaceAll(DASH)
                    .replace(UNDERSCORE_CHAR, DOT_CHAR);
            result.add(dotted);
            result.add(dotted.toLowerCase(Locale.ROOT));
        }

        return result;
    }

    private static boolean shouldAlias(String name) {
        int length = name.length();
        return length > 2
                && name.charAt(0) != UNDERSCORE_CHAR
                && name.charAt(length - 1) != UNDERSCORE_CHAR
                && name.contains(UNDERSCORE)
                && !name.contains(DOUBLE_UNDERSCORE);
    }

    private static boolean isDescendant(String key, String candidateKey) {
        return candidateKey.startsWith(key)
                && candidateKey.length() > key.length()
                && candidateKey.charAt(key.length()) == DOT_SEPARATOR;
    }

    private static String relativeKey(String key, String candidateKey) {
        return candidateKey.substring(key.length() + 1);
    }

    private static String noSpecials(String key) {
        return DISALLOWED_CHARS.matcher(key).replaceAll(UNDERSCORE);
    }

    private static String dashReplacement(String key) {
        return DISALLOWED_CHARS_ALLOW_DASH.matcher(key)
                .replaceAll(UNDERSCORE)
                .toUpperCase(Locale.ROOT)
                .replace("-", "_dash_");
    }
}
