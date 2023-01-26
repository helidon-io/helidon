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

package io.helidon.pico.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

/**
 * Provides the basic formation for {@code module-info.java} creation and manipulation.
 *
 * @see java.lang.module.ModuleDescriptor
 */
@Builder
public interface ModuleInfoDescriptor {

    /**
     * The tag used to represent the module name.
     */
    String TAG_MODULE_NAME = "module_name";

    /**
     * The default module name (i.e., "unnamed").
     */
    String DEFAULT_MODULE_NAME = "unnamed";

    /**
     * The default suffix on the module name for a test module. This suffix will be appended to the module name
     * of the main module.
     */
    String DEFAULT_TEST_SUFFIX = "test";

    /**
     * The base module-info name.
     */
    String MODULE_INFO_NAME = "module-info";

    /**
     * The java module-info name.
     */
    String DEFAULT_MODULE_INFO_JAVA_NAME = MODULE_INFO_NAME + ".java";

    /**
     * The resource providing the module-info template.
     */
    String SERVICE_PROVIDER_MODULE_INFO_HBS = "module-info.hbs";


    /**
     * Used to declare the preferred ordering of the items in the module-info.
     */
    enum Ordering {

        /**
         * Little or no attempt is made to preserve comments, loaded/created ordering is arranged top-down.
         */
        NATURAL,

        /**
         * Attempt is preserve comments and natural, loaded/created ordering is arranged top-down.
         */
        NATURAL_PRESERVE_COMMENTS,

        /**
         * Little or no attempt is made to preserve comments, ordering is arranged sorted by the target class or package.
         */
        SORTED

    }

    /**
     * The module name.
     *
     * @return the module name
     */
    @ConfiguredOption(DEFAULT_MODULE_NAME)
    String name();

    /**
     * Returns true if the name currently set is the same as the {@link #DEFAULT_MODULE_NAME}.
     *
     * @return true if the current name is the default name
     */
    default boolean isDefaultName() {
        return DEFAULT_MODULE_NAME.equals(name());
    }

    /**
     * The template name to apply. The default is {@link io.helidon.pico.tools.TemplateHelper#DEFAULT_TEMPLATE_NAME}.
     *
     * @return the template name
     */
    @ConfiguredOption(TemplateHelper.DEFAULT_TEMPLATE_NAME)
    String templateName();

    /**
     * The header (i.e., copyright) comment - will appear at the very start of the output.
     *
     * @return the header comment
     */
    Optional<String> headerComment();

    /**
     * The description comment - will appear directly above the module's {@link #name()}.
     *
     * @return the description comment
     */
    Optional<String> descriptionComment();

    /**
     * The ordering applied.
     *
     * @return the ordering
     */
    @ConfiguredOption("NATURAL")
    Ordering ordering();

    /**
     * The items contained by this module-info.
     *
     * @return the items
     */
    @Singular
    List<ModuleInfoItem> items();

    /**
     * Returns true if this module info is unnamed.
     *
     * @return true if this module is unnamed
     */
    default boolean isUnnamed() {
        return DEFAULT_MODULE_NAME.equals(name());
    }

    /**
     * Provides the ability to create a new merged descriptor using this as the basis, and then combining another into it
     * in order to create a new descriptor.
     *
     * @param another the other descriptor to merge
     * @return the merged descriptor
     */
    @SuppressWarnings("unchecked")
    default ModuleInfoDescriptor mergeCreate(
            ModuleInfoDescriptor another) {
        if (another == this) {
            throw new IllegalArgumentException("can't merge with self");
        }

        DefaultModuleInfoDescriptor.Builder newOne = DefaultModuleInfoDescriptor.toBuilder(this);
        for (ModuleInfoItem itemThere : another.items()) {
            Optional<ModuleInfoItem> itemHere = first(itemThere.target());
            if (itemHere.isPresent()) {
                int index = newOne.items.indexOf(itemHere.get());
                newOne.items.remove(index);
                newOne.items.add(index, itemHere.get().mergeCreate(itemThere));
            } else {
                newOne.addItem(itemThere);
            }
        }

        return newOne.build();
    }

