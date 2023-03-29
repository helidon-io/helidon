/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http.processor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Templates {
    private Templates() {
    }

    static String loadTemplate(String templateProfile, String name) {
        String path = "templates/pico/" + templateProfile + "/" + name;
        try {
            InputStream in = Templates.class.getClassLoader().getResourceAsStream(path);
            if (in == null) {
                throw new RuntimeException("Could not find template " + path + " on classpath.");
            }
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
