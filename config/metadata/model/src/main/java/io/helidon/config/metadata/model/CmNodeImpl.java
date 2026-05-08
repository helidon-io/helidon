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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;

/**
 * {@link CmNode} implementation.
 */
abstract sealed class CmNodeImpl implements CmNode, Comparable<CmNode> {
    private final CmNode parent;
    private final String path;
    private final String key;
    private final Collection<CmType> types;
    private final List<CmNode> children;

    CmNodeImpl(CmNode parent, String path, String key, Collection<CmType> types, List<CmNode> children) {
        this.parent = parent;
        this.path = Objects.requireNonNull(path);
        this.key = Objects.requireNonNull(key);
        this.types = Objects.requireNonNull(types);
        this.children = Collections.unmodifiableList(Objects.requireNonNull(children));
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
    public List<CmType> types() {
        return List.copyOf(types);
    }

    @Override
    public List<CmNode> children() {
        return children;
    }

    @Override
    public int compareTo(CmNode other) {
        int compare = key.compareTo(other.key());
        if (compare == 0) {
            compare = path.compareTo(other.path());
        }
        return compare;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CmNode other)) {
            return false;
        }
        return key.equals(other.key()) && path.equals(other.path());
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, path);
    }

    static final class CmPathNodeImpl extends CmNodeImpl implements CmPathNode {
        CmPathNodeImpl(CmNode parent, String path, String key, Collection<CmType> types, List<CmNode> children) {
            super(parent, path, key, types, children);
        }

        @Override
        public String toString() {
            return "CmPathNodeImpl{"
                   + "path='" + path() + '\''
                   + ", key='" + key() + '\''
                   + ", types=" + types()
                   + '}';
        }
    }

    static final class CmOptionNodeImpl extends CmNodeImpl implements CmOptionNode {
        private final CmOption option;
        private final CmType type;

        CmOptionNodeImpl(CmNode parent,
                         String path,
                         String key,
                         Collection<CmType> types,
                         List<CmNode> children,
                         CmOption option,
                         CmType type) {
            super(parent, path, key, types, children);
            this.option = Objects.requireNonNull(option);
            this.type = type;
        }

        @Override
        public CmOption option() {
            return option;
        }

        @Override
        public Optional<CmType> type() {
            return Optional.ofNullable(type);
        }

        @Override
        public String toString() {
            return "CmOptionNodeImpl{"
                   + "option=" + option
                   + ", type=" + type
                   + ", path='" + path() + '\''
                   + ", key='" + key() + '\''
                   + ", types=" + types()
                   + '}';
        }
    }
}
