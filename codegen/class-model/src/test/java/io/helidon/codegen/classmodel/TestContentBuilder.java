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
import java.util.List;

class TestContentBuilder implements ContentBuilder<TestContentBuilder> {
    private final List<String> content = new ArrayList<>();
    private final StringBuilder currentLine = new StringBuilder();
    private final String padding = "    ";

    int currentPadding = 0;

    @Override
    public TestContentBuilder addContentLine(String line) {
        addContent(line);
        content.add(currentLine.toString());
        currentLine.delete(0, currentLine.length());
        return this;
    }

    @Override
    public TestContentBuilder content(List<String> content) {
        this.content.clear();
        this.content.addAll(content);
        return this;
    }

    @Override
    public TestContentBuilder addContent(String line) {
        if (currentLine.isEmpty()) {
            currentLine.append(padding.repeat(currentPadding + 1));
        }
        currentLine.append(line);
        return this;
    }

    @Override
    public TestContentBuilder addTypeToContent(String typeName) {
        addContent("@" + typeName + "@");
        return this;
    }

    @Override
    public TestContentBuilder padContent() {
        addContent(padding);
        return this;
    }

    @Override
    public TestContentBuilder padContent(int repetition) {
        addContent(padding.repeat(repetition));
        return this;
    }

    @Override
    public TestContentBuilder increaseContentPadding() {
        currentPadding++;
        return this;
    }

    @Override
    public TestContentBuilder decreaseContentPadding() {
        currentPadding--;
        return this;
    }

    @Override
    public TestContentBuilder clearContent() {
        content.clear();
        currentLine.delete(0, currentLine.length());
        currentPadding = 0;
        return this;
    }

    String generatedString() {
        if (!this.currentLine.isEmpty()) {
            this.content.add(this.currentLine.toString());
            this.currentLine.delete(0, this.currentLine.length());
        }
        return String.join("\n", this.content);
    }
}
