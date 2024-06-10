/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen.classmodel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class ImportSorter {

    private ImportSorter() {
    }

    static List<List<String>> sortImports(Collection<String> imports) {
        if (imports.isEmpty()) {
            return List.of();
        }
        List<String> sorted = imports.stream().sorted().toList();
        List<String> javaImports = new ArrayList<>();
        List<String> javaxImports = new ArrayList<>();
        List<String> helidonImports = new ArrayList<>();
        List<String> everythingElse = new ArrayList<>();
        for (String val : sorted) {
            if (val.startsWith("java.")) {
                javaImports.add(val);
            } else if (val.startsWith("javax.")) {
                javaxImports.add(val);
            } else if (val.startsWith("io.helidon.")) {
                helidonImports.add(val);
            } else {
                everythingElse.add(val);
            }
        }
        return List.of(javaImports, javaxImports, helidonImports, everythingElse);
    }

}
