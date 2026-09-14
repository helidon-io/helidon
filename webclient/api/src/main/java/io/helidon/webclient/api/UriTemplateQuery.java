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

package io.helidon.webclient.api;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriQueryWriteable;

final class UriTemplateQuery {
    private final String template;

    private Set<String> queryParamNames;

    UriTemplateQuery(String template) {
        this.template = template;
    }

    String template() {
        return template;
    }

    void replay(UriQuery source, UriQueryWriteable target, boolean absolute) {
        Set<String> names = queryParamNames;
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (!containsDecodedName(source, name)) {
                continue;
            }
            List<String> sourceValues = source.all(name);
            if (absolute
                    || !containsDecodedName(target, name)
                    || !target.all(name).equals(sourceValues)) {
                target.set(name, sourceValues.toArray(String[]::new));
            }
        }
    }

    void trackQueryParam(String name) {
        Set<String> names = queryParamNames;
        if (names == null) {
            names = new LinkedHashSet<>();
            queryParamNames = names;
        }
        names.add(name);
    }

    private static boolean containsDecodedName(UriQuery query, String name) {
        if (name.indexOf('%') >= 0 || name.indexOf('+') >= 0) {
            return query.names().contains(name);
        }
        return query.contains(name) || query.names().contains(name);
    }
}
