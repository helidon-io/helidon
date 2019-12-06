/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.spi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.helidon.config.ClasspathConfigSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.DirectoryConfigSource;
import io.helidon.config.FileConfigSource;
import io.helidon.config.MetaConfig;
import io.helidon.config.UrlConfigSource;
import io.helidon.config.internal.MapConfigSource;
import io.helidon.config.internal.PrefixedConfigSource;
import io.helidon.config.spi.ConfigNode.ObjectNode;
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

        ConfigSource source = metaConfig.as(MetaConfig::configSource).get();

        assertThat(source, is(instanceOf(AbstractMpSource.class)));

        Config config = justFrom(source);

        assertThat(config.get(TEST_SYS_PROP_NAME).asString().get(), is(TEST_SYS_PROP_VALUE));
    }

    @Test
    public void testEnvironmentVariables() {
        Config metaConfig = justFrom(ConfigSources.create(
                ObjectNode.builder()
                        .addValue("type", "environment-variables")
                        .build()));

        ConfigSource source = metaConfig.as(MetaConfig::configSource).get();

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

        ConfigSource source = metaConfig.as(MetaConfig::configSource).get();

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

        ConfigSource source = metaConfig.as(MetaConfig::configSource).get();

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

        ConfigSource source = metaConfig.as(MetaConfig::configSource).get();

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

            ConfigSource source = metaConfig.as(MetaConfig::configSource).get();

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

        ConfigSource source = metaConfig.as(MetaConfig::configSource).get();

        assertThat(source, is(instanceOf(PrefixedConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("this.is.prefix.key.app.page-size").asInt().get(), is(10));
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

    /**
     * Testing implementation of config source endpoint.
     */
    public static class MyEndpoint {
        private final String myProp1;
        private final int myProp2;

        public MyEndpoint(String myProp1, int myProp2) {
            this.myProp1 = myProp1;
            this.myProp2 = myProp2;
        }

        public String getMyProp1() {
            return myProp1;
        }

        public int getMyProp2() {
            return myProp2;
        }

        @Override
        public String toString() {
            return "MyEndpoint{"
                    + "myProp1='" + myProp1 + '\''
                    + ", myProp2=" + myProp2
                    + '}';
        }
    }

}
