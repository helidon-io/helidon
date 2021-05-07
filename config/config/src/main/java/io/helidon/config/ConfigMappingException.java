/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.lang.reflect.Type;

/**
 * Configuration value mapping exception.
 * <p>
 * Thrown when there is an error mapping a {@code String} configuration
 * value to a specific Java type.
 */
public class ConfigMappingException extends ConfigException {

    private static final long serialVersionUID = -5964426441508571154L;

    /**
     * Create new configuration value mapping exception with additional contextual details describing the failure.
     *
     * @param key    key associated with the mapped configuration value.
     * @param detail detailed information of mapping failure.
     */
    public ConfigMappingException(Config.Key key, String detail) {
        super("Mapping of value stored under key '" + key + "' has failed: " + detail);
    }

    /**
     * Create new configuration value mapping exception with additional contextual details describing the failure.
     *
     * @param key    key associated with the mapped configuration value.
     * @param detail detailed information of mapping failure.
     * @param cause  root exception cause.
     */
    public ConfigMappingException(Config.Key key, String detail, Throwable cause) {
        super("Mapping of value stored under key '" + key + "' has failed: " + detail,
              cause);
    }

    /**
     * Create new configuration value mapping exception with additional contextual details describing the failure.
     *
     * @param key    key associated with the mapped configuration value.
     * @param type   requested mapping type.
     * @param detail detailed information of mapping failure.
     */
    public ConfigMappingException(Config.Key key, Type type, String detail) {
        this(key, type, detail, null);
    }

    /**
     * Create new configuration value mapping exception with additional contextual details describing the failure.
     *
     * @param key    key associated with the mapped configuration value.
     * @param type   requested mapping type.
     * @param detail detailed information about the configuration value mapping failure.
     * @param cause  root exception cause.
     */
    public ConfigMappingException(Config.Key key, Type type, String detail, Throwable cause) {
        super("Mapping of value stored under key '" + key + "' to type '" + type.getTypeName() + "' has failed: " + detail,
              cause);
    }

    /**
     * Create new configuration value mapping exception with additional contextual details describing the failure.
     *
     * @param key   key associated with the mapped configuration value.
     * @param value mapped configuration value.
     * @param type  requested mapping type.
     * @param cause root exception cause.
     */
    public ConfigMappingException(Config.Key key, String value, Class<?> type, Throwable cause) {
        super("Mapping of value '" + value + "' stored under key '" + key + "' to type '" + type.getName() + "' has failed.",
              cause);
    }

    /**
     * Create new configuration value mapping exception with additional contextual details describing the failure.
     *
     * @param key    key associated with the mapped configuration value.
     * @param value  mapped configuration value.
     * @param detail detailed information about the configuration value mapping failure.
     * @param cause  root exception cause.
     */
    public ConfigMappingException(Config.Key key, String value, String detail, Throwable cause) {
        super("Mapping of value '" + value + "' stored under key '" + key + "' has failed: " + detail,
              cause);
    }
}
