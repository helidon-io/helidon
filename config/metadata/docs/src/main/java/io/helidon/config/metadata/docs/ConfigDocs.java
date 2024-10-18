/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.URLTemplateSource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.eclipse.yasson.YassonConfig;

/**
 * Entry point to generate config documentation for Helidon Config reference.
 * <p>
 * This module can either be used through {@link io.helidon.config.metadata.docs.Main}, or via this class.
 *
 * @see #create(java.nio.file.Path)
 * @see #process()
 */
public class ConfigDocs {
    private static final System.Logger LOGGER = System.getLogger(ConfigDocs.class.getName());
    private static final String CONFIG_REFERENCE_ADOC = "config_reference.adoc";
    private static final String METADATA_JSON_LOCATION = "META-INF/helidon/config-metadata.json";
    private static final String RELATIVE_PATH_ADOC = "{rootdir}/config/";
    private static final Pattern MODULE_PATTERN = Pattern.compile("(.*?)(\\.spi)?\\.([a-zA-Z0-9]*?)");
    private static final Pattern COPYRIGHT_LINE_PATTERN = Pattern.compile(".*Copyright \\(c\\) (.*) Oracle and/or its "
                                                                                  + "affiliates.");
    private static final Jsonb JSON_B = JsonbBuilder.create(new YassonConfig().withFailOnUnknownProperties(true));
    private static final Map<String, String> TYPE_MAPPING;

    static {
        Map<String, String> typeMapping = new HashMap<>();
        typeMapping.put("java.lang.String", "string");
        typeMapping.put("java.lang.Integer", "int");
        typeMapping.put("java.lang.Boolean", "boolean");
        typeMapping.put("java.lang.Long", "long");
        typeMapping.put("java.lang.Character", "char");
        typeMapping.put("java.lang.Float", "float");
        typeMapping.put("java.lang.Double", "double");
        TYPE_MAPPING = Map.copyOf(typeMapping);
    }

    private final Path path;

    private ConfigDocs(Path path) {
        this.path = path;
    }

    /**
     * Create a new instance that will update config reference documentation in the {code targetPath}.
     *
     * @param targetPath path of the config reference documentation, must contain the {@value #CONFIG_REFERENCE_ADOC}
     *                   file, or be empty
     * @return new instance of config documentation to call {@link #process()} on
     */
    public static ConfigDocs create(Path targetPath) {
        return new ConfigDocs(targetPath);
    }

    static String titleFromFileName(String fileName) {
        String title = fileName;
        // string .adoc
        if (title.endsWith(".adoc")) {
            title = title.substring(0, title.length() - 5);
        }
        if (title.startsWith("io_helidon_")) {
            title = title.substring("io_helidon_".length());
            int i = title.lastIndexOf('_');
            if (i != -1) {
                String simpleName = title.substring(i + 1);
                String thePackage = title.substring(0, i);
                title = simpleName + " (" + thePackage.replace('_', '.') + ")";
            }
        }
        return title;
    }

