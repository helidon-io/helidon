/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import io.helidon.config.spi.ConfigNode;

/**
 * Extension of {@link ConfigNode} that supports merging with other nodes.
 */
public interface MergeableNode extends ConfigNode {

    /**
     * Returns new instance mergeable node of same type of original one that merges also with specified node.
     *
     * @param node node to be used to merge with
     * @return new instance of mergeable node that combines original node with specified one
     * @throws ConfigException in case it if not possible to merge original node with new one.
     */
    MergeableNode merge(MergeableNode node) throws ConfigException;

    /**
     * Each node may have a direct value, and in addition may be an object node or a list node.
     * This method returns true for any node with direct value.
     *
     * @return true if this node contains a value
     */
    boolean hasValue();
}
