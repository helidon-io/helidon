/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.media.jackson.common;

import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.spi.MediaSupportProvider;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * Jackson support SPI provider.
 */
public class JacksonProvider implements MediaSupportProvider {

    private static final String JACKSON = "jackson";

    @Override
    public MediaSupport create(Config config) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());
        configureJackson(objectMapper, config);
        return JacksonSupport.create(objectMapper);
    }

    private void configureJackson(ObjectMapper objectMapper, Config property) {
        Stream.of(DeserializationFeature.values())
                .forEach(df -> property.get(configName(df.name())).asBoolean()
                        .ifPresent(val -> objectMapper.configure(df, val)));
        Stream.of(SerializationFeature.values())
                .forEach(sf -> property.get(configName(sf.name())).asBoolean()
                        .ifPresent(val -> objectMapper.configure(sf, val)));
        Stream.of(JsonParser.Feature.values())
                .forEach(jp -> property.get(configName(jp.name())).asBoolean()
                        .ifPresent(val -> objectMapper.configure(jp, val)));
        Stream.of(MapperFeature.values())
                .forEach(mf -> property.get(configName(mf.name())).asBoolean()
                        .ifPresent(val -> objectMapper.configure(mf, val)));
        Stream.of(JsonGenerator.Feature.values())
                .forEach(jgf -> property.get(configName(jgf.name())).asBoolean()
                        .ifPresent(val -> objectMapper.configure(jgf, val)));
    }

    private String configName(String enumName) {
        return enumName.toLowerCase()
                .replace('_', '-');
    }

    @Override
    public String configKey() {
        return JACKSON;
    }
}