    // translate HTML to asciidoc
    static String translateHtml(String text) {
        String result = text;
        // <p>
        result = result.replaceAll("\n\\s*<p>", "\n");
        result = result.replaceAll("\\s*<p>", "\n");
        result = result.replaceAll("</p>", "");
        // <ul><nl><li>
        result = result.replaceAll("\\s*</li>\\s*", "");
        result = result.replaceAll("\\s*</ul>\\s*", "\n\n");
        result = result.replaceAll("\\s*</nl>\\s*", "\n\n");
        result = result.replaceAll("\n\\s*<ul>\\s*", "\n");
        result = result.replaceAll("\\s*<ul>\\s*", "\n");
        result = result.replaceAll("\n\\s*<nl>\\s*", "\n");
        result = result.replaceAll("\\s*<nl>\\s*", "\n");
        result = result.replaceAll("<li>\\s*", "\n- ");
        result = result.replaceAll("\n<br>", "\n");
        result = result.replaceAll("<br>\n", "\n");
        result = result.replaceAll("<br>", "\n");
        // also fix javadoc issues
        // {@value}
        result = result.replaceAll("\\{@value\\s+#?(.*?)}", "`$1`");
        // {@link}
        result = result.replaceAll("\\{@link\\s+#?(.*?)}", "`$1`");
        // escaped end of lines
        result = result.replaceAll("\\n", "\n");
        // <b>
        result = replace(result, "<b>", "</b>", "*", "*");
        // <i>
        result = replace(result, "<i>", "</i>", "_", "_");
        // <a href="">...</a>
        result = replaceLinks(result);
        // <pre>....</pre>
        result = replacePre(result);
        // tables
        result = handleTables(result);
        // <h4>, <h5>
        result = replace(result, "<h4>", "</h4>", "\n[.underline]#", "#\n");
        result = replace(result, "<h5>", "</h5>", "\n[.underline]#", "#\n");
        // <sup>, <sub>
        result = replace(result, "<sup>", "</sup>", "^", "^");
        result = replace(result, "<sub>", "</sub>", "~", "~");

        // end of lines followed by a single space (multiple are probably intended)
        result = result.replaceAll("\n ", "\n");

        return result;
    }

