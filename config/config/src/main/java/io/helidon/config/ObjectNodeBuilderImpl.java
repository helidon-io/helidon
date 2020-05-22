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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * Implementation of {@link ObjectNode.Builder}.
 * This class is {@code public} for the time being, though it should not be.
 */
public class ObjectNodeBuilderImpl extends AbstractNodeBuilderImpl<String, ObjectNode.Builder> implements ObjectNode.Builder {

    private final Map<String, MergeableNode> members;
    private String value;

    /**
     * Initialize object builder.
     */
    ObjectNodeBuilderImpl() {
        this(Function.identity());
    }

    /**
     * Initialize object builder.
     *
     * @param tokenResolver a token resolver
     */
    public ObjectNodeBuilderImpl(Function<String, String> tokenResolver) {
        super(tokenResolver);
        this.members = new HashMap<>();
    }

    /**
     * Create a new builder instance.
     *
     * @return a new builder
     */
    public static ObjectNodeBuilderImpl create() {
        return new ObjectNodeBuilderImpl();
    }

    /**
     * Creates new instance of the builder initialized from original map of members.
     *
     * @param members initial members
     * @return new builder instance
     */
    public static ObjectNodeBuilderImpl create(Map<String, ConfigNode> members) {
        return create(members, Function.identity());
    }

    /**
     * Creates new instance of the builder initialized from original map of members.
     *
     * @param members              initial members
     * @param resolveTokenFunction a function resolving key token
     * @return new builder instance
     */
    public static ObjectNodeBuilderImpl create(Map<String, ConfigNode> members, Function<String, String> resolveTokenFunction) {
        return new ObjectNodeBuilderImpl(resolveTokenFunction).fromMap(members);
    }

    /**
     * Creates new instance of the builder initialized from original map of members.
     *
     * @param members initial members
     * @return new builder instance
     */
    private ObjectNodeBuilderImpl fromMap(Map<String, ConfigNode> members) {
        members.forEach(this::addNode);
        return this;
    }

    /**
     * Sets new member into the map.
     *
     * @param name node name
     * @param node new node
     * @return modified builder
     */
    @Override
    public ObjectNodeBuilderImpl addNode(String name, ConfigNode node) {
        members.put(tokenResolver().apply(name), wrap(node, tokenResolver()));
        return this;
    }

    @Override
    protected String id(MergingKey key) {
        return key.first();
    }

    @Override
    protected MergeableNode member(String name) {
        return members.computeIfAbsent(name, (k) -> new ObjectNodeImpl(Map.of(), tokenResolver()));
    }

    @Override
    protected void update(String name, MergeableNode node) {
        members.put(tokenResolver().apply(name), node);
    }

    @Override
    protected void merge(String name, MergeableNode node) {
        try {
            members.merge(name, node, MergeableNode::merge);
        } catch (ConfigException ex) {
            throw new ConfigException(name + ": " + ex.getLocalizedMessage(), ex);
        }
    }

    private String encodeDotsInTokenReferences(String key) {
        return key.replaceAll("\\.+(?=[^(\\$\\{)]*\\})", "~1");
    }

    /**
     * Configure direct value of this node.
     *
     * @param value the value
     * @return modified builder
     */
    @Override
    public ObjectNodeBuilderImpl value(String value) {
        this.value = value;
        return this;
    }

    // this is a shortcut method to keep current fluent code
    // even though value is now optional
    ObjectNodeBuilderImpl value(Optional<String> value) {
        value.ifPresent(this::value);
        return this;
    }

    @Override
    public ObjectNode.Builder addValue(String key, ConfigNode.ValueNode value) {
        return deepMerge(MergingKey.of(encodeDotsInTokenReferences(tokenResolver().apply(key))), ValueNodeImpl.wrap(value));
    }

    @Override
    public ObjectNode.Builder addObject(String key, ObjectNode object) {
        return deepMerge(MergingKey.of(encodeDotsInTokenReferences(tokenResolver().apply(key))),
                         ObjectNodeImpl.wrap(object, tokenResolver()));
    }

    @Override
    public ObjectNode.Builder addList(String key, ListNode list) {
        return deepMerge(MergingKey.of(encodeDotsInTokenReferences(tokenResolver().apply(key))),
                         ListNodeImpl.wrap(list, tokenResolver()));
    }

    @Override
    public ObjectNodeImpl build() {
        return new ObjectNodeImpl(Collections.unmodifiableMap(members), tokenResolver(), value);
    }

    @Override
    public String toString() {
        return "ObjectNodeBuilderImpl{"
                + "members=" + members
                + "} " + super.toString();
    }
}
