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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class ConfigNodeImpl implements Config {
    private final Map<String, ConfigNodeImpl> children = new ConcurrentHashMap<>();
    private final ConfigSourcesRuntime sources;
    private final Key parent;
    private final String name;
    private final Key key;

    private Config.Type type = null;
    private Optional<String> directValue;
    private ConfigNode node;

    ConfigNodeImpl(Builder builder) {
        ConfigSetup setup = new ConfigSetup(builder);

        this.sources = new ConfigSourcesRuntime(setup, builder.sources());
        this.sources.init();
        this.parent = ConfigKeyImpl.of();
        this.name = "";
        this.key = this.parent;
    }

    ConfigNodeImpl(ConfigSourcesRuntime sources, Key parent, String name, Key key) {
        this.sources = sources;
        this.parent = parent;
        this.name = name;
        this.key = key;
    }

    @Override
    public Config get(String key) {
        return get(Key.create(key));
    }

    @Override
    public Config get(Key key) {
        if (key.isRoot()) {
            return this;
        }
        // key = app.config.myMessage
        // or key = "enabled"
        List<String> elements = key.elements();
        String directChild = elements.get(0);
        Key childKey = this.key.child(key);

        if (elements.size() == 1) {
            return children.computeIfAbsent(directChild, it -> new ConfigNodeImpl(sources, this.key, it, childKey));
        }

        Key directChildKey = this.key.child(Key.create(directChild));
        // remove direct child key
        elements.remove(0);
        Key theRest = Key.create(elements);
        return children.computeIfAbsent(directChild, it -> new ConfigNodeImpl(sources, this.key, it, directChildKey))
                .get(theRest);
    }

    @Override
    public Optional<String> getValue() {
        readValue();
        if (type == Type.MISSING) {
            return Optional.empty();
        }
        return directValue;
    }

    @Override
    public Optional<Integer> asInt() {
        return getValue().map(Integer::parseInt);
    }

    @Override
    public Optional<Double> asDouble() {
        return getValue().map(Double::parseDouble);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> as(Class<T> type) {
        Optional<String> value = getValue();

        if (type.equals(Duration.class)) {
            return value.map(it -> (T) Duration.parse(it));
        }

        if (value.isEmpty()) {
            return Optional.empty();
        }
        throw new ConfigException("Cannot convert " + value.get() + " to " + type + ", not implemented");
    }

    @Override
    public String toString() {
        if (null == type) {
            return "UNKNOWN";
        }

        if (Type.MISSING == type) {
            return "MISSING";
        }

        String valueString = directValue.map(value -> ": " + value).orElse("");
        switch (type) {
        case OBJECT:
            return "OBJECT" + valueString;
        case LIST:
            return "LIST" + valueString;
        case VALUE:
            return "VALUE" + valueString;
        }

        return "wrong state of config node impl";
    }

    private synchronized void readValue() {
        if (type != null) {
            return;
        }

        Optional<ConfigNode> maybeNode = sources.getValue(key);

        if (maybeNode.isEmpty()) {
            type = Type.MISSING;
            return;
        }

        this.node = maybeNode.get();

        this.directValue = node.get();
        switch (node.nodeType()) {
        case OBJECT:
            this.type = Type.OBJECT;
            updateChildren((ConfigNode.ObjectNode) node);
            break;
        case LIST:
            this.type = Type.LIST;
            updateChildren((ConfigNode.ListNode) node);
            break;
        case VALUE:
            this.type = Type.VALUE;
            break;
        default:
            throw new IllegalStateException("Use of unsupported node type: " + node.nodeType());
        }
    }

    private void updateChildren(ConfigNode.ListNode node) {
        Key thisNodeKey = this.key;

        int size = node.size();
        for (int i = 0; i < size; i++) {
            String key = String.valueOf(i);
            this.children.computeIfAbsent(key,
                                          it -> new ConfigNodeImpl(sources,
                                                                   thisNodeKey,
                                                                   key,
                                                                   thisNodeKey.child(Key.create(key))));
        }
    }

    private void updateChildren(ConfigNode.ObjectNode node) {
        Key thisNodeKey = this.key;

        node.keySet().forEach(key -> {
            this.children.computeIfAbsent(key,
                                          it -> new ConfigNodeImpl(sources,
                                                                   thisNodeKey,
                                                                   key,
                                                                   thisNodeKey.child(Key.create(key))));
        });
    }
}
