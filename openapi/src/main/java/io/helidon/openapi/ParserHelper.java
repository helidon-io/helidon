/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Reference;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.servers.ServerVariable;
import org.yaml.snakeyaml.TypeDescription;

/**
 * Wraps generated parser and uses {@link io.helidon.openapi.ExpandedTypeDescription} as its type.
 */
public class ParserHelper {

    // Temporary to suppress SnakeYAML warnings.
    // As a static we keep a reference to the logger, thereby making sure any changes we make are persistent. (JUL holds
    // only weak references to loggers internally.)
    private static final java.util.logging.Logger SNAKE_YAML_INTROSPECTOR_LOGGER =
            java.util.logging.Logger.getLogger(org.yaml.snakeyaml.introspector.PropertySubstitute.class.getPackage().getName());

    /**
     * The SnakeYAMLParserHelper is generated by a maven plug-in.
     */
    private final SnakeYAMLParserHelper<ExpandedTypeDescription> generatedHelper;

    private ParserHelper(SnakeYAMLParserHelper<ExpandedTypeDescription> generatedHelper) {
        this.generatedHelper = generatedHelper;
    }

    /**
     * Create a new parser helper.
     *
     * @return a new parser helper
     */
    public static ParserHelper create() {
        boolean warningsEnabled = Boolean.getBoolean("openapi.parsing.warnings.enabled");
        if (SNAKE_YAML_INTROSPECTOR_LOGGER.isLoggable(java.util.logging.Level.WARNING) && !warningsEnabled) {
            SNAKE_YAML_INTROSPECTOR_LOGGER.setLevel(java.util.logging.Level.SEVERE);
        }
        ParserHelper helper = new ParserHelper(SnakeYAMLParserHelper.create(ExpandedTypeDescription::create));
        adjustTypeDescriptions(helper.types());
        return helper;
    }

    /**
     * Types.
     *
     * @return types of this helper
     */
    public Map<Class<?>, ExpandedTypeDescription> types() {
        return generatedHelper.types();
    }

    /**
     * Entries of this helper.
     *
     * @return entry set
     */
    public Set<Map.Entry<Class<?>, ExpandedTypeDescription>> entrySet() {
        return generatedHelper.entrySet();
    }

    private static void adjustTypeDescriptions(Map<Class<?>, ExpandedTypeDescription> types) {
        /*
         * We need to adjust the {@code TypeDescription} objects set up by the generated {@code SnakeYAMLParserHelper} class
         * because there are some OpenAPI-specific issues that the general-purpose helper generator cannot know about.
         */

        /*
         * In the OpenAPI document, HTTP methods are expressed in lower-case. But the associated Java methods on the PathItem
         * class use the HTTP method names in upper-case. So for each HTTP method, "add" a property to PathItem's type
         * description using the lower-case name but upper-case Java methods and exclude the upper-case property that
         * SnakeYAML's automatic analysis of the class already created.
         */
        ExpandedTypeDescription pathItemTD = types.get(PathItem.class);
        for (PathItem.HttpMethod m : PathItem.HttpMethod.values()) {
            pathItemTD.substituteProperty(m.name().toLowerCase(), Operation.class, getter(m), setter(m));
            pathItemTD.addExcludes(m.name());
        }

        /*
         * An OpenAPI document can contain a property named "enum" for Schema and ServerVariable, but the related Java methods
         * use "enumeration".
         */
        Set.<Class<?>>of(Schema.class, ServerVariable.class).forEach(c -> {
            ExpandedTypeDescription tdWithEnumeration = types.get(c);
            tdWithEnumeration.substituteProperty("enum", List.class, "getEnumeration", "setEnumeration");
            tdWithEnumeration.addPropertyParameters("enum", String.class);
            tdWithEnumeration.addExcludes("enumeration");
        });

        /*
         * SnakeYAML derives properties only from methods declared directly by each OpenAPI interface, not from methods defined
         *  on other interfaces which the original one extends. Those we have to handle explicitly.
         */
        for (ExpandedTypeDescription td : types.values()) {
            if (Extensible.class.isAssignableFrom(td.getType())) {
                td.addExtensions();
            }
            if (td.hasDefaultProperty()) {
                td.substituteProperty("default", Object.class, "getDefaultValue", "setDefaultValue");
                td.addExcludes("defaultValue");
            }
            if (isRef(td)) {
                td.addRef();
            }
        }
    }

    private static boolean isRef(TypeDescription td) {
        for (Class<?> c : td.getType().getInterfaces()) {
            if (c.equals(Reference.class)) {
                return true;
            }
        }
        return false;
    }

    private static String getter(PathItem.HttpMethod method) {
        return methodName("get", method);
    }

    private static String setter(PathItem.HttpMethod method) {
        return methodName("set", method);
    }

    private static String methodName(String operation, PathItem.HttpMethod method) {
        return operation + method.name();
    }
}
