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
package io.helidon.config.metadata.model;

import java.util.List;

import io.helidon.config.metadata.model.CmNode.CmOptionNode;
import io.helidon.config.metadata.model.CmNode.CmPathNode;

final class YamlGenerator implements CmNode.Visitor<Void> {
    private final StringBuilder sb = new StringBuilder();
    private int indent;

    static String render(List<? extends CmNode> roots) {
        var visitor = new YamlGenerator();
        for (var node : roots) {
            node.visit(visitor, null);
        }
        return visitor.toString();
    }

    @Override
    public boolean visit(CmNode node, Void arg) {
        switch (node) {
            case CmPathNode path -> {
                sb.repeat("  ", indent);
                sb.append(path.key());
                sb.append(":\n");
            }
            case CmOptionNode optionNode -> {
                sb.repeat("  ", indent);
                if (optionNode.children().isEmpty()) {
                    sb.append(optionNode.key());
                    sb.append(": <");
                    sb.append(optionNode.option().simpleTypeName());
                    sb.append(">");
                } else {
                    sb.append(optionNode.key());
                    sb.append(":");
                }
                sb.append("\n");
            }
        }
        if (!node.children().isEmpty()) {
            indent++;
        }
        return true;
    }

    @Override
    public void postVisit(CmNode node, Void arg) {
        if (!node.children().isEmpty()) {
            indent--;
        }
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
