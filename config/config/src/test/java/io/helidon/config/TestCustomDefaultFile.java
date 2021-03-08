/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class TestCustomDefaultFile {
    @Test
    void testFileLoaded() {
        Config config = Config.builder()
                .addParser(new YmlParser())
                .build();

        ConfigValue<Boolean> configValue = config.get("it-works").asBoolean();

        assertThat(configValue.asOptional(), not(Optional.empty()));
        assertThat(configValue.get(), is(true));
    }

    private static class YmlParser implements ConfigParser {
        private static final Pattern PROPERTY_PATTERN = Pattern.compile("(.*?): (.*)");

        @Override
        public Set<String> supportedMediaTypes() {
            return Set.of("application/x-yaml");
        }

        @Override
        public ConfigNode.ObjectNode parse(Content content) throws ConfigParserException {
            List<String> lines = new LinkedList<>();

            try(BufferedReader br = new BufferedReader(new InputStreamReader(content.data()))) {
                String line;

                while ((line = br.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    lines.add(line);
                }
            } catch (IOException e) {
                throw new ConfigParserException("Failed to read content", e);
            }

            //it-works: true
            // a very stupid way to do this, but this is just a test

            ConfigNode.ObjectNode.Builder rootNodeBuilder = ConfigNode.ObjectNode.builder();
            for (String line : lines) {
                Matcher matcher = PROPERTY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    rootNodeBuilder.addValue(matcher.group(1), matcher.group(2));
                }
            }
            return rootNodeBuilder.build();
        }

        @Override
        public List<String> supportedSuffixes() {
            return List.of("yml", "yaml");
        }
    }
}
