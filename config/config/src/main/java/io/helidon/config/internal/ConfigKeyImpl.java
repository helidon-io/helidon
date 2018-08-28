/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import io.helidon.config.Config;

/**
 * Implementation of {@link Config.Key Config Key}.
 */
public class ConfigKeyImpl implements Config.Key {
    private final String name;
    private final ConfigKeyImpl parent;
    private final List<String> path;
    private final String fullKey;

    private ConfigKeyImpl(ConfigKeyImpl parent, String name) {
        Objects.requireNonNull(name, "name is mandatory");
        if (parent != null && name.equals("")) {
            if (parent.isRoot()) {
                //collapse roots to single one
                parent = null;
            } else {
                throw new IllegalArgumentException("Illegal empty name. Only root can be empty.");
            }
        }
        if (name.contains(".")) {
            throw new IllegalArgumentException("Illegal key token format. Dot character ('.') is not ");
        }

        this.parent = parent;
        List<String> path = new ArrayList<>();
        StringBuilder fullSB = new StringBuilder();
        if (parent != null) {
            path.addAll(parent.path);
            fullSB.append(parent.fullKey);
        }
        if (!name.equals("")) {
            if (fullSB.length() > 0) {
                fullSB.append(".");
            }
            path.add(name);
            fullSB.append(name);
        }
        this.name = Config.Key.unescapeName(name);
        this.path = Collections.unmodifiableList(path);
        fullKey = fullSB.toString();
    }

    @Override
    public ConfigKeyImpl parent() {
        return parent;
    }

    /**
     * Creates new root instance of ConfigKeyImpl.
     *
     * @return new instance of ConfigKeyImpl.
     */
    public static ConfigKeyImpl of() {
        return new ConfigKeyImpl(null, "");
    }

    /**
     * Creates new instance of ConfigKeyImpl.
     *
     * @param key key
     * @return new instance of ConfigKeyImpl.
     */
    public static ConfigKeyImpl of(String key) {
        return of().child(key);
    }

    /**
     * Creates new child instance of ConfigKeyImpl.
     *
     * @param key sub-key
     * @return new child instance of ConfigKeyImpl.
     */
    public ConfigKeyImpl child(String key) {
        return child(Arrays.asList(key.split("\\.")));
    }

    /**
     * Creates new child instance of ConfigKeyImpl.
     *
     * @param key sub-key
     * @return new child instance of ConfigKeyImpl.
     */
    public ConfigKeyImpl child(Config.Key key) {
        final List<String> path;
        if (key instanceof ConfigKeyImpl) {
            path = ((ConfigKeyImpl) key).path;
        } else {
            path = new LinkedList<>();
            while (!key.isRoot()) {
                path.add(0, key.name());
                key = key.parent();
            }
        }
        return child(path);
    }

    private ConfigKeyImpl child(List<String> path) {
        ConfigKeyImpl result = this;
        for (String name : path) {
            result = new ConfigKeyImpl(result, name);
        }
        return result;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return fullKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigKeyImpl key = (ConfigKeyImpl) o;
        return Objects.equals(name, key.name)
                && Objects.equals(parent, key.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, parent);
    }

    @Override
    public int compareTo(Config.Key that) {
        return toString().compareTo(that.toString());
    }
}
