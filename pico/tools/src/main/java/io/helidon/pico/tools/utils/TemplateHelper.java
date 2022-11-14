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

package io.helidon.pico.tools.utils;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.spi.impl.DefaultPicoServicesConfig;
import io.helidon.pico.tools.ToolsException;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.TagType;
import com.github.jknack.handlebars.Template;

/**
 * Helper tools for dealing with templates.
 */
public class TemplateHelper {

    /**
     * The tag that us used to represent the template name to use.
     */
    public static final String TAG_TEMPLATE_NAME = PicoServicesConfig.FQN + ".template.name";

    /**
     * The default template name to use.
     */
    public static final String DEFAULT_TEMPLATE_NAME = "default";

    static final System.Logger LOGGER = System.getLogger(TemplateHelper.class.getName());

    private TemplateHelper() {
    }

    /**
     * The default generated sticker annotation contents.
     *
     * @param generatorClassTypeName the generator class type name
     * @return the generated sticker
     */
    public static final String getDefaultGeneratedSticker(String generatorClassTypeName) {
        String[] generatedSticker = new String[] {
                "provider=" + DefaultPicoServicesConfig.PROVIDER,
                "generator=" + generatorClassTypeName,
                "ver=" + DefaultPicoServicesConfig.VERSION};

        StringBuilder result = new StringBuilder();
        for (String s : generatedSticker) {
            if (0 == result.length()) {
                result.append("{");
            } else {
                result.append(", ");
            }
            result.append("\"").append(s).append("\"");
        }
        if (result.length() > 0) {
            result.append("}");
        }
        return result.toString();
    }

    /**
     * Load a template by its fileName using the {@link #DEFAULT_TEMPLATE_NAME}.
     *
     * @param name the file name
     *
     * @return the non-null template file, without substitutions applied
     */
    public static String safeLoadTemplate(String name) {
        return safeLoadTemplate(DEFAULT_TEMPLATE_NAME, name);
    }

    /**
     * Load a template by name.
     *
     * @param templateName the template profile name (e.g., "default")
     * @param name the file name
     *
     * @return the non-null template
     */
    public static String safeLoadTemplate(String templateName, String name) {
        return Objects.requireNonNull(loadTemplate(templateName, name), toFQN(templateName, name) + " was not found");
    }

    /**
     * Same as {@link #safeLoadTemplate(String, String)} but will return null if the template name is not found.
     *
     * @param templateName  the template profile/directory
     * @param name          the template name to use
     * @return the template, or null if not found
     */
    public static String loadTemplate(String templateName, String name) {
        return CommonUtils.loadStringFromResource(toFQN(templateName, name));
    }

    static String toFQN(String templateName, String name) {
        return "templates/" + PicoServicesConfig.NAME + "/" + templateName + "/" + name;
    }

    /**
     * Apply substitutions.
     *
     * @param errOut the error writer, or null if no error writer should be used.
     * @param target the target string to find substitutions for.
     * @param props the replacements
     *
     * @return the new string, fully resolved
     */
    public static String applySubstitutions(PrintStream errOut, String target, Map<String, Object> props) {
        Set<String> missingArgs = new LinkedHashSet<>();
        try {
            return applySubstitutions(true, errOut, target, props, missingArgs, null, null);
        } catch (IOException e) {
            throw new ToolsException("unable to apply substitutions", e);
        }
    }

    /**
     * Determine the arguments needed for template evaluation.
     *
     * @param target the target template
     * @return the set of attributes that are required for substitution
     */
    public static Set<String> getRequiredArguments(String target) {
        return getRequiredArguments(target, null, null);
    }

    /**
     * Determine the arguments needed for template evaluation.
     *
     * @param target the target template
     * @param delimStart provides support for custom delimiters
     * @param delimEnd provides support for custom delimiters
     * @return the set of attributes that are required for substitution
     */
    public static Set<String> getRequiredArguments(String target, String delimStart, String delimEnd) {
        try {
            Handlebars handlebars = new Handlebars();
            if (Objects.nonNull(delimStart)) {
                handlebars.setStartDelimiter(delimStart);
            }
            if (Objects.nonNull(delimEnd)) {
                handlebars.setEndDelimiter(delimEnd);
            }
            Template template = handlebars.compileInline(target);
            Set<String> result = new TreeSet<>(template.collect(TagType.VAR, TagType.SECTION, TagType.STAR_VAR));
            result.addAll(template.collectReferenceParameters());
            result.remove(".");
            result.remove("each");
            result.remove("with");
            result.remove("if");
            return result;
        } catch (IOException e) {
            throw new ToolsException("unable to determine substitutions", e);
        }
    }

    private static String applySubstitutions(boolean throwOnMissingArgs,
                                             PrintStream errOut,
                                             String target,
                                             Map<String, Object> props,
                                             Set<String> missingArgs,
                                             String delimStart,
                                             String delimEnd) throws IOException {
        if (null == target) {
            return null;
        }

        Handlebars handlebars = new Handlebars();
        Template template = handlebars.compileInline(target);

        if (Objects.nonNull(delimStart)) {
            handlebars.setStartDelimiter(delimStart);
        }
        if (Objects.nonNull(delimEnd)) {
            handlebars.setEndDelimiter(delimEnd);
        }

        target = template.apply(props);

        if (!missingArgs.isEmpty()) {
            String err = "Unsatisfied substitution of {{...}}: " + missingArgs;
            if (Objects.nonNull(errOut)) {
                LOGGER.log(System.Logger.Level.WARNING, err);
            }
            if (throwOnMissingArgs) {
                throw new IOException(err);
            }
        }

        return target;
    }

}
