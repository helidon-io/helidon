/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Simple implementation of {@link OpenApiManager}.
 */
final class SimpleOpenApiManager implements OpenApiManager<String> {

    private static final System.Logger LOGGER = System.getLogger(SimpleOpenApiManager.class.getName());
    private static final DumperOptions JSON_DUMPER_OPTIONS = jsonDumperOptions();
    private static final DumperOptions YAML_DUMPER_OPTIONS = yamlDumperOptions();

    @Override
    public String name() {
        return "default";
    }

    @Override
    public String type() {
        return "default";
    }

    @Override
    public String load(String content) {
        return content;
    }

    @Override
    public String format(String content, OpenApiFormat format) {
        return switch (format) {
            case UNSUPPORTED, YAML -> toYaml(content);
            case JSON -> toJson(content);
        };
    }

    private String toYaml(String rawData) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Converting OpenAPI document in YAML format");
        }
        Yaml yaml = new Yaml(YAML_DUMPER_OPTIONS);
        Object loadedData = yaml.load(rawData);
        return yaml.dump(loadedData);
    }

    private String toJson(String data) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Converting OpenAPI document in JSON format");
        }
        Representer representer = new Representer(JSON_DUMPER_OPTIONS) {

            @Override
            protected Node representScalar(Tag tag, String value, DumperOptions.ScalarStyle style) {
                if (tag.equals(Tag.BINARY)) {
                    // base64 string
                    return super.representScalar(Tag.STR, value, DumperOptions.ScalarStyle.DOUBLE_QUOTED);
                }
                if (tag.equals(Tag.BOOL)
                    || tag.equals(Tag.FLOAT)
                    || tag.equals(Tag.INT)) {
                    return super.representScalar(tag, value, DumperOptions.ScalarStyle.PLAIN);
                }
                return super.representScalar(tag, value, style);
            }
        };
        Yaml yaml = new Yaml(representer, JSON_DUMPER_OPTIONS);
        Object loadedData = yaml.load(data);
        return yaml.dump(loadedData);
    }

    private static DumperOptions yamlDumperOptions() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setIndent(2);
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return dumperOptions;
    }

    private static DumperOptions jsonDumperOptions() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);
        dumperOptions.setSplitLines(false);
        return dumperOptions;
    }
}
