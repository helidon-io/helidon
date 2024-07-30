/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tools;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import io.helidon.common.processor.GeneratedAnnotationHandler;
import io.helidon.common.types.TypeName;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.InjectionServicesConfig;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.TagType;
import com.github.jknack.handlebars.Template;

/**
 * Helper tools for dealing with Injection-related Handlebar templates.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class TemplateHelper {
    /**
     * The tag that us used to represent the template name to use.
     */
    public static final String TAG_TEMPLATE_NAME = "io.helidon.inject.template.name";

    /**
     * The default template name to use.
     */
    public static final String DEFAULT_TEMPLATE_NAME = "default";

    private static final System.Logger LOGGER = System.getLogger(TemplateHelper.class.getName());

    private final String versionId;

    private TemplateHelper(InjectionServicesConfig cfg) {
        Objects.requireNonNull(cfg.providerName(), "provider name is required");
        this.versionId = cfg.providerVersion().orElseThrow(() -> new IllegalStateException("provider version is required"));
    }

    /**
     * Creates a template helper utility using the global bootstrap configuration.
     *
     * @return the template helper initialized with the bootstrap configuration
     */
    public static TemplateHelper create() {
        InjectionServicesConfig cfg = InjectionServices.injectionServices()
                .orElseThrow(() -> new ToolsException("Injection services not found")).config();
        return new TemplateHelper(cfg);
    }

    /**
     * Produces the generated sticker annotation attribute contents.
     *
     * @param generatorClassTypeName the generator class type name
     * @param triggerClassType class that caused the type to be generated
     * @param generatedType generated type
     * @return the generated sticker
     */
    public String generatedStickerFor(TypeName generatorClassTypeName, TypeName triggerClassType, TypeName generatedType) {
        return GeneratedAnnotationHandler.createString(generatorClassTypeName,
                                                       triggerClassType,
                                                       generatedType,
                                                       versionId,
                                                       "");
    }

    /**
     * Apply substitutions.
     *
     * @param target    the target string to find substitutions for
     * @param props     the replacements
     * @param logErr    flag indicating whether logger should be written for errors and warnings
     *
     * @return the new string, fully resolved with substitutions
     */
    public String applySubstitutions(CharSequence target,
                                     Map<String, Object> props,
                                     boolean logErr) {
        Set<String> missingArgs = new LinkedHashSet<>();
        try {
            return applySubstitutions(target.toString(), props, logErr, true, missingArgs, null, null);
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
    public Set<String> requiredArguments(String target) {
        return requiredArguments(target, Optional.empty(), Optional.empty());
    }

    /**
     * Load a template by its fileName using the {@link #DEFAULT_TEMPLATE_NAME}.
     *
     * @param name the file name
     *
     * @return the template file, without substitutions applied
     */
    String safeLoadTemplate(String name) {
        return safeLoadTemplate(DEFAULT_TEMPLATE_NAME, name);
    }

    /**
     * Load a template by name.
     *
     * @param templateName  the template profile name (e.g., "default")
     * @param name          the file name
     *
     * @return the template file, without substitutions applied
     */
    String safeLoadTemplate(String templateName,
                            String name) {
        return Objects.requireNonNull(loadTemplate(templateName, name), "failed to load: " + toFQN(templateName, name));
    }

    /**
     * Same as {@link #safeLoadTemplate(String, String)} but will return null if the template name is not found.
     *
     * @param templateName  the template profile/directory
     * @param name          the template name to use
     * @return the template, or null if not found
     */
    public String loadTemplate(String templateName,
                               String name) {
        return CommonUtils.loadStringFromResource(toFQN(templateName, name));
    }

    /**
     * Determine the arguments needed for template evaluation.
     *
     * @param target        the target template
     * @param delimStart    provides support for custom delimiters
     * @param delimEnd      provides support for custom delimiters
     * @return the set of attributes that are required for substitution
     */
    Set<String> requiredArguments(String target,
                                  Optional<String> delimStart,
                                  Optional<String> delimEnd) {
        try {
            Handlebars handlebars = new Handlebars();
            delimStart.ifPresent(handlebars::setStartDelimiter);
            delimEnd.ifPresent(handlebars::setEndDelimiter);
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

    private static String toFQN(String templateName,
                                String name) {
        return "templates/inject/" + templateName + "/" + name;
    }

    private static String applySubstitutions(String target,
                                             Map<String, Object> props,
                                             boolean logErr,
                                             boolean throwOnMissingArgs,
                                             Set<String> missingArgs,
                                             String delimStart,
                                             String delimEnd) throws IOException {
        if (target == null) {
            return null;
        }

        Handlebars handlebars = new Handlebars();
        Template template = handlebars.compileInline(target);

        if (delimStart != null) {
            handlebars.setStartDelimiter(delimStart);
        }
        if (delimEnd != null) {
            handlebars.setEndDelimiter(delimEnd);
        }

        target = template.apply(props);

        if (!missingArgs.isEmpty()) {
            String err = "Unsatisfied substitution of {{...}}: " + missingArgs;
            if (logErr) {
                LOGGER.log(System.Logger.Level.WARNING, err);
            }
            if (throwOnMissingArgs) {
                throw new IOException(err);
            }
        }

        return target;
    }

}
