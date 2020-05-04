/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * Difference between two {@link Config}s, represented by the keys that were
 * added to or removed from the original {@code Config} or for which the key's
 * corresponding value changed.
 */
class ConfigDiff {

    private final Config config;
    private final Set<Config.Key> changedKeys;

    private ConfigDiff(Config config, Set<Config.Key> changedKeys) {
        this.config = config;
        this.changedKeys = changedKeys;
    }

    /**
     * Computes the difference between the first {@code Config} and the second
     * one.
     * @param origConfig original configuration
     * @param newConfig newer configuration
     * @return {@code ConfigDiff} representing the changes
     */
    static ConfigDiff from(Config origConfig, Config newConfig) {
        Stream<Config> forward = origConfig.traverse()
                .filter(origNode -> notEqual(origNode, newConfig.get(origNode.key())));

        Stream<Config> backward = newConfig.traverse()
                .filter(newNode -> notEqual(newNode, origConfig.get(newNode.key())));

        Set<Config.Key> changedKeys = Stream.concat(forward, backward)
                .map(Config::key)
                .distinct()
                .flatMap(ConfigDiff::expandKey)
                .collect(toSet());

        return new ConfigDiff(newConfig, changedKeys);
    }

    private static Stream<Config.Key> expandKey(Config.Key key) {
        Set<Config.Key> keys = new HashSet<>();
        expandKey(key, keys);
        return keys.stream();
    }

    private static void expandKey(Config.Key key, Set<Config.Key> keys) {
        keys.add(key);
        if (!key.isRoot()) {
            expandKey(key.parent(), keys);
        }
    }

    private static boolean notEqual(Config left, Config right) {
        if (left.type() != right.type()) {
            return true;
        }
        if (left.isLeaf()) {
            return !value(left).equals(value(right));
        }

        return false;
    }

    private static Optional<String> value(Config node) {
        if (node instanceof AbstractConfigImpl) {
            return ((AbstractConfigImpl) node).value();
        }
        return node.asString().asOptional();
    }

    /**
     *
     * @return {@code} true if there were no changes; {@code false} otherwise
     */
    boolean isEmpty() {
        return changedKeys.isEmpty();
    }

    /**
     *
     * @return the {@code Config.Key}s that were added, removed, or the values
     * for which were changed
     */
    Set<Config.Key> changedKeys() {
        return changedKeys;
    }

    /**
     *
     * @return the newer {@code Config} used in the comparison
     */
    Config config() {
        return config;
    }

}