    /**
     * Takes a builder, and if the target does not yet exist, will add the new module info item from the supplier.
     *
     * @param builder       the fluent builder
     * @param target        the target to check for existence for
     * @param itemSupplier  the item to add which presumably has the same target as above
     * @return true if added
     */
    static boolean addIfAbsent(
            DefaultModuleInfoDescriptor.Builder builder,
            String target,
            Supplier<ModuleInfoItem> itemSupplier) {
        Optional<ModuleInfoItem> existing = builder.first(target);
        if (existing.isEmpty()) {
            ModuleInfoItem item = Objects.requireNonNull(itemSupplier.get());
            assert (target.equals(item.target())) : "target mismatch: " + target + " and " + item.target();
            builder.addItem(item);
            return true;
        }
        return false;
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its source file location.
     *
     * @param path the source path location for the module-info descriptor
     * @return the module-info descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    static ModuleInfoDescriptor create(
            Path path) {
        return create(path, Ordering.NATURAL_PRESERVE_COMMENTS);
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its source file location and preferred ordering scheme.
     *
     * @param path      the source path location for the module-info descriptor
     * @param ordering  the ordering to apply
     * @return the module-info descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    static ModuleInfoDescriptor create(
            Path path,
            Ordering ordering) {
        try {
            String moduleInfo = Files.readString(path);
            return create(moduleInfo, ordering);
        } catch (IOException e) {
            throw new ToolsException("unable to load: " + path, e);
        }
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its source input stream.
     *
     * @param is the source file input stream
     * @return the module-info descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    static ModuleInfoDescriptor create(
            InputStream is) {
        return create(is, Ordering.NATURAL_PRESERVE_COMMENTS);
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its input stream.
     *
     * @param is the source file location
     * @param ordering the ordering to apply
     * @return the module-info descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    static ModuleInfoDescriptor create(
            InputStream is,
            Ordering ordering) {
        try {
            String moduleInfo = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return create(moduleInfo, ordering);
        } catch (IOException e) {
            throw new ToolsException("unable to load from stream", e);
        }
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its literal source.
     *
     * @param moduleInfo the source
     * @return the module-info descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    static ModuleInfoDescriptor create(
            String moduleInfo) {
        return create(moduleInfo, Ordering.NATURAL_PRESERVE_COMMENTS);
    }

    /**
     * Loads and creates the {@code module-info} descriptor given its literal source and preferred ordering scheme.
     *
     * @param moduleInfo    the source
     * @param ordering      the ordering to apply
     * @return the module-info descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    static ModuleInfoDescriptor create(
            String moduleInfo,
            Ordering ordering) {
        DefaultModuleInfoDescriptor.Builder descriptor = DefaultModuleInfoDescriptor.builder();

        String clean = moduleInfo;
        List<String> comments = null;
        if (Ordering.NATURAL_PRESERVE_COMMENTS == ordering) {
            comments = new ArrayList<>();
        } else {
            clean = moduleInfo.replaceAll("/\\*[^*]*(?:\\*(?!/)[^*]*)*\\*/|//.*", "");
        }

        boolean firstLine = true;
        Map<String, TypeName> importAliases = new LinkedHashMap<>();
        String line = null;
        try (BufferedReader reader = new BufferedReader(new StringReader(clean))) {
            while (null != (line = cleanLine(reader, comments, importAliases))) {
                if (firstLine && (comments != null) && comments.size() > 0) {
                    descriptor.headerComment(String.join("\n", comments));
                }
                firstLine = false;

                String[] split = line.split("\\s+");
                if (line.startsWith("module ")) {
                    descriptor.name(split[1]);
                } else if (line.startsWith("requires ")) {
                    int start = 1;
                    boolean isStatic = (split[start].equals("static"));
                    boolean isTransitive = (split[start].equals("transitive"));
                    if (isStatic || isTransitive) {
                        start++;
                    }
                    for (int i = start; i < split.length; i++) {
                        descriptor.addItem(requiresModuleName(cleanLine(split[i]), isTransitive, isStatic,
                                                              (comments != null) ? comments : List.of()));
                    }
                } else if (line.startsWith("exports ")) {
                    DefaultModuleInfoItem.Builder exports = DefaultModuleInfoItem.builder()
                            .exports(true)
                            .target(resolve(split[1], importAliases))
                            .precomments((comments != null) ? comments : List.of());
                    for (int i = 2; i < split.length; i++) {
                        if (!"to".equalsIgnoreCase(split[i])) {
                            exports.addWithOrTo(resolve(cleanLine(split[i]), importAliases));
                        }
                    }
                    descriptor.addItem(exports.build());
                } else if (line.startsWith("uses ")) {
                    DefaultModuleInfoItem.Builder uses = DefaultModuleInfoItem.builder()
                            .uses(true)
                            .target(resolve(split[1], importAliases))
                            .precomments((comments != null) ? comments : List.of());
                    descriptor.addItem(uses.build());
                } else if (line.startsWith("provides ")) {
                    DefaultModuleInfoItem.Builder provides = DefaultModuleInfoItem.builder()
                            .provides(true)
                            .target(resolve(split[1], importAliases))
                            .precomments((comments != null) ? comments : List.of());
                    if (split.length < 3) {
                        throw new ToolsException("unable to process module-info's use of: " + line);
                    }
                    if (split[2].equals("with")) {
                        for (int i = 3; i < split.length; i++) {
                            provides.addWithOrTo(resolve(cleanLine(split[i]), importAliases));
                        }
                    }
                    descriptor.addItem(provides.build());
                } else if (line.equals("}")) {
                    break;
                } else {
                    throw new ToolsException("unable to process module-info's use of: " + line);
                }

                if (comments != null) {
                    comments = new ArrayList<>();
                }
            }
        } catch (ToolsException e) {
            throw e;
        } catch (Exception e) {
            if (line != null) {
                throw new ToolsException("failed on line: " + line + ";\n"
                                                 + "unable to load or parse module-info: " + moduleInfo, e);
            }
            throw new ToolsException("unable to load or parse module-info: " + moduleInfo, e);
        }

        return descriptor.build();
    }

    /**
     * Saves the descriptor source to the provided path.
     *
     * @param path the target path
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    default void save(
            Path path) {
        try {
            Files.writeString(path, contents());
        } catch (IOException e) {
            throw new ToolsException("unable to save: " + path, e);
        }
    }

    /**
     * Retrieves the first item matching the target requested.
     *
     * @param target the target name to find
     * @return the item or empty if not found
     */
    default Optional<ModuleInfoItem> first(
            String target) {
        return items().stream()
                .filter(it -> it.target().equals(target))
                .findFirst();
    }

    /**
     * Returns the first export found in the module that is unqualified with any extra {@code to} declaration.
     *
     * @return the first package that is exported from this module, or empty if there are no exports appropriate
     */
    default Optional<String> firstUnqualifiedPackageExport() {
        return items().stream()
                .filter(item -> item.exports() && item.withOrTo().isEmpty())
                .map(ModuleInfoItem::target)
                .findFirst();
    }

    /**
     * Provides the content of the description appropriate to write out.
     *
     * @return The contents (source code body) for this descriptor.
     */
    default String contents() {
        return contents(true);
    }

    /**
     * Provides the content of the description appropriate to write out.
     *
     * @param wantAnnotation flag determining whether the Generated annotation comment should be present
     * @return The contents (source code body) for this descriptor.
     */
    default String contents(
            boolean wantAnnotation) {
        TemplateHelper helper = TemplateHelper.create();

        Map<String, Object> subst = new HashMap<>();
        subst.put("name", name());
        List<ModuleInfoItem> items = items();
        if (!items.isEmpty()) {
            if (Ordering.SORTED == ordering()) {
                ArrayList<ModuleInfoItem> newItems = new ArrayList<>();
                items.forEach(i -> newItems.add(DefaultModuleInfoItem.toBuilder(i).ordering(Ordering.SORTED).build()));
                items = newItems;
                items.sort(Comparator.comparing(ModuleInfoItem::target));
            }
            subst.put("items", items);
        }
        if (wantAnnotation) {
            subst.put("generatedanno",
                      (Ordering.NATURAL_PRESERVE_COMMENTS == ordering() || headerComment().isPresent())
                              ? null : helper.defaultGeneratedStickerFor(getClass().getName()));
        }
        headerComment().ifPresent(it -> subst.put("header", it));
        descriptionComment().ifPresent(it -> subst.put("description", it));
        subst.put("hasdescription", descriptionComment().isPresent());
        String template = helper.safeLoadTemplate(templateName(), SERVICE_PROVIDER_MODULE_INFO_HBS);
        String contents = helper.applySubstitutions(template, subst, true).trim();
        return CommonUtils.trimLines(contents);
    }

    /**
     * Creates a new item declaring a {@code uses} external contract definition from this module descriptor.
     *
     * @param externalContract the external contract definition
     * @return the item created
     */
    static ModuleInfoItem usesExternalContract(
            Class<?> externalContract) {
        return usesExternalContract(externalContract.getName());
    }

    /**
     * Creates a new item declaring a {@code uses} external contract definition from this module descriptor.
     *
     * @param externalContract the external contract definition
     * @return the item created
     */
    static ModuleInfoItem usesExternalContract(
            TypeName externalContract) {
        return usesExternalContract(externalContract.name());
    }

    /**
     * Creates a new item declaring a {@code uses} external contract definition from this module descriptor.
     *
     * @param externalContract the external contract definition
     * @return the item created
     */
    static ModuleInfoItem usesExternalContract(
            String externalContract) {
        return DefaultModuleInfoItem.builder().uses(true).target(externalContract).build();
    }

    /**
     * Creates a new item declaring it to provide some contract from this module definition, along with
     * a {@code 'with'} declaration.
     *
     * @param contract  the contract definition being provided
     * @param with      the with part
     * @return the item created
     */
    static ModuleInfoItem providesContract(
            String contract,
            String with) {
        return DefaultModuleInfoItem.builder().provides(true).target(contract).addWithOrTo(with).build();
    }

    /**
     * Creates a new item declaring a {@code requires} on an external module usage from this module descriptor.
     *
     * @param moduleName the module name to require
     * @return the item created
     */
    static ModuleInfoItem requiresModuleName(
            String moduleName) {
        return DefaultModuleInfoItem.builder().requires(true).target(moduleName).build();
    }

    /**
     * Creates a new item declaring a {@code requires} on an external module usage from this module descriptor, that is
     * extended to use additional item attributes.
     *
     * @param moduleName    the module name to require
     * @param isTransitive  true if the requires declaration is transitive
     * @param isStatic      true if the requires declaration is static
     * @param comments      any comments to ascribe to the item
     * @return the item created
     */
    static ModuleInfoItem requiresModuleName(
            String moduleName,
            boolean isTransitive,
            boolean isStatic,
            List<String> comments) {
        return DefaultModuleInfoItem.builder()
                .requires(true)
                .precomments(comments)
                .transitiveUsed(isTransitive)
                .staticUsed(isStatic)
                .target(moduleName)
                .build();
    }

    /**
     * Creates a new item {@code exports} on a a package from this module descriptor.
     *
     * @param typeName the type name exported
     * @return the item created
     */
    static ModuleInfoItem exportsPackage(
            TypeName typeName) {
        return exportsPackage(typeName.packageName());
    }

    /**
     * Creates a new item {@code exports} on a a package from this module descriptor.
     *
     * @param pkg the package name exported
     * @return the item created
     */
    static ModuleInfoItem exportsPackage(
            String pkg) {
        return DefaultModuleInfoItem.builder().exports(true).target(pkg).build();
    }

    /**
     * Creates a new item {@code exports} on a a package from this module descriptor, along with
     * a {@code 'to'} declaration.
     *
     * @param contract  the contract definition being exported
     * @param to        the to part
     * @return the item created
     */
    static ModuleInfoItem exportsPackage(
            String contract,
            String to) {
        return DefaultModuleInfoItem.builder().exports(true).target(contract).addWithOrTo(to).build();
    }

    private static String resolve(
            String name,
            Map<String, TypeName> importAliases) {
        TypeName typeName = importAliases.get(name);
        return (typeName == null) ? name : typeName.name();
    }

    private static String cleanLine(
            BufferedReader reader,
            List<String> preComments,
            Map<String, TypeName> importAliases) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }

        String trimmedline = line.trim();
        if (preComments != null) {
            boolean incomment = trimmedline.startsWith("//") || trimmedline.startsWith("/*");
            boolean inempty = trimmedline.isEmpty();
            while (incomment || inempty) {
                preComments.add(line);
                incomment = incomment && !trimmedline.endsWith("*/") && !trimmedline.startsWith("//");

                line = reader.readLine();
                if (line == null) {
                    return null;
                }
                trimmedline = line.trim();

                inempty = trimmedline.isEmpty();
                if (!inempty && !incomment) {
                    incomment = trimmedline.startsWith("//") || trimmedline.startsWith("/*");
                }
            }
        }

        StringBuilder result = new StringBuilder(trimmedline);
        String tmp;
        while (!trimmedline.endsWith(";") && !trimmedline.endsWith("}") && !trimmedline.endsWith("{")
                && (null != (tmp = reader.readLine()))) {
            if (tmp.contains("/*") || tmp.contains("*/") || tmp.contains("//")) {
                throw new IOException("Unable to parse line-level comments: '" + line + "'");
            }
            tmp = tmp.trim();
            result.append(" ").append(tmp);
            if (tmp.endsWith(";") || tmp.endsWith("}") || tmp.endsWith("{")) {
                break;
            }
        }

        line = cleanLine(result.toString());
        if (line.startsWith("import ")) {
            String[] split = line.split("\\s+");
            TypeName typeName = DefaultTypeName.createFromTypeName(split[split.length - 1]);
            importAliases.put(typeName.className(), typeName);
            line = cleanLine(reader, preComments, importAliases);
        }
        return line;
    }

    private static String cleanLine(
            String line) {
        if (line == null) {
            return null;
        }

        while (line.endsWith(";") || line.endsWith(",")) {
            line = line.substring(0, line.length() - 1).trim();
        }

        if (line.contains("/*") || line.contains("*/")) {
            throw new ToolsException("unable to parse lines that have inner comments: '" + line + "'");
        }

        return line.trim();
    }

}
