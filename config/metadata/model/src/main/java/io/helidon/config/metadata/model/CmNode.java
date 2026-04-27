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
import java.util.List;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;
import io.helidon.config.metadata.model.CmNodeImpl.CmOptionNodeImpl;
import io.helidon.config.metadata.model.CmNodeImpl.CmPathNodeImpl;

/**
 * Resolved config metadata tree node.
 * <p>
 * <b>This class is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
@Api.Internal
public sealed interface CmNode extends Comparable<CmNode> permits CmNode.CmOptionNode,
                                                                  CmNode.CmPathNode,
                                                                  CmNodeImpl {

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
     * Node children.
     *
     * @return children
     */
    List<CmNode> children();

    /**
     * Exact config types represented at this path.
     *
     * @return exact path types
     */
    List<CmType> types();

    /**
     * Get the node identity.
     *
     * @return identity, never {@code null}
     */
    default String identity() {
        if (types().size() == 1) {
            return types().getFirst().typeName();
        } else {
            return path();
        }
    }

    /**
     * Visit this node in depth-first traversal order.
     *
     * @param visitor visitor
     * @param arg     traversal argument
     * @param <T>     traversal argument type
     */
    default <T> void visit(Visitor<T> visitor, T arg) {
        var stack = new ArrayDeque<CmNode>();
        stack.push(this);
        var parent = parent().orElse(null);
        while (!stack.isEmpty()) {
            var node = stack.getFirst();
            if (node == parent) {
                visitor.postVisit(node, arg);
                parent = node.parent().orElse(null);
                stack.pop();
            } else {
                if (visitor.visit(node, arg)) {
                    var children = node.children();
                    for (int i = children.size() - 1; i >= 0; i--) {
                        stack.push(children.get(i));
                    }
                }
                if (parent != node.parent().orElse(null)) {
                    throw new IllegalStateException("Parent mismatch");
                }
                parent = node;
            }
        }
    }

    /**
     * Pure path node.
     * <p>
     * Path nodes represent traversable config paths without a direct option
     * contract at the path itself.
     * </p>
     */
    sealed interface CmPathNode extends CmNode permits CmPathNodeImpl {
    }

    /**
     * Option node.
     * <p>
     * Option nodes model both scalar options and complex options.
     * </p>
     */
    sealed interface CmOptionNode extends CmNode permits CmOptionNodeImpl {

        /**
         * Resolved option metadata.
         *
         * @return option, never {@code null}
         */
        CmOption option();

        /**
         * Resolved complex option type.
         *
         * @return complex type, empty for scalar options
         */
        Optional<CmType> type();
    }

    /**
     * Visitor.
     *
     * @param <T> argument type
     */
    interface Visitor<T> {

        /**
         * Visit a node before its children.
         *
         * @param node node
         * @param arg  traversal argument
         * @return {@code true} to keep traversing, {@code false} to skip the
         *         subtree rooted at {@code node}
         */
        boolean visit(CmNode node, T arg);

        /**
         * Visit a node after traversing the nested nodes.
         *
         * @param node node
         * @param arg  traversal argument
         */
        default void postVisit(CmNode node, T arg) {
            // no-op
        }
    }
}
