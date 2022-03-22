/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.config.hocon;

import java.nio.file.Paths;

import io.helidon.config.ClasspathConfigSource;
import io.helidon.config.Config;
import io.helidon.config.FileConfigSource;
import io.helidon.config.spi.ConfigParserException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncludeTest {

    private static final String RELATIVE_PATH_TO_RESOURCE = "./src/test/resources/";

    @Test
    void testClasspathIncludes() {
        Config config = Config.create(ClasspathConfigSource.create("conf/application.conf"));

        String value = config.get("app.greeting").asString().orElse(null);

        assertThat("app.greeting should be loaded from application.conf", value, notNullValue());
        assertThat(value, is("Hello"));

        value = config.get("server.host").asString().orElse(null);

        assertThat("server.host should be loaded from included.conf", value, notNullValue());
        assertThat(value, is("localhost"));
    }

    @Test
    void testFilesIncludes() {
        Config config = Config.create(FileConfigSource.builder()
                                              .path(Paths.get(RELATIVE_PATH_TO_RESOURCE + "conf/application2.conf")));

        String value = config.get("app.greeting").asString().orElse(null);

        assertThat("app.greeting should be loaded from application2.conf", value, notNullValue());
        assertThat(value, is("Hello"));

        value = config.get("server.host").asString().orElse(null);

        assertThat("server.host should be loaded from sub/included.conf", value, notNullValue());
        assertThat(value, is("127.0.0.1"));
    }

    @Test
    void testIncludesWithRequiredIncludeNotPresent() {
        ConfigParserException cpe = assertThrows(ConfigParserException.class, () ->
                Config.create(FileConfigSource.builder()
                                      .path(Paths.get(RELATIVE_PATH_TO_RESOURCE + "conf/application3.conf"))));
        assertThat(cpe.getMessage(), is("bogus.conf is missing"));
    }

}
