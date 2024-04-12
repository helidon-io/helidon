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

package io.helidon.microprofile.testing.testng;

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
 * Defines the configuration as a String in {@link #content()} for the
 * given type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface ConfigBlob {

    /**
     * Specifies the format type of the {@link #content()}.
     *
     * It defaults to {@link Type#PROPERTIES}.
     *
     * @return the supported type
     */
    Type type() default Type.PROPERTIES;

    /**
     * Configuration content.
     *
     * @return String with content.
     */
    String content();

    /**
     * Different formats of the configuration content.
     */
    enum Type {

        /**
         * Properties format.
         */
        PROPERTIES {
            @Override
            protected ConfigSource configSource(String content) {
                Objects.requireNonNull(content);
                Properties p = new Properties();
                try {
                    p.load(new StringReader(content));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return MpConfigSources.create("PropertiesConfigBlob", p);
            }
        },
        /**
         * YAML format.
         */
        YAML {
            @Override
            protected ConfigSource configSource(String content) {
                Objects.requireNonNull(content);
                return YamlMpConfigSource.create("YamlConfigBlob", new StringReader(content));
            }
        };

        /**
         * Create the ConfigSource given the content.
         *
         * @param content
         * @return the configuration
         */
        protected abstract ConfigSource configSource(String content);
    }
}
