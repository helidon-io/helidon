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

package io.helidon.json.codegen;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.common.types.TypeName;

final class GeneratedNames {
    private final Set<String> names;
    private final Map<TypeName, String> serializers = new HashMap<>();
    private final Map<TypeName, String> deserializers = new HashMap<>();

    GeneratedNames() {
        this(Set.of());
    }

    GeneratedNames(Set<String> reservedNames) {
        names = new HashSet<>(reservedNames);
    }

    static String normalize(String preferredName) {
        String decomposed = Normalizer.normalize(preferredName, Normalizer.Form.NFD);
        StringBuilder identifier = new StringBuilder();
        for (int offset = 0; offset < decomposed.length();) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }
            if (codePoint < 128 && Character.isJavaIdentifierPart(codePoint) && !Character.isIdentifierIgnorable(codePoint)) {
                identifier.appendCodePoint(codePoint);
            } else {
                identifier.append('_');
            }
        }
        if (identifier.isEmpty() || identifier.toString().equals("_")) {
            return "NAME";
        }
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            identifier.insert(0, "NAME_");
        }
        return identifier.toString();
    }

    String serializer(TypeName type, String preferredName) {
        return serializers.computeIfAbsent(type, _ -> unique(preferredName));
    }

    String deserializer(TypeName type, String preferredName) {
        return deserializers.computeIfAbsent(type, _ -> unique(preferredName));
    }

    String unique(String preferredName) {
        String base = normalize(preferredName);
        String name = base;
        int suffix = 1;
        while (!names.add(name)) {
            name = base + "_" + suffix++;
        }
        return name;
    }
}
