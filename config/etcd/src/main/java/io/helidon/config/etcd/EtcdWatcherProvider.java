/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.config.etcd;

import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.spi.ChangeWatcherProvider;

/**
 * Service loader service for ETCD config source.
 */
public class EtcdWatcherProvider implements ChangeWatcherProvider {
    static final String TYPE = "etcd";

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }

    @Override
    public EtcdWatcher create(String type, Config metaConfig) {
        return EtcdWatcher.create(metaConfig);
    }

    @Override
    public Set<String> supported() {
        return Set.of(TYPE);
    }
}
