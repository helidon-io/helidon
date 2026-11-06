/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.hocon.HoconConfigParser;
import io.helidon.config.spi.ConfigParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class LiteralKeysTest {
    private static final String YAML = """
            a.b: literal
            a:
              b: nested
            "object.key":
              child.key: child
            list.key:
              - first
              - second
            parent:
              nested.key: nested-child
              entries:
                - item.key: item
            "~": tilde
            "~0": tilde-zero
            "~1": tilde-one
            "x.~1.y": mixed
            "a~1b": preescaped
            """;
    private static final String HOCON = """
            "a.b" = literal
            a.b = nested
            "object.key" { "child.key" = child }
            "list.key" = [first, second]
            parent {
              "nested.key" = nested-child
              entries = [{ "item.key" = item }]
            }
            "~" = tilde
            "~0" = tilde-zero
            "~1" = tilde-one
            "x.~1.y" = mixed
            "a~1b" = preescaped
            """;

    static Stream<Arguments> literalConfigs() {
        return Stream.of(Arguments.of("YAML", config(YAML, MediaTypes.APPLICATION_X_YAML,
                                                    YamlConfigParser.builder().literalKeys(true).build())),
                         Arguments.of("HOCON", config(HOCON, MediaTypes.APPLICATION_HOCON, HoconConfigParser.create())));
    }

    static Stream<Arguments> defaultParsers() {
        Config disabled = Config.builder(ConfigSources.create(Map.of("literal-keys", "false")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        return Stream.of(Arguments.of("factory", YamlConfigParser.create()),
                         Arguments.of("constructor", new YamlConfigParser()),
                         Arguments.of("builder default", YamlConfigParser.builder().build()),
                         Arguments.of("builder false", YamlConfigParser.builder().literalKeys(false).build()),
                         Arguments.of("config false", YamlConfigParser.builder().config(disabled).build()));
    }

    @ParameterizedTest(name = "{0}: literal scalar, object and list names")
    @MethodSource("literalConfigs")
    void literalValues(String format, Config config) {
        assertThat(config.get("a~1b").asString().get(), is("literal"));
        assertThat(config.get("a.b").asString().get(), is("nested"));
        assertThat(config.get("object~1key.child~1key").asString().get(), is("child"));
        assertThat(config.get("list~1key").asList(String.class).get(), is(List.of("first", "second")));
        assertThat(config.get("parent.nested~1key").asString().get(), is("nested-child"));
        assertThat(config.get("parent.entries.0.item~1key").asString().get(), is("item"));
        assertThat(config.get("object.key").exists(), is(false));
        assertThat(config.get("list.key").exists(), is(false));
    }

    @ParameterizedTest(name = "{0}: source escapes are encoded once")
    @MethodSource("literalConfigs")
    void escapedNames(String format, Config config) {
        assertThat(config.get("~0").asString().get(), is("tilde"));
        assertThat(config.get("~00").asString().get(), is("tilde-zero"));
        assertThat(config.get("~01").asString().get(), is("tilde-one"));
        assertThat(config.get("x~1~01~1y").asString().get(), is("mixed"));
        assertThat(config.get("a~01b").asString().get(), is("preescaped"));
        assertThat(config.get("~01").name(), is("~1"));
        assertThat(config.get("x~1~01~1y").name(), is("x.~1.y"));
    }

    @ParameterizedTest(name = "{0}: navigation preserves names")
    @MethodSource("literalConfigs")
    void navigation(String format, Config config) {
        assertThat(config.get("object~1key").name(), is("object.key"));
        assertThat(config.get("object~1key").asNodeList().get().stream().map(Config::name).toList(),
                   containsInAnyOrder("child.key"));
        assertThat(config.get("parent").traverse().map(node -> node.key().toString()).toList(),
                   containsInAnyOrder("parent.nested~1key", "parent.entries", "parent.entries.0",
                                      "parent.entries.0.item~1key"));

        Config detached = config.get("object~1key").detach();
        assertThat(detached.get("child~1key").asString().get(), is("child"));
        Config copied = Config.builder(ConfigSources.create(config)).disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource().build();
        assertThat(copied.get("a~1b").asString().get(), is("literal"));
        assertThat(copied.get("parent.entries.0.item~1key").asString().get(), is("item"));
    }

    @ParameterizedTest(name = "{0}: dotted source keys remain paths")
    @MethodSource("defaultParsers")
    void defaultCompatibility(String factory, ConfigParser parser) {
        assertDefaultPaths(config("""
                quoted.key: unquoted
                "other.key": quoted
                a~1b: preescaped
                """, MediaTypes.APPLICATION_X_YAML, parser));
    }

    @Test
    void discoveredParserUsesCompatibleDefault() {
        Config config = Config.builder(ConfigSources.create("""
                quoted.key: unquoted
                "other.key": quoted
                a~1b: preescaped
                """, MediaTypes.APPLICATION_X_YAML))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        assertDefaultPaths(config);
    }

    @Test
    void componentConfigEnablesLiteralKeys() {
        Config bootstrap = Config.builder(ConfigSources.create(Map.of("yaml.literal-keys", "true")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        ConfigParser parser = YamlConfigParser.builder().config(bootstrap.get("yaml")).build();
        Config config = config("a.b: literal", MediaTypes.APPLICATION_X_YAML, parser);
        assertThat(config.get("a~1b").asString().get(), is("literal"));
        assertThat(config.get("a.b").exists(), is(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void classpathSourceUsesAttachedParser(boolean literalKeys) {
        Config config = Config.builder(ConfigSources.classpath("literal-keys.yaml")
                                               .parser(YamlConfigParser.builder().literalKeys(literalKeys).build()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .build();
        assertThat(config.get(literalKeys ? "resource~1key" : "resource.key").asString().get(), is("value"));
        assertThat(config.get(literalKeys ? "resource.key" : "resource~1key").exists(), is(false));
    }

    private static Config config(String content, MediaType mediaType, ConfigParser parser) {
        return Config.builder(ConfigSources.create(content, mediaType))
                .addParser(parser)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .build();
    }

    private static void assertDefaultPaths(Config config) {
        assertThat(config.get("quoted.key").asString().get(), is("unquoted"));
        assertThat(config.get("other.key").asString().get(), is("quoted"));
        assertThat(config.get("quoted~1key").exists(), is(false));
        assertThat(config.get("other~1key").exists(), is(false));
        assertThat(config.get("a~1b").asString().get(), is("preescaped"));
    }
}
