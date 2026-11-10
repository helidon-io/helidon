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
package io.helidon.config;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.LazyConfigSource;

/**
 * Diagnostic state only; does not suppress source operations or change notifications.
 * The lock protects completed observations, never source I/O or application callbacks.
 */
final class ConfigSourceUpdates {
    private final ReentrantLock lock = new ReentrantLock();
    private final ConfigSource source;
    private Optional<ObjectNode> previous = Optional.empty();
    private boolean initialized;
    private volatile boolean failed;
    private boolean failedCheck;
    private boolean unavailable;

    ConfigSourceUpdates(ConfigSource source) {
        this.source = source;
    }

    Optional<ObjectNode> load(Supplier<Optional<ObjectNode>> loader) {
        var event = ConfigSourceUpdateEvent.beginObservation();
        try {
            var data = loader.get();
            if (event != null) {
                event.end();
            }
            lock.lock();
            try {
                boolean absent = data.isEmpty() && !source.optional() && !(source instanceof LazyConfigSource);
                String outcome;
                if (absent) {
                    outcome = unavailable ? null : "UNAVAILABLE";
                } else if (failed || unavailable) {
                    outcome = "RECOVERED";
                } else if (!initialized) {
                    outcome = "INITIAL";
                } else if (event != null && event.shouldCommit()
                        && !sameNode(previous.orElse(null), data.orElse(null))) {
                    outcome = data.isEmpty() ? "DELETED" : "CHANGED";
                } else {
                    outcome = null;
                }
                previous = data;
                initialized = true;
                failed = false;
                unavailable = absent;
                commit(event, outcome, null);
            } finally {
                lock.unlock();
            }
            return data;
        } catch (RuntimeException | Error e) {
            failed(event, e, false);
            throw e;
        }
    }

    boolean modified(BooleanSupplier check) {
        var event = ConfigSourceUpdateEvent.beginObservation();
        try {
            boolean modified = check.getAsBoolean();
            if (!modified) {
                unchanged(event);
            }
            return modified;
        } catch (RuntimeException | Error e) {
            failed(event, e, true);
            throw e;
        }
    }

    void changed() {
        var event = ConfigSourceUpdateEvent.beginObservation();
        if (event != null) {
            event.end();
        }
        // Event sources report changed nodes directly; neither keys nor nodes enter the diagnostic event.
        commit(event, "CHANGED", null);
    }

    // Compare only Helidon-owned implementations. Custom nodes are conservatively treated as changed,
    // without invoking their equals implementations or introducing additional application callbacks.
    private static boolean sameNode(ConfigNode previous, ConfigNode current) {
        if (previous == current) {
            return true;
        }
        if (previous == null || current == null || previous.getClass() != current.getClass()) {
            return false;
        }
        Class<?> type = previous.getClass();
        if (type != ObjectNodeImpl.class && type != ListNodeImpl.class && type != ValueNodeImpl.class) {
            return false;
        }
        if (!previous.value().equals(current.value())) {
            return false;
        }
        if (type == ObjectNodeImpl.class) {
            var oldObject = (ObjectNodeImpl) previous;
            var newObject = (ObjectNodeImpl) current;
            if (oldObject.size() != newObject.size()) {
                return false;
            }
            var newMembers = new HashMap<>(newObject);
            for (var entry : oldObject.entrySet()) {
                if (!sameNode(entry.getValue(), newMembers.get(entry.getKey()))) {
                    return false;
                }
            }
        } else if (type == ListNodeImpl.class) {
            var oldList = (ListNodeImpl) previous;
            var newList = (ListNodeImpl) current;
            if (oldList.size() != newList.size()) {
                return false;
            }
            for (int i = 0; i < oldList.size(); i++) {
                if (!sameNode(oldList.get(i), newList.get(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void unchanged(ConfigSourceUpdateEvent event) {
        if (event != null) {
            event.end();
        }
        if (!failed) {
            return;
        }
        lock.lock();
        try {
            // A successful stamp check cannot establish recovery from an unsuccessful load or parse.
            if (failed && failedCheck) {
                failed = false;
                commit(event, "RECOVERED", null);
            }
        } finally {
            lock.unlock();
        }
    }

    private void failed(ConfigSourceUpdateEvent event, Throwable failure, boolean checkFailure) {
        if (event != null) {
            event.end();
        }
        lock.lock();
        try {
            if (!failed) {
                failedCheck = checkFailure;
                commit(event, "FAILED", failure);
            }
            if (!checkFailure) {
                failedCheck = false;
            }
            failed = true;
        } finally {
            lock.unlock();
        }
    }

    private void commit(ConfigSourceUpdateEvent event, String outcome, Throwable failure) {
        if (event != null && outcome != null && event.shouldCommit()) {
            event.record(source.getClass().getName(), outcome, failure);
        }
    }
}
