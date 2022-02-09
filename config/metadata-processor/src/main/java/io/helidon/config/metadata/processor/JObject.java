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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

class JObject {
    private final Map<String, String> stringValues = new TreeMap<>();
    private final Map<String, Boolean> booleanValues = new TreeMap<>();
    private final Map<String, List<String>> stringListValues = new TreeMap<>();
    private final Map<String, JArray> arrayValues = new TreeMap<>();

    JObject add(String key, String value) {
        stringValues.put(key, value);
        return this;
    }

    JObject add(String key, boolean value) {
        booleanValues.put(key, value);
        return this;
    }

    JObject add(String key, JArray array) {
        arrayValues.put(key, array);
        return this;
    }

    JObject add(String key, List<String> values) {
        stringListValues.put(key, values);
        return this;
    }

    void write(PrintWriter metaWriter) {
        metaWriter.write('{');
        AtomicBoolean first = new AtomicBoolean(true);

        stringValues.forEach((key, value) -> {
            writeNext(metaWriter, first);
            metaWriter.write('\"');
            metaWriter.write(key);
            metaWriter.write("\":\"");
            metaWriter.write(escape(value));
            metaWriter.write('\"');
        });
        booleanValues.forEach((key, value) -> {
            writeNext(metaWriter, first);
            metaWriter.write('\"');
            metaWriter.write(key);
            metaWriter.write("\":");
            metaWriter.write(String.valueOf(value));
        });
        stringListValues.forEach((key, value) -> {
            writeNext(metaWriter, first);
            metaWriter.write('\"');
            metaWriter.write(key);
            metaWriter.write("\":[");
            writeStringList(metaWriter, value);
            metaWriter.write(']');
        });
        arrayValues.forEach((key, value) -> {
            writeNext(metaWriter, first);
            metaWriter.write('\"');
            metaWriter.write(key);
            metaWriter.write("\":");
            value.write(metaWriter);
        });

        metaWriter.write('}');
    }

    private void writeStringList(PrintWriter metaWriter, List<String> value) {
        metaWriter.write(value.stream()
                                 .map(this::escape)
                                 .map(this::quote)
                                 .collect(Collectors.joining(",")));
    }

    private String quote(String value) {
        return '"' + value + '"';
    }

    private String escape(String string) {
        return string.replaceAll("\n", "\\\\n")
                .replaceAll("\"", "\\\\\"");
    }

    private void writeNext(PrintWriter metaWriter, AtomicBoolean first) {
        if (first.get()) {
            first.set(false);
            return;
        }
        metaWriter.write(',');
    }
}
