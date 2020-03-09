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

package io.helidon.config.yaml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParser.Content;

import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

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

        ConfigParser parser = YamlConfigParser.create();
        ConfigNode.ObjectNode node = parser.parse(toContent(COMPLEX_YAML));

        assertThat(node.entrySet(), hasSize(5));
    }

    @Test
    public void testEmpty() {
        ConfigParser parser = YamlConfigParser.create();
        ConfigNode.ObjectNode node = parser.parse(toContent(""));

        assertThat(node.entrySet(), hasSize(0));
    }

    @Test
    public void testSingleValue() {
        ConfigParser parser = YamlConfigParser.create();
        ConfigNode.ObjectNode node = parser.parse(toContent("aaa: bbb"));

        assertThat(node.entrySet(), hasSize(1));
        assertThat(node.get("aaa"), valueNode("bbb"));
    }

    @Test
    public void testStringListValue() {
        ConfigParser parser = YamlConfigParser.create();
        ConfigNode.ObjectNode node = parser.parse(toContent("aaa:\n"
                + "  - bbb\n"
                + "  - ccc\n"
                + "  - ddd\n"));

        assertThat(node.entrySet(), hasSize(1));

        List<ConfigNode> aaa = ((ConfigNode.ListNode) node.get("aaa"));
        assertThat(aaa, hasSize(3));
        assertThat(aaa.get(0), valueNode("bbb"));
        assertThat(aaa.get(1), valueNode("ccc"));
        assertThat(aaa.get(2), valueNode("ddd"));
    }

    private Content toContent(String yaml) {
        return Content.builder()
                .data(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)))
                .mediaType(YamlConfigParser.MEDIA_TYPE_APPLICATION_YAML)
                .build();
    }
}
