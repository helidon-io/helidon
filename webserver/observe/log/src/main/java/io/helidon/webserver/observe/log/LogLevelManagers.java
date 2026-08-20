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

package io.helidon.webserver.observe.log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.logging.common.spi.LogLevelManager;
import io.helidon.logging.common.spi.LoggerLevel;

class LogLevelManagers {
    private final List<LogLevelManager> managers;

    private LogLevelManagers(List<LogLevelManager> managers) {
        this.managers = managers;
    }

    static LogLevelManagers create() {
        ServiceLoader<LogLevelManager> loader = ServiceLoader.load(LogLevelManager.class);
        List<LogLevelManager> managers = new ArrayList<>(HelidonServiceLoader.create(loader).asList());
        managers.add(new JulLogLevelManager());
        return new LogLevelManagers(List.copyOf(managers));
    }

    List<String> levels() {
        Set<String> result = new LinkedHashSet<>();
        managers.forEach(manager -> result.addAll(manager.levels()));
        return List.copyOf(result);
    }

    Map<String, LoggerLevel> loggers() {
        Map<String, LoggerLevel> result = new LinkedHashMap<>();
        for (LogLevelManager manager : managers) {
            manager.loggers().forEach(result::putIfAbsent);
        }
        return result;
    }

    Optional<LoggerLevel> logger(String name) {
        return manager(name).flatMap(manager -> manager.logger(name));
    }

    void setLevel(String name, String level) {
        manager(name)
                .orElseGet(() -> managers.get(0))
                .setLevel(name, level);
    }

    void unsetLevel(String name) {
        manager(name)
                .orElseGet(() -> managers.get(0))
                .unsetLevel(name);
    }

    private Optional<LogLevelManager> manager(String name) {
        for (LogLevelManager manager : managers) {
            if (manager.logger(name).isPresent()) {
                return Optional.of(manager);
            }
        }
        return Optional.empty();
    }
}
