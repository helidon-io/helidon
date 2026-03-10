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

package io.helidon.config.metadata.docs;

import java.util.Arrays;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

class CmDocNames {

    private CmDocNames() {
    }

    static String shortPackageName(String pkg) {
        return Arrays.stream(pkg.split("\\."))
                .filter(not(String::isBlank))
                .map(s -> s.substring(0, 1))
                .collect(Collectors.joining("."));
    }

    static String shortTypeName(String typeName) {
        if (typeName.startsWith("java.")) {
            return typeName.substring(typeName.lastIndexOf('.') + 1);
        } else {
            int index = typeName.lastIndexOf('.');
            if (index > 0) {
                var pkg = typeName.substring(0, index);
                if (index + 1 < typeName.length()) {
                    var simpleName = typeName.substring(index + 1);
                    var shortPackageName = shortPackageName(pkg);
                    return shortPackageName + "." + simpleName;
                } else {
                    return pkg;
                }
            } else {
                return typeName;
            }
        }
    }
}