    /**
     * Process the {@code META-INF/helidon/config-metadata.json} files from all dependencies of Helidon, and generate
     * config reference documentation for them.
     * <p>
     * The documentation is updated, including copyright years
     */
    public void process() {
        Path configReference = path.resolve(ConfigDocs.CONFIG_REFERENCE_ADOC);
        try {
            checkTargetPath(configReference);
        } catch (IOException e) {
            throw new ConfigDocsException("Failed to check if target path exists and is valid", e);
        }

        Handlebars handlebars = new Handlebars();
        Template typeTemplate = template(handlebars, "type-docs.adoc.hbs");
        Template configReferenceTemplate = template(handlebars, "config_reference.adoc.hbs");

        Enumeration<URL> files;
        try {
            files = ConfigDocs.class.getClassLoader().getResources(METADATA_JSON_LOCATION);
        } catch (IOException e) {
            throw new ConfigDocsException("Failed to load " + METADATA_JSON_LOCATION + " files from classpath", e);
        }

        List<CmModule> allModules = new LinkedList<>();

        while (files.hasMoreElements()) {
            URL url = files.nextElement();
            try {
                try (InputStream is = url.openStream()) {
                    CmModule[] cmModules = JSON_B.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), CmModule[].class);
                    allModules.addAll(Arrays.asList(cmModules));
                }
            } catch (IOException e) {
                LOGGER.log(Level.ERROR, "Failed to process metadata JSON file in: " + url, e);
            }
        }

        // map of annotated types to documentation
        Map<String, CmType> configuredTypes = new HashMap<>();

        for (CmModule module : allModules) {
            for (CmType type : module.getTypes()) {
                configuredTypes.put(type.getAnnotatedType(), type);
            }
        }

        // translate HTML in description
        translateHtml(configuredTypes);
        // add all inherited options to each type
        resolveInheritance(configuredTypes);
        // add all options from merged types as direct options to each type
        resolveMerges(configuredTypes);
        // resolve type reference (for javadocs)
        resolveTypeReference(configuredTypes);
        // add titles (remove io.helidon from package or similar)
        addTitle(configuredTypes);

        List<String> generatedFiles = new LinkedList<>();
        for (CmModule module : allModules) {
            moduleDocs(configuredTypes, typeTemplate, path, module, generatedFiles);
        }

        // sort alphabetically by page title
        generatedFiles.sort(Comparator.comparing(ConfigDocs::titleFromFileName));

        generateConfigReference(configReference, configReferenceTemplate, generatedFiles);

        // and now report obsolete files
        // filter out generated files
        try (Stream<Path> x = Files.list(path)
                .filter(it -> it.getFileName().toString().endsWith(".adoc"))
                .filter(it -> !it.getFileName().toString().equals(CONFIG_REFERENCE_ADOC))
                .filter(it -> !generatedFiles.contains(String.valueOf(it.getFileName())))) {
            x.forEach(it -> LOGGER.log(Level.WARNING, "File " + it.toAbsolutePath()
                    + " should be deleted, as its config metadata no longer exists"));
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Failed to discover obsolete files in " + path.toAbsolutePath(), e);
        }
    }

    private static String replacePre(String result) {
        // pre - replace with code block
        StringBuilder theBuilder = new StringBuilder();
        int lastIndex = 0;
        while (true) {
            int index = result.indexOf("<pre>", lastIndex);
            if (index == -1) {
                // add the rest of the string
                theBuilder.append(result.substring(lastIndex));
                break;
            }
            int endIndex = result.indexOf("</pre>", index);
            theBuilder.append(result, lastIndex, index);
            theBuilder.append("\n----\n");
            theBuilder.append(result, index + 5, endIndex);
            theBuilder.append("\n----\n");
            lastIndex = endIndex + 6;
        }
        return theBuilder.toString();
    }

    private static String handleTables(String result) {
        // table - keep as is, just a pass-through
        StringBuilder theBuilder = new StringBuilder();
        int lastIndex = 0;
        while (true) {
            int index = result.indexOf("<table", lastIndex);
            if (index == -1) {
                // add the rest of the string
                theBuilder.append(result.substring(lastIndex));
                break;
            }
            int endIndex = result.indexOf("</table>", index);
            theBuilder.append(result, lastIndex, index);
            theBuilder.append("\n++++\n");
            theBuilder.append(result, index, endIndex + 8);
            theBuilder.append("\n++++\n");
            lastIndex = endIndex + 8;
        }
        return theBuilder.toString();
    }

    private static String replaceLinks(String result) {
        //https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm[API Signing Key's fingerprint]
        Pattern pattern = Pattern.compile("<a.*?href=\"(.*?)\">(.*?)</a>", Pattern.DOTALL);
        return pattern.matcher(result).replaceAll(it -> it.group(1)
                + "[" + it.group(2) + "]");
    }

    // replaces beginning and ending tags with a string, and removed end of lines in the text within
    private static String replace(String source, String start, String end, String newStart, String newEnd) {
        Pattern pattern = Pattern.compile(start + "(\\s*)(.*?)(\\s*)" + end, Pattern.DOTALL);
        return pattern.matcher(source)
                .replaceAll(it -> it.group(1)
                        + newStart
                        + it.group(2).replace('\n', ' ')
                        + newEnd
                        + it.group(3));
    }

    private static String title(String typeName) {
        String title = typeName;
        if (title.startsWith("io.helidon.")) {
            title = title.substring("io.helidon.".length());
            int i = title.lastIndexOf('.');
            if (i != -1) {
                String simpleName = title.substring(i + 1);
                String thePackage = title.substring(0, i);
                title = simpleName + " (" + thePackage + ")";
            }
        }
        return title;
    }

    private static void moduleDocs(Map<String, CmType> configuredTypes,
                                   Template template,
                                   Path modulePath,
                                   CmModule module,
                                   List<String> generatedFiles) {
        Function<String, Boolean> exists = type -> {
            // 1: check if part of this processing
            if (configuredTypes.containsKey(type)) {
                return true;
            }
            // 2: check if exists in target directory
            String path = type.replace('.', '_') + ".adoc";
            return Files.exists(modulePath.resolve(path));
        };
        LOGGER.log(Level.INFO, "Documenting module " + module.getModule());
        // each type will have its own, such as:
        // docs/io.helidon.common.configurable/LruCache.adoc
        for (CmType type : module.getTypes()) {
            try {
                generateType(generatedFiles, configuredTypes, template, modulePath, type, exists);
            } catch (IOException e) {
                LOGGER.log(Level.ERROR, "Failed to generate docs for annotated type: " + type.getAnnotatedType(), e);
            }
        }
    }

    private static void generateType(List<String> generatedFiles,
                                     Map<String, CmType> configuredTypes,
                                     Template template,
                                     Path modulePath,
                                     CmType type,
                                     Function<String, Boolean> exists) throws IOException {
        sortOptions(type);

        String fileName = fileName(type.getType());
        Path typePath = modulePath.resolve(fileName);

        boolean sameContent = false;
        if (Files.exists(typePath)) {
            // check if maybe the file content is not modified
            CharSequence current = typeFile(configuredTypes,
                                            template,
                                            type,
                                            exists,
                                            currentCopyrightYears(typePath));
            if (sameContent(typePath, current)) {
                sameContent = true;
            }
        }

        CharSequence fileContent = typeFile(configuredTypes,
                                            template,
                                            type,
                                            exists,
                                            newCopyrightYears(typePath));

        generatedFiles.add(fileName);
        if (!sameContent) {
            // Write the target type
            Files.writeString(typePath,
                              fileContent,
                              StandardOpenOption.TRUNCATE_EXISTING,
                              StandardOpenOption.CREATE);
        }

        if (!type.getAnnotatedType().startsWith(type.getType())) {
            // generate two docs, just to make sure we do not have a conflict
            // example: Zipkin and Jaeger generate target type io.opentracing.Tracer, yet we need separate documents
            fileName = fileName(type.getAnnotatedType());
            Path annotatedTypePath = modulePath.resolve(fileName);
            generatedFiles.add(fileName);
            if (!sameContent) {
                // Write the annotated type (needed for Jaeger & Zipkin that produce the same target)
                Files.writeString(annotatedTypePath,
                                  fileContent,
                                  StandardOpenOption.TRUNCATE_EXISTING,
                                  StandardOpenOption.CREATE);
            }
        }
    }

    private static boolean sameContent(Path path, CharSequence current) {
        try {
            return Files.readString(path).equals(current.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String fileName(String typeName) {
        return typeName.replace('.', '_') + ".adoc";
    }

    private static void sortOptions(CmType type) {
        List<CmOption> options = new ArrayList<>(type.getOptions());
        options.sort(Comparator.comparing(CmOption::getKey));
        type.setOptions(options);
    }

    private static CharSequence configReferenceFile(Template template,
                                                    List<String> generatedFiles,
                                                    String copyrightYears) {
        List<CmReference> references = new ArrayList<>();
        for (String generatedFile : generatedFiles) {
            references.add(new CmReference(generatedFile,
                                           titleFromFileName(generatedFile)));
        }
        Map<String, Object> context = Map.of("year", copyrightYears,
                                             "data", references);
        try {
            return template.apply(context);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CharSequence typeFile(Map<String, CmType> configuredTypes,
                                         Template template,
                                         CmType type,
                                         Function<String, Boolean> exists,
                                         String copyrightYears) throws IOException {
        boolean hasRequired = false;
        boolean hasOptional = false;
        for (CmOption option : type.getOptions()) {
            if (option.isRequired()) {
                hasRequired = true;
            } else {
                hasOptional = true;
            }
            option.setRefType(mapType(configuredTypes, option, exists));
        }

        Map<String, Object> context = Map.of("year", copyrightYears,
                                             "hasRequired", hasRequired,
                                             "hasOptional", hasOptional,
                                             "type", type);
        return template.apply(context);
    }

    private static String newCopyrightYears(Path path) {
        String currentYear = String.valueOf(ZonedDateTime.now().getYear());

        if (Files.exists(path)) {
            // get current copyright year
            String copyrightYears = currentCopyrightYears(path);
            if (copyrightYears == null) {
                return currentYear;
            }
            if (copyrightYears.endsWith(currentYear)) {
                return copyrightYears;
            }
            int index = copyrightYears.indexOf(',');
            if (index == -1) {
                return copyrightYears + ", " + currentYear;
            }
            return copyrightYears.substring(0, index) + ", " + currentYear;
        }
        return currentYear;
    }

    private static String currentCopyrightYears(Path path) {
        try (var lines = Files.lines(path)) {
            return lines.flatMap(line -> {
                        Matcher matcher = COPYRIGHT_LINE_PATTERN.matcher(line);
                        if (matcher.matches()) {
                            return Stream.of(matcher.group(1));
                        }
                        return Stream.empty();
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not discover existing copyright year for " + path.toAbsolutePath(), e);
            return null;
        }
    }

    private static String mapType(Map<String, CmType> configuredTypes,
                                  CmOption option,
                                  Function<String, Boolean> exists) {
        String type = option.getType();
        String mapped = TYPE_MAPPING.get(type);
        CmOption.Kind kind = option.getKind();

        String displayType = displayType(kind, mapped == null ? type : mapped);

        if (mapped == null) {
            if (option.getAllowedValues() != null && !option.getAllowedValues().isEmpty()) {
                return mapAllowedValues(option, displayType);
            }
            if (option.isProvider()) {
                String providerType = option.getProviderType();
                providerType = (providerType == null) ? type : providerType;
                StringBuilder typeString = new StringBuilder(byKind(kind, type));
                typeString.append(" (service provider interface)");

                // let's try to locate available service implementations on classpath
                List<CmType> providers = findProviders(configuredTypes, providerType);
                if (!providers.isEmpty()) {
                    typeString.append("\n\nSuch as:\n\n");
                    for (CmType provider : providers) {
                        String linkText = displayType(CmOption.Kind.VALUE, provider.getType());
                        if (provider.getPrefix() != null) {
                            linkText = provider.getPrefix() + " (" + linkText + ")";
                        }
                        typeString.append(" - ")
                                .append(toLink(provider.getType(), linkText, exists));
                        typeString.append("\n");
                    }
                    typeString.append("\n");
                }
                return typeString.toString();
            }
            return toLink(type, displayType, exists);
        }
        return displayType;
    }

    private static String toLink(String type, String displayType, Function<String, Boolean> exists) {
        if (type.startsWith("io.helidon")) {
            if (type.equals("io.helidon.config.Config") || type.equals("io.helidon.common.config.Config")) {
                return "Map&lt;string, string&gt; (documented for specific cases)";
            }
            // make sure the file exists
            if (exists.apply(type)) {
                return "xref:" + ConfigDocs.RELATIVE_PATH_ADOC + type.replace('.', '_') + ".adoc[" + displayType + "]";
            }
        }
        return displayType;
    }

    private static List<CmType> findProviders(Map<String, CmType> configuredTypes, String providerInterface) {
        return configuredTypes.values()
                .stream()
                .filter(it -> it.getProvides() != null)
                .filter(it -> it.getProvides().contains(providerInterface))
                .toList();
    }

    private static String displayType(CmOption.Kind kind, String type) {
        int lastIndex = type.lastIndexOf('.');
        if (lastIndex == -1) {
            return byKind(kind, type);
        }
        String name = type.substring(lastIndex + 1);
        if ("Builder".equals(name)) {
            String base = type.substring(0, lastIndex);
            lastIndex = base.lastIndexOf('.');
            if (lastIndex == -1) {
                // this is a pure Builder class, need to show package to distinguish
                return byKind(kind, type);
            } else {
                return byKind(kind, base.substring(lastIndex + 1) + ".Builder");
            }
        } else {
            return byKind(kind, name);
        }
    }

    private static String byKind(CmOption.Kind kind, String type) {
        // no dots
        return switch (kind) {
            case LIST -> type + "[&#93;";
            case MAP -> "Map&lt;string, " + type + "&gt;";
            default -> type;
        };
    }

    private static String mapAllowedValues(CmOption option, String displayType) {
        List<CmAllowedValue> values = option.getAllowedValues();

        return displayType
                + " (" + values.stream().map(CmAllowedValue::getValue).collect(Collectors.joining(", ")) + ")";
    }

    // if the target path exists, it must either contain zero files, or the config reference
    private void checkTargetPath(Path configReference) throws IOException {
        // contains config reference
        if (Files.exists(configReference) && Files.isRegularFile(configReference)) {
            return;
        }
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            return;
        }
        if (Files.isDirectory(path)) {
            // must be empty
            try (Stream<Path> stream = Files.list(path)) {
                if (stream.findAny()
                        .isPresent()) {

                    throw new ConfigDocsException(
                            "Cannot generate config reference documentation, unless target path contains "
                            + CONFIG_REFERENCE_ADOC + " file or it is empty. "
                            + "Target path: " + path.toAbsolutePath() + " contains files");
                }
            }
        } else {
            throw new IllegalArgumentException("Target path must be a directory: "
                                               + path.toAbsolutePath().normalize());
        }
    }

    private Template template(Handlebars handlebars, String template) {
        URL resource = ConfigDocs.class.getResource(template);
        if (resource == null) {
            throw new ConfigDocsException("Failed to locate required handlebars template on classpath: " + template);
        }
        try {
            return handlebars.compile(new URLTemplateSource(template, resource));
        } catch (IOException e) {
            throw new ConfigDocsException("Failed to load handlebars template on classpath: " + template, e);
        }
    }

    private void generateConfigReference(Path configReference, Template template, List<String> generatedFiles) {
        if (Files.exists(configReference)) {
            // if content not modified, do not update copyright
            CharSequence current = configReferenceFile(template,
                                                       generatedFiles,
                                                       currentCopyrightYears(configReference));
            if (sameContent(configReference, current)) {
                return;
            }
        }

        CharSequence fileContent = configReferenceFile(template,
                                                       generatedFiles,
                                                       newCopyrightYears(configReference));

        try {
            LOGGER.log(Level.INFO, "Updating " + configReference.toAbsolutePath());
            Files.writeString(configReference,
                              fileContent,
                              StandardOpenOption.TRUNCATE_EXISTING,
                              StandardOpenOption.CREATE);
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Failed to update " + configReference.toAbsolutePath(), e);
        }
    }

    private void addTitle(Map<String, CmType> configuredTypes) {
        for (CmType value : configuredTypes.values()) {
            value.setTitle(title(value.getType()));
        }
    }

    private void resolveTypeReference(Map<String, CmType> configuredTypes) {
        for (CmType value : configuredTypes.values()) {
            value.setTypeReference(resolveTypeReference(value.getType()));
        }
    }

    private String resolveTypeReference(String type) {
        if (type.startsWith("io.helidon")) {
            // our type
            return resolveModuleFromType(type);
        } else {
            // no reference
            return type;
        }
    }

    private String resolveModuleFromType(String type) {
        Matcher m = MODULE_PATTERN.matcher(type);
        if (m.matches()) {
            String moduleName = m.group(1);
            return "link:{javadoc-base-url}/" + moduleName + "/" + toJavadocLink(type) + "[" + type + "]";
        }
        return type;
    }

    private String toJavadocLink(String type) {
        return type.replace('.', '/') + ".html";
    }

    private void translateHtml(Map<String, CmType> configuredTypes) {
        for (CmType value : configuredTypes.values()) {
            value.getOptions().forEach(this::translateHtml);
        }
    }

    private void translateHtml(CmOption option) {
        String description = option.getDescription();
        description = addAllowedValues(description, option);
        description = translateHtml(description);
        option.setDescription(description);
    }

    private String addAllowedValues(String description, CmOption option) {
        List<CmAllowedValue> allowedValues = option.getAllowedValues();
        if (allowedValues == null || allowedValues.isEmpty()) {
            // no allowed values
            return description;
        }
        if (allowedValues.stream()
                .allMatch(it -> it.getDescription() == null || it.getDescription().isBlank())) {
            // allowed values, but no description (we should eventually add javadoc link, if we can figure out
            // how to locate the URL for it
            return description;
        }
        StringBuilder sb = new StringBuilder("\n\nAllowed values:\n\n");
        for (CmAllowedValue allowedValue : allowedValues) {
            sb.append("- `")
                    .append(allowedValue.getValue())
                    .append("`: ")
                    .append(allowedValue.getDescription())
                    .append('\n');

        }
        return description + sb;
    }

    private void resolveMerges(Map<String, CmType> configuredTypes) {
        List<CmType> remaining = new ArrayList<>(configuredTypes.values());
        Map<String, CmType> resolved = new HashMap<>();
        boolean shouldExit = false;
        while (!shouldExit) {
            shouldExit = true;

            for (int i = 0; i < remaining.size(); i++) {
                CmType next = remaining.get(i);
                boolean isResolved = true;
                List<CmOption> options = next.getOptions();
                for (int j = 0; j < options.size(); j++) {
                    CmOption option = options.get(j);
                    String optionType = option.getType();
                    if (option.isMerge()) {
                        // primitives and strings are always resolved
                        if (!(TYPE_MAPPING.containsKey(optionType) || TYPE_MAPPING.containsValue(optionType))) {
                            isResolved = false;
                            if (resolved.containsKey(optionType)) {
                                options.remove(j);
                                options.addAll(resolved.get(optionType).getOptions());
                                shouldExit = false;
                                break;
                            }
                        }
                    }
                }

                if (isResolved) {
                    resolved.put(next.getType(), next);
                    remaining.remove(i);
                    shouldExit = false;
                    break;
                }
            }
        }

        if (!remaining.isEmpty()) {
            LOGGER.log(Level.WARNING, "There are types with merged type that is not on classpath: ");
            for (CmType cmType : remaining) {
                for (CmOption option : cmType.getOptions()) {
                    if (option.isMerge()) {
                        LOGGER.log(Level.WARNING, "    Option " + option.getKey() + ", merges: " + option.getType() + " in "
                                + cmType.getAnnotatedType());
                    }
                }

            }
        }
    }

    private void resolveInheritance(Map<String, CmType> configuredTypes) {
        Map<String, CmType> resolved = new HashMap<>();
        List<CmType> remaining = new ArrayList<>(configuredTypes.values());

        boolean didResolve = true;

        while (didResolve) {
            didResolve = false;
            for (int i = 0; i < remaining.size(); i++) {
                CmType next = remaining.get(i);
                if (next.getInherits() == null) {
                    resolved.put(next.getAnnotatedType(), next);
                    didResolve = true;
                    remaining.remove(i);
                    break;
                } else {
                    boolean allExist = true;
                    for (String inherit : next.getInherits()) {
                        if (!resolved.containsKey(inherit)) {
                            allExist = false;
                            break;
                        }
                    }
                    if (allExist) {
                        resolveInheritance(resolved, next);
                        resolved.put(next.getType(), next);
                        didResolve = true;
                        remaining.remove(i);
                        break;
                    }
                }
            }
        }

        if (!remaining.isEmpty()) {
            System.err.println("There are types with inheritance that is not on classpath: ");
            for (CmType cmType : remaining) {
                System.err.println("Type " + cmType.getType() + ", inherits: " + cmType.getInherits());
            }
        }
    }

    private void resolveInheritance(Map<String, CmType> resolved, CmType next) {
        // Allow option info on subclasses or implementations of interfaces to override option info from higher.
        Map<String, CmOption> options = new HashMap<>();

        List<String> inherits = next.getInherits();
        // Traverse from higher to lower in the inheritance structure so more specific settings take precedence.
        ListIterator<String> inheritsIt = inherits.listIterator(inherits.size());
        while (inheritsIt.hasPrevious()) {
            resolved.get(inheritsIt.previous())
                    .getOptions()
                    .forEach(inheritedOption -> options.put(inheritedOption.getKey(), inheritedOption));
        }
        // Now apply options from the type being processed.
        next.getOptions().forEach(opt -> options.put(opt.getKey(), opt));
        next.setOptions(new ArrayList<>(options.values()));
        next.setInherits(null);
    }
}
