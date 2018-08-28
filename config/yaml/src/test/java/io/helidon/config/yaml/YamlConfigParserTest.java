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

package io.helidon.config.yaml;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.yaml.internal.YamlConfigParser;

import static io.helidon.config.testing.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ConfigParser}.
 */
public class YamlConfigParserTest {

    private static final String COMPLEX_YAML =
            "name: Just for test\n"
                    + "type: dev-test\n"
                    + "service: some-service\n"
                    + "setup:\n"
                    + "  - cmd1: c\n"
                    + "  - cmd2:\n"
                    + "      param1: value1\n"
                    + "      param2: value2\n"
                    + "run:\n"
                    + "  - run-cmd:\n"
                    + "      jo: ne\n"
                    + "  - cmd3:\n"
                    + "      param3: 3\n"
                    + "      param4:\n"
                    + "      group1:\n"
                    + "        sub1: true\n"
                    + "        sub2:\n"
                    + "        sub3: 3.14159\n"
                    + "      group2.sub4:\n"
                    + "      group2.sub5: \"GNU's Not Unix!\"\n";

    @Test
    public void testLoad() {

        ConfigParser parser = YamlConfigParserBuilder.buildDefault();
        ConfigNode.ObjectNode node = parser.parse((StringContent) () -> COMPLEX_YAML);

        assertThat(node.entrySet(), hasSize(5));
    }

    @Test
    public void testEmpty() {
        ConfigParser parser = YamlConfigParserBuilder.buildDefault();
        ConfigNode.ObjectNode node = parser.parse((StringContent) () -> "");

        assertThat(node.entrySet(), hasSize(0));
    }

    @Test
    public void testSingleValue() {
        ConfigParser parser = YamlConfigParserBuilder.buildDefault();
        ConfigNode.ObjectNode node = parser.parse((StringContent) () -> "aaa: bbb");

        assertThat(node.entrySet(), hasSize(1));
        assertThat(node.get("aaa"), valueNode("bbb"));
    }

    @Test
    public void testStringListValue() {
        ConfigParser parser = YamlConfigParserBuilder.buildDefault();
        ConfigNode.ObjectNode node = parser.parse((StringContent) () -> "aaa:\n"
                + "  - bbb\n"
                + "  - ccc\n"
                + "  - ddd\n");

        assertThat(node.entrySet(), hasSize(1));

        List<ConfigNode> aaa = ((ConfigNode.ListNode) node.get("aaa"));
        assertThat(aaa, hasSize(3));
        assertThat(aaa.get(0), valueNode("bbb"));
        assertThat(aaa.get(1), valueNode("ccc"));
        assertThat(aaa.get(2), valueNode("ddd"));
    }

    //
    // helper
    //

    @FunctionalInterface
    private interface StringContent extends ConfigParser.Content {
        @Override
        default String getMediaType() {
            return YamlConfigParser.MEDIA_TYPE_APPLICATION_YAML;
        }

        @Override
        default Reader asReadable() {
            return new StringReader(getContent());
        }

        String getContent();
    }

}
