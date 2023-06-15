/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.faulttolerance;

import io.helidon.config.Config;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * This service will be part of Níma on Pico module.
 * It may use pico to get config sources exposed through pico.
 */
@Singleton
class ConfigService implements Provider<Config> {
    private static volatile Config config;
    static void config(Config config) {
        ConfigService.config = config;
    }

    @Override
    public Config get() {
        return config;
    }

    public static void main(String[] args) {
        Config asyncs = config.get("fault-tolerance.asyncs");
        asyncs.asNodeList()
                .ifPresent(beanConfigList -> {

                });
    }
}
