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

package io.helidon.config;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.internal.PropertiesConfigParser;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests part of {@link BuilderImpl} related to {@link ConfigParser}s.
 */
public class BuilderImplParsersTest {

    private static final String TEST_MEDIA_TYPE = "my/media/type";

    @Test
    public void testServicesDisabled() {
        List<ConfigParser> parsers = BuilderImpl.buildParsers(false, CollectionsHelper.listOf());

        assertThat(parsers, hasSize(0));
    }

    @Test
    public void testBuiltInParserLoaded() {
        List<ConfigParser> parsers = BuilderImpl.buildParsers(true, CollectionsHelper.listOf());

        assertThat(parsers, hasSize(1));
        assertThat(parsers.get(0), instanceOf(PropertiesConfigParser.class));
    }

    @Test
    public void testUserDefinedHasPrecedence() {
        List<ConfigParser> parsers = BuilderImpl.buildParsers(true, CollectionsHelper.listOf(new MyConfigParser()));

        assertThat(parsers, hasSize(2));
        assertThat(parsers.get(0), instanceOf(MyConfigParser.class));
        assertThat(parsers.get(1), instanceOf(PropertiesConfigParser.class));
    }

    @Test
    public void testContextFindParserEmpty() {
        BuilderImpl.ConfigContextImpl context = new BuilderImpl.ConfigContextImpl(CollectionsHelper.listOf());

        assertThat(context.findParser("_WHATEVER_"), is(Optional.empty()));
    }

    @Test
    public void testContextFindParserNotAvailable() {
        ConfigParser.Content content = mock(ConfigParser.Content.class);
        when(content.getMediaType()).thenReturn(TEST_MEDIA_TYPE);

        BuilderImpl.ConfigContextImpl context = new BuilderImpl.ConfigContextImpl(CollectionsHelper.listOf(
                mockParser("application/hocon", "application/json"),
                mockParser(),
                mockParser("application/x-yaml")
        ));

        assertThat(context.findParser(content.getMediaType()), is(Optional.empty()));
    }

    @Test
    public void testContextFindParserFindFirst() {
        ConfigParser.Content content = mock(ConfigParser.Content.class);
        when(content.getMediaType()).thenReturn(TEST_MEDIA_TYPE);

        ConfigParser firstParser = mockParser(TEST_MEDIA_TYPE);

        BuilderImpl.ConfigContextImpl context = new BuilderImpl.ConfigContextImpl(CollectionsHelper.listOf(
                mockParser("application/hocon", "application/json"),
                firstParser,
                mockParser(TEST_MEDIA_TYPE),
                mockParser("application/x-yaml")
        ));

        assertThat(context.findParser(content.getMediaType()).get(), is(firstParser));
    }

    private ConfigParser mockParser(String... supportedMediaTypes) {
        ConfigParser parser = mock(ConfigParser.class);
        when(parser.getSupportedMediaTypes()).thenReturn(CollectionsHelper.setOf(supportedMediaTypes));

        return parser;
    }

    //
    // MyConfigParser
    //

    private static class MyConfigParser implements ConfigParser {

        @Override
        public Set<String> getSupportedMediaTypes() {
            return CollectionsHelper.setOf();
        }

        @Override
        public ObjectNode parse(Content content) throws ConfigParserException {
            return ObjectNode.empty();
        }
    }

}
