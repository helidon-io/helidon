/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.processor;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

class JArray {
    private final List<JObject> values = new LinkedList<>();

    public void add(JObject object) {
        values.add(object);
    }

    public void write(PrintWriter metaWriter) {
        metaWriter.write('[');

        for (int i = 0; i < values.size(); i++) {
            values.get(i).write(metaWriter);
            if (i < (values.size() - 1)) {
                metaWriter.write(',');
            }
        }

        metaWriter.write(']');
    }
}
