/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;
import io.helidon.config.test.infra.TemporaryFolderExt;

import com.xebialabs.restito.server.StubServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Action.stringContent;
import static com.xebialabs.restito.semantics.Condition.get;
import static org.glassfish.grizzly.http.util.HttpStatus.OK_200;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.iterableWithSize;

/**
 * Tests meta configuration of config sources.
 */
public class ConfigSourceMetaConfigTest {

    private static final String TEST_SYS_PROP_NAME = "this_is_my_property-ConfigSourceConfigMapperTest";
    private static final String TEST_SYS_PROP_VALUE = "This Is My SYS PROPS Value.";
    private static final String TEST_ENV_VAR_NAME = "CONFIG_SOURCE_TEST_PROPERTY";
    private static final String TEST_ENV_VAR_VALUE = "This Is My ENV VARS Value.";
    private static final String RELATIVE_PATH_TO_RESOURCE = "/src/test/resources/";

    @RegisterExtension
    public TemporaryFolderExt folder = TemporaryFolderExt.build();

    @Test
    @ExtendWith(RestoreSystemPropertiesExt.class)
    public void testSystemProperties() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config metaConfig = justFrom(ConfigSources.create(
                ObjectNode.builder()
                        .addValue("type", "system-properties")
                        .build()));
        
        ConfigSource source = singleSource(metaConfig);
        
        assertThat(source, is(instanceOf(AbstractConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get(TEST_SYS_PROP_NAME).asString().get(), is(TEST_SYS_PROP_VALUE));
    }

    @Test
    public void testEnvironmentVariables() {
        Config metaConfig = justFrom(ConfigSources.create(
                ObjectNode.builder()
                        .addValue("type", "environment-variables")
                        .build()));

        ConfigSource source = singleSource(metaConfig);

        assertThat(source, is(instanceOf(MapConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get(TEST_ENV_VAR_NAME).asString().get(), is(TEST_ENV_VAR_VALUE));
    }

    @Test
    public void testClasspath() {
        Config metaConfig = builderFrom(ConfigSources.create(
                ObjectNode.builder()
                        .addValue("type", "classpath")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("resource", "io/helidon/config/application.properties")
                                .build())
                        .build()))
                .build();

        ConfigSource source = singleSource(metaConfig);

        assertThat(source, is(instanceOf(ClasspathConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("app.page-size").asInt().get(), is(10));
    }

    @Test
    public void testFile() {
        Config metaConfig = builderFrom(ConfigSources.create(
                ObjectNode.builder()
                        .addValue("type", "file")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("path", getDir() + "io/helidon/config/application.properties")
                                .build())
                        .build()))
                .build();

        ConfigSource source = singleSource(metaConfig);

        assertThat(source, is(instanceOf(FileConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("app.page-size").asInt().get(), is(10));
    }

    @Test
    public void testDirectory() throws IOException {
        File folder = this.folder.newFolder();
        Files.write(Files.createFile(new File(folder, "username").toPath()), "libor".getBytes());
        Files.write(Files.createFile(new File(folder, "password").toPath()), "^ery$ecretP&ssword".getBytes());

        Config metaConfig = builderFrom(ConfigSources.create(
                ObjectNode.builder()
                        .addValue("type", "directory")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("path", folder.getAbsolutePath())
                                .build())
                        .build()))
                .build();

        ConfigSource source = singleSource(metaConfig);

        assertThat(source, is(instanceOf(DirectoryConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("username").asString().get(), is("libor"));
        assertThat(config.get("password").asString().get(), is("^ery$ecretP&ssword"));
    }

    @Test
    public void testUrl() {
        StubServer server = new StubServer().run();
        try {
            whenHttp(server)
                    .match(get("/application.properties"))
                    .then(status(OK_200),
                          stringContent("greeting = Hello"));

            Config metaConfig = builderFrom(ConfigSources.create(
                    ObjectNode.builder()
                            .addValue("type", "url")
                            .addObject("properties", ObjectNode.builder()
                                    .addValue("url", String.format("http://127.0.0.1:%d/application.properties",
                                                                   server.getPort()))
                                    .build())
                            .build()))
                    .build();

            ConfigSource source = singleSource(metaConfig);

            assertThat(source, is(instanceOf(UrlConfigSource.class)));

            Config config = justFrom(source);

            assertThat(config.get("greeting").asString().get(), is("Hello"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void testPrefixed() {
        Config metaConfig = builderFrom(ConfigSources.create(
                ObjectNode.builder()
                        .addValue("type", "prefixed")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("key", "this.is.prefix.key")
                                .addValue("type", "classpath")
                                .addObject("properties", ObjectNode.builder()
                                        .addValue("resource", "io/helidon/config/application.properties")
                                        .build())
                                .build())
                        .build()))
                .build();

        ConfigSource source = singleSource(metaConfig);

        assertThat(source, is(instanceOf(PrefixedConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("this.is.prefix.key.app.page-size").asInt().get(), is(10));
    }

    @Test
    public void testInlined() {
        Config metaConfig = builderFrom(ConfigSources.create(
                ObjectNode.builder()
                        .addValue("type", "inlined")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("key", "inlined-value")
                                .addObject("server", ObjectNode.builder()
                                        .addValue("port", "8014")
                                        .addValue("host", "localhost")
                                        .build())
                                .build())
                        .build()))
                .build();

        ConfigSource source = singleSource(metaConfig);

        Config config = justFrom(source);

        assertThat(config.get("key").asString().get(), is("inlined-value"));
        assertThat(config.get("server.port").asInt().get(), is(8014));
        assertThat(config.get("server.host").asString().get(), is("localhost"));
    }

    private ConfigSource singleSource(Config metaConfig) {
        List<ConfigSource> sources = metaConfig.as(MetaConfig::configSource).get();

        assertThat("There should be just one source loaded", sources, iterableWithSize(1));

        return sources.get(0);
    }
    
    private static String getDir() {
        return Paths.get("").toAbsolutePath() + RELATIVE_PATH_TO_RESOURCE;
    }

    static Config.Builder builderFrom(ConfigSource source) {
        return Config.builder(source)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource();
    }

    static Config justFrom(ConfigSource source) {
        return builderFrom(source)
                .build();
    }
}
