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

package io.helidon.config.examples.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonValue;

import io.helidon.config.examples.metadata.ConfiguredType.ConfiguredProperty;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Reads configuration metadata and prints a full configuration example.
 */
public final class ConfigMetadataMain {
    private static final Map<String, TypedValue> TYPED_VALUES = Map.of("java.lang.Integer",
                                                                       new TypedValue("1"),
                                                                       "java.lang.Boolean",
                                                                       new TypedValue("true"),
                                                                       "java.lang.Long",
                                                                       new TypedValue("1000"),
                                                                       "java.lang.Short",
                                                                       new TypedValue("0"),
                                                                       "java.lang.String",
                                                                       new TypedValue("value", true),
                                                                       "java.net.URI",
                                                                       new TypedValue("https://www.example.net", true),
                                                                       "java.lang.Class",
                                                                       new TypedValue("net.example.SomeClass", true),
                                                                       "java.time.ZoneId",
                                                                       new TypedValue("UTC", true));

    private ConfigMetadataMain() {
    }

    /**
     * Start the example.
     * @param args ignored
     */
    public static void main(String[] args) throws IOException {
        JsonReaderFactory readerFactory = Json.createReaderFactory(Map.of());

        Enumeration<URL> files = ConfigMetadataMain.class.getClassLoader().getResources("META-INF/helidon/config-metadata.json");
        List<ConfiguredType> configuredTypes = new LinkedList<>();
        Map<String, ConfiguredType> typesMap = new HashMap<>();

        while (files.hasMoreElements()) {
            URL url = files.nextElement();
            try (InputStream is = url.openStream()) {
                JsonReader reader = readerFactory.createReader(is, StandardCharsets.UTF_8);
                processMetadataJson(configuredTypes, typesMap, reader.readArray());
            }
        }
        for (ConfiguredType configuredType : configuredTypes) {
            if (configuredType.standalone()) {
                printType(configuredType, typesMap);
            }
        }
    }

    private static void printType(ConfiguredType configuredType, Map<String, ConfiguredType> typesMap) {
        String prefix = configuredType.prefix();
        System.out.println("# " + configuredType.description());
        System.out.println("# " + configuredType.targetClass());
        System.out.println(prefix + ":");
        printType(configuredType, typesMap, 1, false);
    }

    private static void printType(ConfiguredType configuredType,
                                  Map<String, ConfiguredType> typesMap,
                                  int nesting,
                                  boolean listStart) {
        String spaces = " ".repeat(nesting * 2);
        Set<ConfiguredProperty> properties = configuredType.properties();
        boolean isListStart = listStart;

        for (ConfiguredProperty property : properties) {
            if (property.key() != null && property.key().contains(".*.")) {
                // this is a nested key, must be resolved by the parent list node
                continue;
            }

            printProperty(property, properties, typesMap, nesting, isListStart);
            isListStart = false;
        }

        List<String> inherited = configuredType.inherited();
        for (String inheritedTypeName : inherited) {
            ConfiguredType inheritedType = typesMap.get(inheritedTypeName);
            if (inheritedType == null) {
                System.out.println(spaces + "# Missing inherited type: " + inheritedTypeName);
            } else {
                printType(inheritedType, typesMap, nesting, false);
            }
        }
    }

    private static void printProperty(ConfiguredProperty property,
                                      Set<ConfiguredProperty> properties,
                                      Map<String, ConfiguredType> typesMap,
                                      int nesting,
                                      boolean listStart) {
        String spaces = " ".repeat(nesting * 2);

        printDocs(property, spaces, listStart);

        if (property.kind() == ConfiguredOption.Kind.LIST) {
            printListProperty(property, properties, typesMap, nesting, spaces);
            return;
        }

        if (property.kind() == ConfiguredOption.Kind.MAP) {
            printMapProperty(property, typesMap, nesting, spaces);
            return;
        }

        TypedValue typed = TYPED_VALUES.get(property.type());
        if (typed == null) {
            // this is a nested type, or a missing type
            ConfiguredType nestedType = typesMap.get(property.type());
            if (nestedType == null) {
                // either we have a list of allowed values, default value, or this is really a missing type
                printAllowedValuesOrMissing(property, typesMap, nesting, spaces);
            } else {
                // proper nested type
                if (property.merge()) {
                    printType(nestedType, typesMap, nesting, false);
                } else {
                    System.out.println(spaces + property.outputKey() + ":");
                    printType(nestedType, typesMap, nesting + 1, false);
                }
            }
        } else {
            // this is a "leaf" node
            if (property.defaultValue() == null) {
                System.out.println(spaces + "# Generated value (property does not have a configured default)");
            }
            System.out.println(spaces + property.outputKey() + ": " + toTypedValue(property, typed));
        }
    }

