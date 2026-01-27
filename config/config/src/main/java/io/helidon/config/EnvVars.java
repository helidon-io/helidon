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

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import io.helidon.config.spi.ConfigNode;

class EnvVars {
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[^a-zA-Z0-9_]");
    private static final Pattern DISALLOWED_CHARS_ALLOW_DASH = Pattern.compile("[^a-zA-Z0-9_\\-]");
    private static final String UNDERSCORE = "_";

    private EnvVars() {
    }

    static Optional<ConfigNode> node(String key) {
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
            return Optional.of(ConfigNode.ValueNode.create(result));
        }

        /*
        2. Replace special chars (., _-)
        com.ACME.t-shirt.size -> com_ACME_t_shirt_size
         */
        String noSpecials = DISALLOWED_CHARS.matcher(key)
                .replaceAll(UNDERSCORE);
        result = System.getenv(noSpecials);
        if (result != null) {
            return Optional.of(ConfigNode.ValueNode.create(result));
        }

        /*
        3. Replace special chars except for - (dash), upper case, replace - (dash) with `_dash_`
         */
        String dashReplacement = DISALLOWED_CHARS_ALLOW_DASH.matcher(key)
                .replaceAll(UNDERSCORE)
                .toUpperCase(Locale.ROOT)
                .replaceAll("-", "_dash_");
        result = System.getenv(dashReplacement);
        if (result != null) {
            return Optional.of(ConfigNode.ValueNode.create(result));
        }

        /*
        4. Replace special chars except for - (dash), replace - (dash) with `_dash_`, upper case
         */
        String dashReplacementUpper = dashReplacement.toUpperCase(Locale.ROOT);
        result = System.getenv(dashReplacementUpper);
        if (result != null) {
            return Optional.of(ConfigNode.ValueNode.create(result));
        }

        /*
        Replace all special chars and uppercase
        5. com.ACME.t-shirt.size -> COM_ACME_T_SHIRT_SIZE
         */
        String noSpecialsUppercase = noSpecials.toUpperCase(Locale.ROOT);
        result = System.getenv(noSpecialsUppercase);
        if (result != null) {
            return Optional.of(ConfigNode.ValueNode.create(result));
        }

        return Optional.empty();
    }
}
