/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import io.helidon.microprofile.graphql.server.AbstractGraphQLTest;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for {@link JandexUtils}.
 */
class JandexUtilsTest extends AbstractGraphQLTest {

    @BeforeEach
    public void resetProperties() {
        System.clearProperty(JandexUtils.PROP_INDEX_FILE);
    }

    @Test
    public void testDefaultIndexFile() {
        JandexUtils utils = new JandexUtils();
        assertThat(utils.getIndexFile(), is(JandexUtils.DEFAULT_INDEX_FILE));
        assertThat(utils.hasIndex(), is(false));
    }

    @Test
    public void testCustomIndexFile() {
        System.setProperty(JandexUtils.PROP_INDEX_FILE, "my-index-file");
        JandexUtils utils = new JandexUtils();
        assertThat(utils.getIndexFile(), is("my-index-file"));
    }

    @Test
    public void testLoadingCustomIndexFile() throws IOException, ClassNotFoundException {
        String indexFile = getTempIndexFile();
        try {
            System.setProperty(JandexUtils.PROP_INDEX_FILE, indexFile);
            createManualIndex(indexFile,
                              "java/lang/String.class",
                              "java/lang/Double.class",
                              "java/lang/Integer.class");
            JandexUtils utils = new JandexUtils();
            utils.loadIndex();

            assertThat(utils.hasIndex(), is(true));
            Index index = utils.getIndex();
            Arrays.stream(new Class<?>[] { String.class, Double.class, Integer.class })
                    .forEach(c -> {
                        ClassInfo classByName = index.getClassByName(DotName.createSimple(c.getName()));
                        assertThat(classByName, is(notNullValue()));
                        assertThat(classByName.toString(), is(c.getName()));
                    });
        } finally {
            if (indexFile != null) {
                new File(indexFile).delete();
            }
        }
    }
}