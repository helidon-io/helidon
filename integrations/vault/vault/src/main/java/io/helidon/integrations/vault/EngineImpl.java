/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault;

import java.util.Optional;

class EngineImpl<T extends Secrets> implements Engine<T> {
    private final Class<T> secrets;
    private final String type;
    private final Optional<String> version;
    private final String defaultMountPoint;

    EngineImpl(Class<T> secrets, String type, String defaultMountPoint) {
        this.secrets = secrets;
        this.type = type;
        this.version = Optional.empty();
        this.defaultMountPoint = defaultMountPoint;
    }

    EngineImpl(Class<T> secrets, String type, String defaultMountPoint, String version) {
        this.secrets = secrets;
        this.type = type;
        this.defaultMountPoint = defaultMountPoint;
        this.version = Optional.of(version);
    }

    static <T extends Secrets> Engine<T> create(Class<T> secrets, String type, String defaultMountPoint, String version) {
        return new EngineImpl<>(secrets, type, defaultMountPoint, version);
    }

    static <T extends Secrets> Engine<T> create(Class<T> secrets, String type, String defaultMountPoint) {
        return new EngineImpl<>(secrets, type, defaultMountPoint);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public Optional<String> version() {
        return version;
    }

    @Override
    public Class<T> secretsType() {
        return secrets;
    }

    @Override
    public String defaultMount() {
        return defaultMountPoint;
    }
}
