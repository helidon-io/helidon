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

package io.helidon.pico.builder.tools;

class BuilderTemplateHelper {

    private BuilderTemplateHelper() {
    }

    public static String getDefaultGeneratedSticker(String generatorClassTypeName) {
        String[] generatedSticker = new String[] {
                "generator=" + generatorClassTypeName
        };

        StringBuilder result = new StringBuilder("// Generated(");
        int i = 0;
        for (String s : generatedSticker) {
            if (i++ > 0) {
                result.append(", ");
            }
            result.append("\"").append(s).append("\"");
        }
        if (result.length() > 0) {
            result.append(")");
        }

        return result.toString();
    }

}
