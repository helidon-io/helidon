/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.config.spi;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * MP Config source basis. Just extend this class to be both Helidon config source and an MP config source.
 * @param <S> Type of the stamp of this config source
 */
public abstract class AbstractMpSource<S> extends AbstractSource<ConfigNode.ObjectNode, S> implements ConfigSource, io.helidon.config.spi.ConfigSource {
    private final AtomicReference<Map<String, String>> currentValues = new AtomicReference<>();

    /**
     * Initializes config source from builder.
     *
     * @param builder builder to be initialized from
     */
    protected AbstractMpSource(Builder<?, ?, ?> builder) {
        super(builder);
    }

    @Override
    protected Data<ConfigNode.ObjectNode, S> processLoadedData(Data<ConfigNode.ObjectNode, S> data) {
        currentValues.set(loadMap(data.data()));
        return super.processLoadedData(data);
    }

    @Override
    public void init(ConfigContext context) {
        this.changes().subscribe(new Flow.Subscriber<Optional<ConfigNode.ObjectNode>>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Optional<ConfigNode.ObjectNode> item) {
                currentValues.set(loadMap(item));
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    @Override
    public Map<String, String> getProperties() {
        if (null == currentValues.get()) {
            currentValues.set(loadMap(load()));
        }

        return currentValues.get();
    }

    @Override
    public String getValue(String propertyName) {
        if (null == currentValues.get()) {
            currentValues.set(loadMap(load()));
        }
        return currentValues.get().get(propertyName);
    }

    @Override
    public String getName() {
        return description();
    }

    private static Map<String, String> loadMap(Optional<ConfigNode.ObjectNode> item) {
        if (item.isPresent()) {
            ConfigNode.ObjectNode node = item.get();
            Map<String, String> values = new TreeMap<>();
            processNode(values, "", node);
            return values;
        } else {
            return Map.of();
        }
    }

    private static void processNode(Map<String, String> values, String keyPrefix, ConfigNode.ObjectNode node) {
        node.forEach((key, configNode) -> {
            switch (configNode.nodeType()) {
            case OBJECT:
                processNode(values, key(keyPrefix, key), (ConfigNode.ObjectNode) configNode);
                break;
            case LIST:
                processNode(values, key(keyPrefix, key), ((ConfigNode.ListNode) configNode));
                break;
            case VALUE:
                break;
            default:
                throw new IllegalStateException("Config node of type: " + configNode.nodeType() + " not supported");
            }

            String directValue = configNode.get();
            if (null != directValue) {
                values.put(key(keyPrefix, key), directValue);
            }
        });
    }

    private static void processNode(Map<String, String> values, String keyPrefix, ConfigNode.ListNode node) {
        List<String> directValue = new LinkedList<>();
        Map<String, String> thisListValues = new HashMap<>();
        boolean hasDirectValue = true;

        for (int i = 0; i < node.size(); i++) {
            ConfigNode configNode = node.get(i);
            String nextKey = key(keyPrefix, String.valueOf(i));
            switch (configNode.nodeType()) {
            case OBJECT:
                processNode(thisListValues, nextKey, (ConfigNode.ObjectNode) configNode);
                hasDirectValue = false;
                break;
            case LIST:
                processNode(thisListValues, nextKey, (ConfigNode.ListNode) configNode);
                hasDirectValue = false;
                break;
            case VALUE:
                String value = configNode.get();
                directValue.add(value);
                thisListValues.put(nextKey, value);
                break;
            default:
                throw new IllegalStateException("Config node of type: " + configNode.nodeType() + " not supported");
            }
        }

        if (hasDirectValue) {
            values.put(keyPrefix, String.join(",", directValue));
        } else {
            values.putAll(thisListValues);
        }
    }

    private static String key(String keyPrefix, String key) {
        if (keyPrefix.isEmpty()) {
            return key;
        }
        return keyPrefix + "." + key;
    }
}
