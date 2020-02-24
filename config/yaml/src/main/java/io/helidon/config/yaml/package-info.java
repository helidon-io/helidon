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

/**
 * YAML format ConfigParser implementation.
 * <p>
 * It supports {@value io.helidon.config.yaml.YamlConfigParser#MEDIA_TYPE_APPLICATION_YAML} format.
 * <p>
 * The parser implementation supports {@link java.util.ServiceLoader}, i.e. {@link io.helidon.config.Config.Builder}
 * can automatically load and register YAML ConfigParser instance,
 * if not {@link io.helidon.config.Config.Builder#disableParserServices() disabled}.
 * Priority of the {@code YamlConfigParser} to be used by {@link io.helidon.config.Config.Builder},
 * if loaded automatically as a {@link java.util.ServiceLoader service},
 * is {@value io.helidon.config.yaml.YamlConfigParser#PRIORITY}.
 * And of course it can be {@link io.helidon.config.Config.Builder#addParser(io.helidon.config.spi.ConfigParser)
 * registered programmatically} using {@link io.helidon.config.yaml.YamlConfigParser#create()}.
 * <p>
 * YAML integration is placed in {@code io.helidon.config.yaml} module.
 * Maven coordinates are {@code io.helidon.config:helidon-config-yaml}.
 *
 * @see io.helidon.config Configuration API
 * @see io.helidon.config.spi Configuration SPI
 */
package io.helidon.config.yaml;
