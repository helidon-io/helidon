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
import java.util.Optional;

import io.helidon.config.metadata.model.CmModel.CmType;

/**
 * Config metadata tree node.
 * <p>
 * <b>This class is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
public interface CmNode extends Comparable<CmNode> {

    /**
     * Parent node.
     *
     * @return parent, empty for root nodes
     */
    Optional<CmNode> parent();

    /**
     * Tree path of this node.
     *
     * @return path, never {@code null}
     */
    String path();

    /**
     * Node key.
     * May not be unique within the parent node.
     *
     * @return key, never {@code null}
     */
    String key();

    /**
     * Node type name.
     *
     * @return type name, never {@code null}
     */
    String typeName();

    /**
     * Node resolved type.
     *
     * @return resolved type, empty for value types
     */
    Optional<CmType> type();

    /**
     * Node children (options).
     *
     * @return children
     */
    List<CmNode> children();

    /**
     * Visit this node in depth-first traversal order.
     *
     * @param visitor visitor
     */
    void visit(Visitor visitor);

    /**
     * Visitor.
     */
    interface Visitor {

        /**
         * Visit a node.
         *
         * @param node node
         * @return {@code true} to keep traversing, {@code false} to stop
         */
        boolean visit(CmNode node);

        /**
         * Visit a node after traversing the nested nodes.
         *
         * @param node node
         */
        default void postVisit(CmNode node) {
            // no-op
        }
    }
}
