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

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;

/**
 * Common implementation of {@link ListNode.Builder} and {@link ObjectNode.Builder}.
 *
 * @param <ID> type of id
 * @param <B>  type of builder implementation
 */
public abstract class AbstractNodeBuilderImpl<ID, B> {

    private final B thisBuilder;
    private final Function<String, String> tokenResolver;

    @SuppressWarnings("unchecked")
    AbstractNodeBuilderImpl(Function<String, String> tokenResolver) {
        this.tokenResolver = tokenResolver;
        thisBuilder = (B) this;
    }

    /**
     * Wraps node into mergeable one.
     *
     * @param node original node
     * @return new instance of mergeable node or original node if already was mergeable.
     */
    static MergeableNode wrap(ConfigNode node) {
        return wrap(node, Function.identity());
    }

    /**
     * Wraps node into mergeable one.
     *
     * @param node                 original node
     * @param resolveTokenFunction a token resolver
     * @return new instance of mergeable node or original node if already was mergeable.
     */
    static MergeableNode wrap(ConfigNode node, Function<String, String> resolveTokenFunction) {
        switch (node.nodeType()) {
        case OBJECT:
            return ObjectNodeImpl.wrap((ObjectNode) node, resolveTokenFunction);
        case LIST:
            return ListNodeImpl.wrap((ListNode) node, resolveTokenFunction);
        case VALUE:
            return ValueNodeImpl.wrap((ValueNode) node);
        default:
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
        }
    }

    static String formatFrom(String from) {
        if (from != null) {
            return " from " + from;
        } else {
            return "";
        }
    }

    /**
     * Returns id computed from key.
     *
     * @param key key to be used to extract id from
     * @return id computed from key
     */
    protected abstract ID id(MergingKey key);

    /**
     * Returns member instance associated with specified id.
     *
     * @param id member id
     * @return member instance associated with specified id.
     */
    protected abstract MergeableNode member(ID id);

    /**
     * Updates/replaces a member of specified id by specified node.
     *
     * @param id   member id
     * @param node new node
     */
    protected abstract void update(ID id, MergeableNode node);

    /**
     * Merges a member of specified id with specified node.
     *
     * @param id   member id
     * @param node new node
     */
    protected abstract void merge(ID id, MergeableNode node);

    /**
     * Applies deep merging through whole structure.
     *
     * @param key  key of node
     * @param node node to be merged into
     * @return modified builder
     */
    protected B deepMerge(MergingKey key, MergeableNode node) {
        // compotes id from current key
        ID id = id(key);

        if (key.isLeaf()) {
            // merges leaf nodes
            merge(id, node);
        } else {
            // get current member associated with id
            MergeableNode member = member(id);
            // merges current member with specified node
            switch (member.nodeType()) {
            case OBJECT:
                mergeObjectMember((ObjectNode) member, key, node, id);
                break;
            case LIST:
                mergeListMember((ListNode) member, key, node, id);
                break;
            case VALUE:
                mergeValueMember((ValueNode) member, key, node, id);
                break;
            default:
                throw new IllegalArgumentException("Unsupported node type: " + member.getClass().getName());
            }
        }
        return thisBuilder;
    }

    private void mergeValueMember(ValueNode member, MergingKey key, MergeableNode node, ID id) {
        ObjectNode on = ObjectNodeBuilderImpl.create(Map.of(), tokenResolver).value(member.value()).build();
        ConfigNode merged = ObjectNodeBuilderImpl
                .create(on, tokenResolver) // make copy of member
                .value(on.value())
                .deepMerge(key.rest(), node) // merge it with specified node
                .build();

        update(id, wrap(merged, tokenResolver));
    }

    private void mergeListMember(ListNode member, MergingKey key, MergeableNode node, ID id) {
        try {
            // deep merge of list with specified node
            ConfigNode merged = ListNodeBuilderImpl.from(member, tokenResolver) // make copy of member
                    .value(member.value())
                    .deepMerge(key.rest(), node) // merge it with specified node
                    .build();
            // updates/replaces original member associated by id with new merged value
            update(id, wrap(merged, tokenResolver));
        } catch (ConfigException ex) {
            throw new ConfigException(id + ": " + ex.getLocalizedMessage(), ex);
        }
    }

    private void mergeObjectMember(ObjectNode member, MergingKey key, MergeableNode node, ID id) {
        try {
            // deep merge of object with specified node
            ConfigNode merged = ObjectNodeBuilderImpl
                    .create(member, tokenResolver) // make copy of member
                    .value(member.value())
                    .deepMerge(key.rest(), node) // merge it with specified node
                    .build();
            // updates/replaces original member associated by id with new merged value
            update(id, wrap(merged, tokenResolver));
        } catch (ConfigException ex) {
            throw new ConfigException(id + ": " + ex.getLocalizedMessage(), ex);
        }
    }

    Function<String, String> tokenResolver() {
        return tokenResolver;
    }

    /**
     * Internal config node key useful during internal structure building.
     */
    public static class MergingKey {

        private final String first;
        private final MergingKey rest;

        private MergingKey(String first, MergingKey rest) {
            Objects.requireNonNull(first, "first cannot be null");

            this.first = first;
            this.rest = rest;
        }

        /**
         * Creates instance of Key parsed from string representation.
         *
         * @param key fully-qualified key
         * @return new instance of Key
         */
        public static MergingKey of(String key) {
            Objects.requireNonNull(key, "key cannot be null");

            int index = key.indexOf('.');
            if (index == -1) {
                return new MergingKey(key, null);
            } else {
                return new MergingKey(key.substring(0, index), MergingKey.of(key.substring(index + 1)));
            }
        }

        /**
         * Returns first key token.
         *
         * @return first key token.
         */
        public String first() {
            return first;
        }

        /**
         * Returns a sub-key of the key. If the key represents a leaf node it returns {@code null}.
         *
         * @return a sub-key of the key.
         */
        public MergingKey rest() {
            return rest;
        }

        /**
         * Returns {@code true} in case the key represents a leaf node.
         *
         * @return {@code true} in case the key represents a leaf node.
         */
        public boolean isLeaf() {
            return (rest == null);
        }

        @Override
        public String toString() {
            return first + (rest != null ? "." + rest : "");
        }

    }
}
