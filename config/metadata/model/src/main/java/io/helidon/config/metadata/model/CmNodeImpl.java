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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.metadata.model.CmModel.CmType;

/**
 * {@link CmNode} implementation.
 */
final class CmNodeImpl implements CmNode {
    private final CmNode parent;
    private final String path;
    private final String key;
    private final String typeName;
    private final CmType type;
    private final List<CmNodeImpl> children;

    CmNodeImpl(CmNode parent,
               String path,
               String key,
               String typeName,
               CmType type,
               List<CmNodeImpl> children) {

        this.parent = parent;
        this.path = path;
        this.key = key;
        this.typeName = typeName;
        this.type = type;
        this.children = children;
    }

    @Override
    public void visit(Visitor visitor) {
        var stack = new ArrayDeque<CmNodeImpl>();
        stack.push(this);
        var parent = this.parent;
        while (!stack.isEmpty()) {
            var node = stack.getFirst();
            if (node == parent) {
                visitor.postVisit(node);
                parent = node.parent;
                stack.pop();
            } else {
                if (visitor.visit(node)) {
                    var children = node.children;
                    for (int i = children.size() - 1; i >= 0; i--) {
                        stack.push(children.get(i));
                    }
                }
                if (parent != node.parent) {
                    throw new IllegalStateException("Parent mismatch");
                }
                parent = node;
            }
        }
    }

    @Override
    public Optional<CmNode> parent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String typeName() {
        return typeName;
    }

    @Override
    public Optional<CmType> type() {
        return Optional.ofNullable(type);
    }

    @Override
    public List<CmNode> children() {
        return Collections.unmodifiableList(children);
    }

    void addChild(CmNodeImpl child) {
        children.add(child);
    }

    @Override
    public int compareTo(CmNode o) {
        return key.compareTo(o.key());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (CmNodeImpl) obj;
        return Objects.equals(this.path, that.path)
               && Objects.equals(this.key, that.key)
               && Objects.equals(this.typeName, that.typeName)
               && Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "CmNodeImpl{"
               + ", path=" + path
               + ", key=" + key
               + ", typeName=" + typeName
               + ", type=" + type
               + '}';
    }
}
