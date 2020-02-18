/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser.Content;
import io.helidon.config.spi.ConfigParserException;

import org.junit.jupiter.api.Test;

import static io.helidon.config.PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES;
import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link PropertiesConfigParser}.
 */
public class PropertiesConfigParserTest {

    @Test
    public void testGetSupportedMediaTypes() {
        PropertiesConfigParser parser = new PropertiesConfigParser();
        assertThat(parser.supportedMediaTypes(), containsInAnyOrder(MEDIA_TYPE_TEXT_JAVA_PROPERTIES));
    }

    @Test
    public void testParse() {
        PropertiesConfigParser parser = new PropertiesConfigParser();
        ConfigNode.ObjectNode node = parser.parse(createContent("aaa = bbb"));

        assertThat(node.entrySet(), hasSize(1));
        assertThat(node.get("aaa"), valueNode("bbb"));
    }

    @Test
    public void testParseThrowsConfigParserException() {
        assertThrows(ConfigParserException.class, () -> {
            PropertiesConfigParser parser = new PropertiesConfigParser();
            parser.parse(createContent(null));
        });
    }

    private Content createContent(String content) {
        if (null == content) {
            return Content.builder().build();
        }

        return Content.builder()
                .data(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
                .charset(StandardCharsets.UTF_8)
                .build();
    }
}
