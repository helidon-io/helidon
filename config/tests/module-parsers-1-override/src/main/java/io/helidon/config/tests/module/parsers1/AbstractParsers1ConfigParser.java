/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.tests.module.parsers1;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.ConfigHelper;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ValueNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;

/**
 * Testing implementation of {@code text/x-java-properties} media type.
 */
public abstract class AbstractParsers1ConfigParser implements ConfigParser {

    private static final String MEDIA_TYPE_TEXT_JAVA_PROPERTIES = "text/x-java-properties";

    @Override
    public Set<String> getSupportedMediaTypes() {
        return CollectionsHelper.setOf(MEDIA_TYPE_TEXT_JAVA_PROPERTIES);
    }

    @Override
    public ConfigNode.ObjectNode parse(Content content) throws ConfigParserException {
        Properties properties = new Properties();
        try {
            properties.load(ConfigHelper.createReader(content.asReadable()));
        } catch (IOException e) {
            throw new ConfigParserException("Cannot read from source.", e);
        }
        ConfigNode.ObjectNode.Builder rootBuilder = ConfigNode.ObjectNode.builder();
        properties.stringPropertyNames().forEach(k -> addValue(rootBuilder, k, ValueNode.from(properties.getProperty(k))));
        return rootBuilder.build();
    }

    /**
     * {@link ConfigNode.ObjectNode.Builder#addValue} hook.
     *
     * @param rootBuilder builder
     * @param key         key
     * @param value       value
     */
    protected void addValue(ConfigNode.ObjectNode.Builder rootBuilder, String key, ValueNode value) {
        rootBuilder.addValue(key, value);
    }

}
