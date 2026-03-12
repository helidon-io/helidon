/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.config.metadata.model.CmModel;
import io.helidon.config.metadata.model.CmModel.CmAllowedValue;
import io.helidon.config.metadata.model.CmModel.CmEnum;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;
import io.helidon.config.metadata.model.CmNode;
import io.helidon.config.metadata.model.CmResolver;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;

import static io.helidon.config.metadata.docs.CmDocNames.shortTypeName;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Config docs generator.
 *
 * @see #process()
 */
class CmDocCodegen {
    private static final System.Logger LOGGER = System.getLogger(CmDocCodegen.class.getName());

    private final CmResolver resolver;
    private final Path outputDir;
    private final Template rootTemplate;
    private final Template configTemplate;
    private final Template enumTemplate;
    private final Template providerTemplate;
    private final Template manifestTemplate;
    private final MessageDigest digest;
    private final Map<String, String> ids = new HashMap<>(); // ids cache (key: type#optionKey:optionType)
    private final Set<String> allIds = new HashSet<>(); // all unique ids

    /**
     * Create a new instance.
     *
     * @param outputDir output directory
     * @param metadata  config metadata
     */
    CmDocCodegen(Path outputDir, CmModel metadata) {
        this.outputDir = outputDir;
        this.resolver = CmResolver.create(metadata);
        this.digest = initDigest();
        var loader = new ClassPathTemplateLoader("/io/helidon/config/metadata/docs");
        var handlebars = new Handlebars(loader);
        rootTemplate = template(handlebars, "config_reference.adoc");
        configTemplate = template(handlebars, "config.adoc");
        enumTemplate = template(handlebars, "enum.adoc");
        providerTemplate = template(handlebars, "provider.adoc");
        manifestTemplate = template(handlebars, "manifest.adoc");
    }

