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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;

/**
 * Implementation of {@link ListNode.Builder}.
 */
public class ListNodeBuilderImpl extends AbstractNodeBuilderImpl<Integer, ListNode.Builder> implements ListNode.Builder {

    private final List<MergeableNode> elements;
    private String value;

    /**
     * Initialize list builder.
     */
    public ListNodeBuilderImpl() {
        this(Function.identity());
    }

    /**
     * Initialize list builder.
     *
     * @param resolveTokenFunctions a token resolver
     */
    public ListNodeBuilderImpl(Function<String, String> resolveTokenFunctions) {
        super(resolveTokenFunctions);
        elements = new LinkedList<>();
    }

    /**
     * Creates new instance of the builder initialized from original list of elements.
     *
     * @param elements initial elements
     * @return new builder instance
     */
    static ListNodeBuilderImpl from(List<ConfigNode> elements) {
        return from(elements, Function.identity());
    }

    /**
     * Creates new instance of the builder initialized from original list of elements.
     *
     * @param elements             initial elements
     * @param resolveTokenFunction a token resolver
     * @return new builder instance
     */
    static ListNodeBuilderImpl from(List<ConfigNode> elements, Function<String, String> resolveTokenFunction) {
        ListNodeBuilderImpl builder = new ListNodeBuilderImpl(resolveTokenFunction);
        elements.forEach(builder::addNode);
        return builder;
    }

    /**
     * Adds new element into the list.
     *
     * @param node new node
     * @return modified builder
     */
    public ListNodeBuilderImpl addNode(ConfigNode node) {
        elements.add(wrap(node));
        return this;
    }

    /**
     * Combine this list node with a value.
     *
     * @param value value to set
     * @return modified builder
     */
    @Override
    public ListNodeBuilderImpl value(String value) {
        this.value = value;
        return this;
    }

    // this is a shortcut method to keep current fluent code
    // even though value is now optional
    ListNodeBuilderImpl value(Optional<String> value) {
        value.ifPresent(this::value);
        return this;
    }

    @Override
    protected Integer id(MergingKey key) {
        String name = key.first();
        try {
            int index = Integer.parseInt(name);
            if (index < 0) {
                throw new ConfigException("Cannot merge an OBJECT member '" + name
                                                  + "' into a LIST element. Illegal negative index " + index + ".");
            }
            if (index >= elements.size()) {
                throw new ConfigException("Cannot merge an OBJECT member '" + name + "' into a LIST element. "
                                                  + "Index " + index + " out of bounds <0, " + (elements.size() - 1) + ">.");
            }
            return index;
        } catch (NumberFormatException ex) {
            throw new ConfigException("Cannot merge an OBJECT member '" + name + "' into a LIST element, not a number.", ex);
        }
    }

    @Override
    protected MergeableNode member(Integer index) {
        return elements.get(index);
    }

    @Override
    protected void update(Integer index, MergeableNode node) {
        elements.set(index, wrap(node));
    }

    @Override
    protected void merge(Integer index, MergeableNode node) {
        try {
            elements.set(index, elements.get(index).merge(node));
        } catch (ConfigException ex) {
            throw new ConfigException(index + ": " + ex.getLocalizedMessage(), ex);
        }
    }

    @Override
    public ListNode.Builder addValue(ValueNode value) {
        return addNode(value);
    }

    @Override
    public ListNode.Builder addObject(ObjectNode object) {
        return addNode(object);
    }

    @Override
    public ListNode.Builder addList(ListNode list) {
        return addNode(list);
    }

    @Override
    public ListNodeImpl build() {
        return new ListNodeImpl(elements, value);
    }

    @Override
    public String toString() {
        return "ListNodeBuilderImpl{"
                + "elements=" + elements
                + "} " + super.toString();
    }
}
