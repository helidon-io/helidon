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

package io.helidon.config.internal;

import java.io.Reader;
import java.io.StringReader;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;

import static io.helidon.config.ValueNodeMatcher.valueNode;
import static io.helidon.config.internal.PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PropertiesConfigParser}.
 */
public class PropertiesConfigParserTest {

    @Test
    public void testGetSupportedMediaTypes() {
        PropertiesConfigParser parser = new PropertiesConfigParser();
        assertThat(parser.getSupportedMediaTypes(), containsInAnyOrder(MEDIA_TYPE_TEXT_JAVA_PROPERTIES));
    }

    @Test
    public void testParse() {
        PropertiesConfigParser parser = new PropertiesConfigParser();
        ConfigNode.ObjectNode node = parser.parse((StringContent) () -> "aaa = bbb");

        assertThat(node.entrySet(), hasSize(1));
        assertThat(node.get("aaa"), valueNode("bbb"));
    }

    @Test
    public void testParseThrowsConfigParserException() {
        Assertions.assertThrows(ConfigParserException.class, () -> {
                PropertiesConfigParser parser = new PropertiesConfigParser();
            parser.parse((StringContent) () -> null);
        });
    }

    //
    // helper
    //

    @FunctionalInterface
    private interface StringContent extends ConfigParser.Content {
        @Override
        default String getMediaType() {
            return MEDIA_TYPE_TEXT_JAVA_PROPERTIES;
        }

        @Override
        default Reader asReadable() {
            return new StringReader(getContent());
        }

        String getContent();
    }

}