    private static void printMapProperty(ConfiguredProperty property,
                                         Map<String, ConfiguredType> typesMap,
                                         int nesting,
                                         String spaces) {
        System.out.print(spaces);
        System.out.println(property.outputKey() + ":");
        TypedValue typedValue = TYPED_VALUES.get(property.type());

        String mySpaces = " ".repeat((nesting + 1) * 2);
        if (typedValue == null) {
            System.out.println(mySpaces + "key: \"Unsupported map value type: " + property.type() + "\"");
        } else {
            System.out.println(mySpaces + "key-1: " + output(typedValue, typedValue.defaultsDefault()));
            System.out.println(mySpaces + "key-2: " + output(typedValue, typedValue.defaultsDefault()));
        }
    }

    private static void printListProperty(ConfiguredProperty property,
                                          Set<ConfiguredProperty> properties,
                                          Map<String, ConfiguredType> typesMap,
                                          int nesting,
                                          String spaces) {
        System.out.print(spaces);
        System.out.print(property.outputKey() + ":");
        ConfiguredType listType = typesMap.get(property.type());

        if (listType == null) {
            if (property.provider()) {
                listFromProvider(property, typesMap, nesting, spaces);
            } else {
                listFromTypes(property, properties, typesMap, nesting, spaces);
            }
        } else {
            System.out.println();
            System.out.print(spaces + "- ");
            printType(listType, typesMap, nesting + 1, true);
        }
    }

    private static void listFromProvider(ConfiguredProperty property,
                                         Map<String, ConfiguredType> typesMap,
                                         int nesting,
                                         String spaces) {
        // let's find all supported providers
        List<ConfiguredType> providers = new LinkedList<>();
        for (ConfiguredType value : typesMap.values()) {
            if (value.provides().contains(property.type())) {
                providers.add(value);
            }
        }

        System.out.println();
        if (providers.isEmpty()) {
            System.out.print(spaces + "- # There are no modules on classpath providing " + property.type());
            return;
        }
        for (ConfiguredType provider : providers) {
            System.out.print(spaces + "- ");
            if (provider.prefix() != null) {
                System.out.println("# " + provider.description());
                System.out.println(spaces + "  " + provider.prefix() + ":");
                printType(provider, typesMap, nesting + 2, false);
            } else {
                printType(provider, typesMap, nesting + 1, true);
            }
        }
    }

    private static void fromProvider(ConfiguredProperty property,
                                     Map<String, ConfiguredType> typesMap,
                                     int nesting) {
        String spaces = " ".repeat(nesting + 1);
        // let's find all supported providers
        List<ConfiguredType> providers = new LinkedList<>();
        for (ConfiguredType value : typesMap.values()) {
            if (value.provides().contains(property.type())) {
                providers.add(value);
            }
        }

        if (providers.isEmpty()) {
            System.out.println(spaces + " # There are no modules on classpath providing " + property.type());
            return;
        }

        for (ConfiguredType provider : providers) {
            System.out.println(spaces + "   # ****** Provider Configuration ******");
            System.out.println(spaces + "   # " + provider.description());
            System.out.println(spaces + "   # " + provider.targetClass());
            System.out.println(spaces + "   # ************************************");
            if (provider.prefix() != null) {
                System.out.println(spaces + "   " + provider.prefix() + ":");
                printType(provider, typesMap, nesting + 2, false);
            } else {
                printType(provider, typesMap, nesting + 1, false);
            }
        }
    }

    private static void listFromTypes(ConfiguredProperty property,
                                      Set<ConfiguredProperty> properties,
                                      Map<String, ConfiguredType> typesMap,
                                      int nesting,
                                      String spaces) {
        // this may be a list defined in configuration itself (*)
        String prefix = property.outputKey() + ".*.";
        Map<String, ConfiguredProperty> children = new HashMap<>();
        for (ConfiguredProperty configuredProperty : properties) {
            if (configuredProperty.outputKey().startsWith(prefix)) {
                children.put(configuredProperty.outputKey().substring(prefix.length()), configuredProperty);
            }
        }
        if (children.isEmpty()) {
            // this may be an array of primitive types / String
            TypedValue typedValue = TYPED_VALUES.get(property.type());
            if (typedValue == null) {
                List<ConfiguredType.AllowedValue> allowedValues = property.allowedValues();
                if (allowedValues.isEmpty()) {
                    System.out.println();
                    System.out.println(spaces + "# Missing type: " + property.type());
                } else {
                    System.out.println();
                    typedValue = new TypedValue("", true);
                    for (ConfiguredType.AllowedValue allowedValue : allowedValues) {
                        // # Description
                        // # This is the default value
                        // actual value
                        System.out.print(spaces + "  - ");
                        String nextLinePrefix = spaces + "    ";
                        boolean firstLine = true;

                        if (allowedValue.description() != null && !allowedValue.description().isBlank()) {
                            firstLine = false;
                            System.out.println("#" + allowedValue.description());
                        }
                        if (allowedValue.value().equals(property.defaultValue())) {
                            if (firstLine) {
                                firstLine = false;
                            } else {
                                System.out.print(nextLinePrefix);
                            }
                            System.out.println("# This is the default value");
                        }
                        if (!firstLine) {
                            System.out.print(nextLinePrefix);
                        }
                        System.out.println(output(typedValue, allowedValue.value()));
                    }
                }
            } else {
                printArray(typedValue);
            }
        } else {
            System.out.println();
            System.out.print(spaces + "- ");
            boolean listStart = true;
            for (var entry : children.entrySet()) {
                ConfiguredProperty element = entry.getValue();
                // we must modify the key
                element.key(entry.getKey());
                printProperty(element, properties, typesMap, nesting + 1, listStart);
                listStart = false;
            }
        }
    }

