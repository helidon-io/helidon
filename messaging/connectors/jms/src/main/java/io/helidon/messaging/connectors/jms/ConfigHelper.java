/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.messaging.connectors.jms;

import java.util.regex.Pattern;

class ConfigHelper {

    private ConfigHelper(){
        //noop
    }

    static final Pattern KEBAB_DEL = Pattern.compile("\\-([a-z])");
    static final Pattern SETTER_PREFIX = Pattern.compile("set([A-Za-z])");

    static String kebabCase2CamelCase(String val) {
        return KEBAB_DEL.matcher(val).replaceAll(res -> res.group(1).toUpperCase());
    }

    static String stripSet(String val) {
        return SETTER_PREFIX.matcher(val).replaceFirst(res -> res.group(1).toLowerCase());
    }
}
