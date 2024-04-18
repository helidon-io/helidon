/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.testing.junit5;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.Properties;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.config.yaml.mp.YamlMpConfigSource;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Defines the configuration as a String in {@link #value()} for the
 * given type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface AddConfigBlock {

    /**
     * Specifies the format type of the {@link #value()}.
     *
     * It defaults to {@link Type#PROPERTIES}.
     *
     * @return the supported type
     */
    Type type() default Type.PROPERTIES;

    /**
     * Configuration value.
     *
     * @return String with value.
     */
    String value();

    /**
     * Different formats of the configuration.
     */
    enum Type {

        /**
         * Properties format.
         */
        PROPERTIES {
            @Override
            protected ConfigSource configSource(String value) {
                Objects.requireNonNull(value);
                Properties p = new Properties();
                try {
                    p.load(new StringReader(value));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return MpConfigSources.create("PropertiesAddConfigBlock", p);
            }
        },
        /**
         * YAML format.
         */
        YAML {
            @Override
            protected ConfigSource configSource(String value) {
                Objects.requireNonNull(value);
                return YamlMpConfigSource.create("YamlAddConfigBlock", new StringReader(value));
            }
        };

        /**
         * Create the ConfigSource given the value.
         *
         * @param value
         * @return the configuration
         */
        protected abstract ConfigSource configSource(String value);
    }
}