    private static void printDocs(ConfiguredProperty property, String spaces, boolean firstLineNoSpaces) {
        String description = property.description();
        description = (description == null || description.isBlank()) ? null : description;

        // type
        System.out.print((firstLineNoSpaces ? "" : spaces));
        System.out.print("# ");
        System.out.println(property.type());

        // description
        if (description != null) {
            description = description.replace('\n', ' ');
            System.out.print(spaces);
            System.out.print("# ");
            System.out.println(description);
        }

        // required
        if (!property.optional()) {
            System.out.print(spaces);
            System.out.println("# *********** REQUIRED ***********");
        }
    }

    private static void printArray(TypedValue typedValue) {
        String element = output(typedValue, typedValue.defaultsDefault());
        String toPrint = " [" + element + "," + element + "]";
        System.out.println(toPrint);
    }

    private static String output(TypedValue typed, String value) {
        if (typed.escaped()) {
            return "\"" + value + "\"";
        }
        return value;
    }

    private static void printAllowedValuesOrMissing(ConfiguredProperty property,
                                                    Map<String, ConfiguredType> typesMap,
                                                    int nesting, String spaces) {
        if (property.provider()) {
            System.out.println(spaces + property.outputKey() + ":");
            fromProvider(property, typesMap, nesting);
            return;
        }

        List<ConfiguredType.AllowedValue> allowedValues = property.allowedValues();
        if (allowedValues.isEmpty()) {
            if (property.defaultValue() == null) {
                System.out.println(spaces + property.outputKey() + ": \"Missing nested type: " + property.type() + "\"");
            } else {
                System.out.println(spaces + property.outputKey() + ": " + toTypedValue(property,
                                                                                       new TypedValue(property.defaultValue(),
                                                                                                      true)));
            }
        } else {
            List<String> values = allowedValues.stream()
                    .map(ConfiguredType.AllowedValue::value)
                    .collect(Collectors.toList());
            for (ConfiguredType.AllowedValue allowedValue : allowedValues) {
                System.out.println(spaces + "# " + allowedValue.value() + ": " + allowedValue.description()
                        .replace("\n", " "));
            }
            if (property.defaultValue() == null) {
                System.out.println(spaces + property.outputKey() + ": \"One of: " + values + "\"");
            } else {
                System.out.println(spaces + property.outputKey() + ": \"" + property.defaultValue() + "\"");
            }
        }
    }

    private static String toTypedValue(ConfiguredProperty property,
                                       TypedValue typed) {
        String value = property.defaultValue();

        if (value == null) {
            value = typed.defaultsDefault;
        }

        return output(typed, value);
    }

    private static void processMetadataJson(List<ConfiguredType> configuredTypes,
                                            Map<String, ConfiguredType> typesMap,
                                            JsonArray jsonArray) {
        for (JsonValue jsonValue : jsonArray) {
            processTypeArray(configuredTypes, typesMap, jsonValue.asJsonObject().getJsonArray("types"));
        }
    }

    private static void processTypeArray(List<ConfiguredType> configuredTypes,
                                         Map<String, ConfiguredType> typesMap,
                                         JsonArray jsonArray) {
        if (jsonArray == null) {
            return;
        }
        for (JsonValue jsonValue : jsonArray) {
            JsonObject type = jsonValue.asJsonObject();
            ConfiguredType configuredType = ConfiguredType.create(type);
            configuredTypes.add(configuredType);
            typesMap.put(configuredType.targetClass(), configuredType);
        }
    }

    private static final class TypedValue {
        private final String defaultsDefault;
        private final boolean escaped;

        private TypedValue(String defaultsDefault) {
            this(defaultsDefault, false);
        }

        private TypedValue(String defaultsDefault, boolean escaped) {
            this.defaultsDefault = defaultsDefault;
            this.escaped = escaped;
        }

        String defaultsDefault() {
            return defaultsDefault;
        }

        boolean escaped() {
            return escaped;
        }
    }
}