    /**
     * Process the config metadata and generate the corresponding documentation.
     */
    void process() {

        // generate README.adoc
        generateFile("config_reference.adoc", rootTemplate, rootContext());

        var fileNames = new HashSet<String>();

        // document all types
        for (var type : resolver.types()) {
            var typeName = type.type();
            var fileName = fileName(typeName);
            generateFile(fileName, configTemplate, configContext(type));
            fileNames.add(fileName);
        }

        // document all provider contracts
        for (var typeName : resolver.contracts()) {
            var fileName = fileName(typeName);
            generateFile(fileName, providerTemplate, providerContext(typeName));
            fileNames.add(fileName);
        }

        // document enum types
        for (var type : resolver.enums()) {
            var typeName = type.type();
            var fileName = fileName(typeName);
            generateFile(fileName, enumTemplate, enumContext(type));
            fileNames.add(fileName);
        }

        // generate listing
        generateFile("manifest.adoc", manifestTemplate, manifestContext());

        // remove obsolete files
        try (Stream<Path> stream = Files.list(outputDir)
                .filter(it -> {
                    var fileName = it.getFileName().toString();
                    if (!Files.isDirectory(it)
                        && fileName.endsWith(".adoc")
                        && !fileName.equals("config_reference.adoc")
                        && !fileName.equals("manifest.adoc")) {

                        return !fileNames.contains(fileName);
                    }
                    return false;
                })) {

            var toRemove = stream.map(Path::toAbsolutePath).toList();
            for (var file : toRemove) {
                LOGGER.log(Level.INFO, "Removing obsolete file: {0}", file);
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Object> configContext(CmType type) {
        var context = new HashMap<String, Object>();
        var typeName = type.type();
        context.put("description", typeDescription(type));
        context.put("type", typeName);
        context.put("usages", resolver.usage(typeName).stream()
                .map(this::usageContext)
                .toList());
        context.put("options", optionsContext(type, o -> !o.deprecated() && !o.experimental()));
        context.put("experimentalOptions", optionsContext(type, o -> !o.deprecated() && o.experimental()));
        context.put("deprecatedOptions", optionsContext(type, CmOption::deprecated));
        return context;
    }

    private Map<String, Object> enumContext(CmEnum type) {
        var context = new HashMap<String, Object>();
        var typeName = type.type();
        context.put("type", typeName);
        context.put("usages", resolver.usage(typeName).stream()
                .map(this::usageContext)
                .toList());
        context.put("values", type.values().stream()
                .map(this::allowedValueContext)
                .toList());
        return context;
    }

    private Map<String, Object> optionsContext(CmType enclosingType, Predicate<CmOption> predicate) {
        var context = new HashMap<String, Object>();
        var options = enclosingType.options().stream()
                .filter(predicate)
                .toList();
        context.put("options", options.stream().sorted()
                .map(option -> optionContext(enclosingType, option))
                .toList());
        context.put("defaultValues", options.stream()
                .anyMatch(it -> it.defaultValue().isPresent()));
        return context;
    }

    private Map<String, Object> optionContext(CmType enclosingType, CmOption option) {
        var context = new HashMap<String, Object>();
        var key = option.key().orElseThrow();
        var description = option.description().orElse(null);
        if (description == null || description.isBlank()) {
            LOGGER.log(Level.WARNING, "Option does not have a description: {0}", key);
            description = "<code>N/A</code>";
        }
        context.put("id", id(enclosingType.type(), key, option.type()));
        context.put("key", key);
        context.put("type", optionTypeContext(option));
        context.put("description", description);
        option.defaultValue().ifPresent(it -> context.put("defaultValue", it));
        return context;
    }

    private Map<String, Object> optionTypeContext(CmOption option) {
        var context = new HashMap<String, Object>();
        var optionTypeName = option.type();
        boolean resolved = resolver.type(optionTypeName).isPresent();
        if ((option.provider() && !optionTypeName.equals(CmOption.DEFAULT_TYPE))
            || resolved
            || resolver.isEnum(optionTypeName)) {
            context.put("fileName", fileName(optionTypeName));
        }
        context.put("shortName", shortTypeName(optionTypeName));
        context.put("fullName", optionTypeName);
        context.put("kind", option.kind().name());
        return context;
    }

    private Map<String, Object> typeContext(String refName, CmType type) {
        var context = new HashMap<String, Object>();
        var typeName = type.type();
        var prefix = type.prefix().orElseThrow(() ->
                new IllegalStateException("Type does not have a prefix: " + typeName));
        context.put("id", id(refName, prefix, typeName));
        context.put("prefix", prefix);
        context.put("fileName", fileName(typeName));
        context.put("shortName", shortTypeName(typeName));
        context.put("description", typeDescription(type));
        return context;
    }

    private Map<String, Object> allowedValueContext(CmAllowedValue allowedValue) {
        var context = new HashMap<String, Object>();
        context.put("value", allowedValue.value());
        context.put("description", allowedValue.description());
        return context;
    }

    private Map<String, Object> usageContext(CmNode node) {
        var context = new HashMap<String, Object>();
        var refName = node.parent()
                .map(CmNode::typeName)
                .orElse("config_reference");

        context.put("fileName", fileName(refName));
        context.put("id", id(refName, node.key(), node.typeName()));
        context.put("path", node.path());
        return context;
    }

    private Map<String, Object> providerContext(String typeName) {
        var context = new HashMap<String, Object>();
        context.put("type", typeName);
        context.put("implementations", resolver.providers(typeName).stream()
                .sorted()
                .map(it -> typeContext(typeName, it))
                .toList());
        context.put("usages", resolver.usage(typeName).stream()
                .map(this::usageContext)
                .toList());
        return context;
    }

    private Map<String, Object> rootContext() {
        var context = new HashMap<String, Object>();
        context.put("roots", resolver.roots().stream()
                .flatMap(it -> it.type().stream())
                .sorted((o1, o2) -> {
                    var p1 = o1.prefix().orElseThrow();
                    var p2 = o2.prefix().orElseThrow();
                    var i1 = p1.indexOf('.') >= 0;
                    var i2 = p2.indexOf('.') >= 0;
                    if (i1 != i2) {
                        return i1 ? 1 : -1; // dotted prefix go last
                    }
                    int c = p1.compareTo(p2);
                    return c == 0 ? o1.type().compareTo(o2.type()) : c;
                })
                .map(it -> typeContext("config_reference", it))
                .toList());
        return context;
    }

    private Map<String, Object> manifestContext() {
        var context = new HashMap<String, Object>();
        context.put("configTypes", resolver.types().stream()
                .map(CmType::type)
                .map(this::fileContext)
                .toList());
        context.put("providerTypes", resolver.contracts().stream()
                .map(this::fileContext)
                .toList());
        context.put("enumTypes", resolver.enums().stream()
                .map(CmEnum::type)
                .map(this::fileContext)
                .toList());
        return context;
    }

    private Map<String, Object> fileContext(String typeName) {
        var context = new HashMap<String, Object>();
        context.put("fileName", fileName(typeName));
        context.put("typeName", typeName);
        return context;
    }

    private String typeDescription(CmType type) {
        var description = type.description().orElse(null);
        if (description == null || description.isBlank()) {
            LOGGER.log(Level.WARNING, "Type does not have a description: {0}", type.type());
            description = "<code>N/A</code>";
        }
        return description;
    }

    private String id(String typeName, String optionKey, String optionType) {
        var key = typeName + "#" + optionKey + ":" + optionType;
        var existing = ids.get(key);
        if (existing != null) {
            return existing;
        }

        var reverseTypeName = new StringBuilder(typeName).reverse().toString();
        var reverseOptionTypeName = new StringBuilder(optionType).reverse().toString();
        var input = reverseTypeName + "#" + optionKey + ":" + reverseOptionTypeName;
        byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        var hash = HexFormat.of().formatHex(bytes);

        // sanitize for anchor
        var suffix = optionKey.replaceAll("[^A-Za-z0-9_-]", "-");

        // start with 5 characters and increase
        for (int i = 5; i <= hash.length(); i++) {
            // anchors follow XML id rules
            // always start with 'a'
            var prefix = "a" + hash.substring(0, i);
            if (allIds.add(prefix)) {
                var id = prefix + "-" + suffix;
                ids.put(key, id);
                return id;
            }
        }

        throw new IllegalStateException(
                "Could not generate unique id, type: %s, option: %s"
                        .formatted(typeName, optionKey));
    }

    private void generateFile(String fileName, Template template, Map<String, Object> context) {
        try {
            LOGGER.log(Level.INFO, "Generating " + fileName);
            var outputFile = outputDir.resolve(fileName);
            var outputFileParent = outputFile.getParent();
            if (outputFileParent != null) {
                Files.createDirectories(outputFileParent);
            }
            try (var writer = Files.newBufferedWriter(outputFile, TRUNCATE_EXISTING, CREATE)) {
                template.apply(context, writer);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate: " + fileName, ex);
        }
    }

    private static Template template(Handlebars handlebars, String template) {
        try {
            return handlebars.compile(template);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + template, e);
        }
    }

    private static String fileName(String typeName) {
        return typeName.replace('.', '_') + ".adoc";
    }

    private static MessageDigest initDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
