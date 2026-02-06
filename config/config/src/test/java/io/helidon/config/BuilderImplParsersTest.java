/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParser.Content;
import io.helidon.config.spi.ConfigParserException;

import org.junit.jupiter.api.Test;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_HOCON;
import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON;
import static io.helidon.common.media.type.MediaTypes.APPLICATION_X_YAML;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests part of {@link BuilderImpl} related to {@link ConfigParser}s.
 */
public class BuilderImplParsersTest {

    private static final MediaType TEST_MEDIA_TYPE = MediaTypes.create("my/media/type");
    private final Executor changesExecutor = Executors.newSingleThreadExecutor(new ConfigThreadFactory("unit-tests"));

    @Test
    public void testServicesDisabled() {
        List<ConfigParser> parsers = new BuilderImpl().buildParsers(false, List.of());

        assertThat(parsers, hasSize(0));
    }

    @Test
    public void testBuiltInParserLoaded() {
        List<ConfigParser> parsers = new BuilderImpl().buildParsers(true, List.of());

        assertThat(parsers, hasSize(1));
        assertThat(parsers.get(0), instanceOf(PropertiesConfigParser.class));
    }

    @Test
    public void testUserDefinedHasPrecedence() {
        List<ConfigParser> parsers = new BuilderImpl().buildParsers(true, List.of(new MyConfigParser()));

        assertThat(parsers, hasSize(2));
        assertThat(parsers.get(0), instanceOf(MyConfigParser.class));
        assertThat(parsers.get(1), instanceOf(PropertiesConfigParser.class));
    }

    @Test
    public void testContextFindParserEmpty() {
        BuilderImpl.ConfigContextImpl context = new BuilderImpl.ConfigContextImpl(changesExecutor, List.of());

        assertThat(context.findParser(MediaTypes.create("_WHATEVER_/_WHATEVER_")), is(Optional.empty()));
    }

    @Test
    public void testContextFindParserNotAvailable() {
        Content content = mock(Content.class);
        when(content.mediaType()).thenReturn(Optional.of(TEST_MEDIA_TYPE));

        BuilderImpl.ConfigContextImpl context = new BuilderImpl.ConfigContextImpl(changesExecutor, List.of(
                mockParser(APPLICATION_HOCON, APPLICATION_JSON),
                mockParser(),
                mockParser(APPLICATION_X_YAML)
        ));

        assertThat(context.findParser(content.mediaType().get()), is(Optional.empty()));
    }

    @Test
    public void testContextFindParserFindFirst() {
        Content content = mock(Content.class);
        when(content.mediaType()).thenReturn(Optional.of(TEST_MEDIA_TYPE));

        ConfigParser firstParser = mockParser(TEST_MEDIA_TYPE);

        BuilderImpl.ConfigContextImpl context = new BuilderImpl.ConfigContextImpl(changesExecutor, List.of(
                mockParser(APPLICATION_HOCON, APPLICATION_JSON),
                firstParser,
                mockParser(TEST_MEDIA_TYPE),
                mockParser(APPLICATION_X_YAML)
        ));

        assertThat(context.findParser(content.mediaType().get()).get(), is(firstParser));
    }

    private ConfigParser mockParser(MediaType... supportedMediaTypes) {
        ConfigParser parser = mock(ConfigParser.class);
        when(parser.supportedMediaTypes()).thenReturn(Set.of(supportedMediaTypes));

        return parser;
    }

    //
    // MyConfigParser
    //

    private static class MyConfigParser implements ConfigParser {

        @Override
        public Set<MediaType> supportedMediaTypes() {
            return Set.of();
        }

        @Override
        public ObjectNode parse(Content content) throws ConfigParserException {
            return ObjectNode.empty();
        }
    }

}
