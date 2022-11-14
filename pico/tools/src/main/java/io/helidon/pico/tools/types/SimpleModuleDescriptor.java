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

package io.helidon.pico.tools.types;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.utils.CommonUtils;
import io.helidon.pico.tools.utils.TemplateHelper;
import io.helidon.pico.types.TypeName;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * Here to provide the basics for module-info creation pre Java 9. This is a very simplified version of java.lang.module.ModuleDescriptor
 */
public class SimpleModuleDescriptor implements Cloneable {
    static final String SERVICE_PROVIDER_MODULE_INFO_HBS = "service-provider-module-info.hbs";

    /**
     * Used to declare the preferred ordering of the items in the module-info.
     */
    public enum Ordering {
        /**
         * Little or no attempt is made to preserve comments, ordering is arranged top-down.
         */
        NATURAL,

        /**
         * Attempt is preserve comments, ordering is arranged top-down.
         */
        NATURAL_PRESERVE_COMMENTS,

        /**
         * Little or no attempt is made to preserve comments, ordering is arranged sorted by the target class or package.
         */
        SORTED
    }

    /**
     * The tag used to represent the module name.
     */
    public static final String TAG_MODULE_NAME = "module_name";

    /**
     * The default module name (i.e., "unnamed").
     */
    public static final String DEFAULT_MODULE_NAME = "unnamed";

    /**
     * The default suffix on the module name for the test module. This suffix will be appended to the module name
     * of the main module.
     */
    public static final String DEFAULT_TEST_SUFFIX = "test";

    /**
     * The base module-info name.
     */
    public static final String MODULE_INFO_NAME = "module-info";

    /**
     * The java module-info name.
     */
    public static final String DEFAULT_MODULE_INFO_JAVA_NAME = MODULE_INFO_NAME + ".java";

    private String name;
    private String templateName = TemplateHelper.DEFAULT_TEMPLATE_NAME;
    private String headerComment;
    private String description;
    private final Ordering ordering;
    private final Map<String, Item> items;
    private boolean readOnly;

    protected SimpleModuleDescriptor(boolean readOnly) {
        this(null);
        this.readOnly = readOnly;
    }

    /**
     * Ctor.
     */
    public SimpleModuleDescriptor() {
        this(null);
    }

    /**
     * Ctor.
     *
     * @param name      the module name
     */
    public SimpleModuleDescriptor(String name) {
        this(name, null);
    }

    /**
     * Ctor.
     *
     * @param name      the module name
     * @param ordering  the ordering to apply or null for defaults
     */
    public SimpleModuleDescriptor(String name, Ordering ordering) {
        this.ordering = Objects.isNull(ordering) ? Ordering.NATURAL : ordering;
        this.items = (Ordering.SORTED != this.ordering) ? new LinkedHashMap<>() : new TreeMap<>();
        name(name);
    }

    SimpleModuleDescriptor(SimpleModuleDescriptor src, Ordering ordering) {
        this(src.getName(), Objects.isNull(ordering) ? src.getOrdering() : ordering);
        templateName(src.getTemplateName());
        headerComment(src.getHeaderComment());
        description(src.getDescription());
        for (Map.Entry<String, Item> e : src.items.entrySet()) {
            add(e.getValue().cloneToOrdering(ordering));
        }
    }

    @Override
    public SimpleModuleDescriptor clone() {
        return cloneToOrdering(ordering);
    }

    /**
     * Clone this instance, and optionally assign a new ordering.
     *
     * @param ordering optionally, the new ordering to apply
     * @return the cloned descriptor
     */
    public SimpleModuleDescriptor cloneToOrdering(Ordering ordering) {
        assert (!readOnly);
        return new SimpleModuleDescriptor(this, ordering);
    }

    /**
     * @return the ordering being used for this instance
     */
    public Ordering getOrdering() {
        return ordering;
    }

    /**
     * @return true if this module is unnamed.
     */
    public boolean isUnnamed() {
        return DEFAULT_MODULE_NAME.equals(getName());
    }

    /**
     * Loads the descriptor giving its source file definition.
     *
     * @param file the source file location
     * @return the descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    public static SimpleModuleDescriptor uncheckedLoad(File file) {
        try {
            return load(file, Ordering.NATURAL_PRESERVE_COMMENTS);
        } catch (IOException e) {
            throw new ToolsException("unable to load: " + file, e);
        }
    }

    /**
     * Loads the descriptor giving its source file definition.
     *
     * @param file the source file location
     * @param ordering the ordering to apply
     * @return the descriptor
     * @throws IOException if there is any exception encountered
     */
    public static SimpleModuleDescriptor load(File file, Ordering ordering) throws IOException {
        try {
            Path filePath = file.toPath();
            String moduleInfo = Files.readString(filePath);
            return load(moduleInfo, ordering);
        } catch (ToolsException e) {
            throw new ToolsException("unable to load: " + file, e);
        }
    }

    /**
     * Loads the descriptor giving its source file input stream.
     *
     * @param is the source file input stream
     * @return the descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    public static SimpleModuleDescriptor uncheckedLoad(InputStream is) {
        try {
            return load(is, Ordering.NATURAL_PRESERVE_COMMENTS);
        } catch (IOException e) {
            throw new ToolsException("unable to load", e);
        }
    }

    /**
     * Loads the descriptor giving its input stream.
     *
     * @param is the source file location
     * @param ordering the ordering to apply
     * @return the descriptor
     * @throws IOException if there is any exception encountered
     */
    public static SimpleModuleDescriptor load(InputStream is, Ordering ordering) throws IOException {
        String moduleInfo = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        return load(moduleInfo, ordering);
    }

    /**
     * Loads the descriptor given its literal source.
     *
     * @param moduleInfo the source
     * @return the descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    public static SimpleModuleDescriptor uncheckedLoad(String moduleInfo) {
        try {
            return load(moduleInfo, Ordering.NATURAL_PRESERVE_COMMENTS);
        } catch (IOException e) {
            throw new ToolsException("unable to load", e);
        }
    }

    /**
     * Loads the descriptor given its literal source.
     *
     * @param moduleInfo the source
     * @param ordering the ordering to apply
     * @return the descriptor
     * @throws io.helidon.pico.tools.ToolsException if there is any exception encountered
     */
    public static SimpleModuleDescriptor load(String moduleInfo, Ordering ordering) throws IOException {
        if (Objects.isNull(moduleInfo)) {
            return null;
        }

        final SimpleModuleDescriptor descriptor = new SimpleModuleDescriptor((String) null, ordering);
        descriptor.headerComment = null;

        String clean = moduleInfo;
        List<String> commentBuilder = null;
        if (Ordering.NATURAL_PRESERVE_COMMENTS == ordering) {
            commentBuilder = new ArrayList<>();
        } else {
            clean = moduleInfo.replaceAll("/\\*[^*]*(?:\\*(?!/)[^*]*)*\\*/|//.*", "");
        }

        boolean firstLine = true;
        final Map<String, TypeName> importAliases = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(clean))) {
            String line;
            while (null != (line = cleanLine(reader, commentBuilder, importAliases))) {
                if (firstLine && Objects.nonNull(commentBuilder) && commentBuilder.size() > 0) {
                    descriptor.headerComment = CommonUtils.toString(commentBuilder, null, "\n");
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
                        descriptor.add(Item.requiresModuleName(cleanLine(split[i]), commentBuilder, isTransitive, isStatic));
                    }
                } else if (line.startsWith("exports ")) {
                    Item exports = Item.exportsPackage(resolve(split[1], importAliases)).precomments(commentBuilder);
                    for (int i = 2; i < split.length; i++) {
                        if (!"to".equalsIgnoreCase(split[i])) {
                            exports.to(resolve(cleanLine(split[i]), importAliases));
                        }
                    }
                    descriptor.add(exports);
                } else if (line.startsWith("uses ")) {
                    descriptor.add(Item.usesExternalContract(resolve(split[1], importAliases)).precomments(commentBuilder));
                } else if (line.startsWith("provides ")) {
                    Item provides = Item.providesContract(resolve(split[1], importAliases)).precomments(commentBuilder);
                    if (split[2].equals("with")) {
                        for (int i = 3; i < split.length; i++) {
                            provides.with(resolve(cleanLine(split[i]), importAliases));
                        }
                    }
                    descriptor.add(provides);
                } else if (line.equals("}")) {
                    break;
                } else {
                    throw new ToolsException("unable to process module-info's use of: " + line);
                }

                if (Objects.nonNull(commentBuilder)) {
                    commentBuilder = new ArrayList<>();
                }
            }
        }

        return descriptor;
    }

    /**
     * Saves the descriptor source to the provided file location.
     *
     * @param file the file location
     */
    public void uncheckedSave(File file) {
        try {
            save(file);
        } catch (IOException e) {
            throw new ToolsException("unable to save: " + file, e);
        }
    }

    /**
     * Saves the descriptor source to the provided file location.
     *
     * @param file the file location
     */
    public void save(File file) throws IOException {
        try {
            Path filePath = file.toPath();
            Files.writeString(filePath, getContents());
        } catch (ToolsException e) {
            throw new ToolsException("unable to save: " + file, e);
        }
    }

    /**
     * Provides the ability to merge two descriptors together.
     *
     * @param another the other descriptor to merge
     * @return the merged descriptor
     */
    public SimpleModuleDescriptor merge(SimpleModuleDescriptor another) {
        assert (!readOnly);
        if (Objects.nonNull(another)) {
            for (Item item : another.getItems()) {
                add(item);
            }
        }
        return this;
    }

    /**
     * Retrieves the item matching the targetClassType requested, or null if not found.
     *
     * @param targetClassType the target class type to find
     * @return the item or null if not found
     */
    public Item get(TypeName targetClassType) {
        return get(targetClassType.name());
    }

    /**
     * Retrieves the item matching the targetClassType requested, or null if not found.
     *
     * @param targetClassType the target class type to find
     * @return the item or null if not found
     */
    public Item get(Class<?> targetClassType) {
        return get(targetClassType.getName());
    }

    /**
     * Retrieves the item matching the targetClassType requested, or null if not found.
     *
     * @param targetClassTypeName the target class type to find
     * @return the item or null if not found
     */
    public Item get(String targetClassTypeName) {
        return items.get(targetClassTypeName);
    }

    /**
     * Removes the item matching the targetClassType requested, or null if not found.
     *
     * @param targetClassType the target class type to find
     * @return the item or null if not found
     */
    public Item remove(TypeName targetClassType) {
        return remove(targetClassType.name());
    }

    /**
     * Removes the item matching the targetClassType requested, or null if not found.
     *
     * @param targetClassType the target class type to find
     * @return the item or null if not found
     */
    public Item remove(Class<?> targetClassType) {
        return remove(targetClassType.getName());
    }

    /**
     * Removes the item matching the targetClassType requested, or null if not found.
     *
     * @param targetClassTypeName the target class type to find
     * @return the item or null if not found
     */
    public Item remove(String targetClassTypeName) {
        return items.remove(targetClassTypeName);
    }

    /**
     * @return the first package that is exported from this module, or null if there are no exports.
     */
    public String getFirstUnqualifiedPackageExport() {
        return getItems().stream()
                .filter((item) -> item.exports() && item.withOrTo().isEmpty())
                .map(Item::target)
                .findFirst().orElse(null);
    }

    /**
     * Declares the provides-with item, and if a previous-with was set, it will be replaced with the new value.
     *
     * @param provides the provides
     * @param with the with
     * @return the item that ultimately was added or replaced
     */
    public SimpleModuleDescriptor.Item setOrReplace(Class<?> provides, String with) {
        return setOrReplace(provides.getName(), with);
    }

    /**
     * Declares the provides-with item, and if a previous-with was set, it will be replaced with the new value.
     *
     * @param provides the provides
     * @param with the with
     * @return the item that ultimately was added or replaced
     */
    public SimpleModuleDescriptor.Item setOrReplace(String provides, String with) {
        assert (!readOnly);
        Item item = items.get(provides);
        if (Objects.isNull(item)) {
            item = add(Item.providesContract(provides).with(with));
        } else {
            item.withOrTo.clear();
            item.with(with);
        }
        return Objects.requireNonNull(item);
    }

    private static String resolve(String name, Map<String, TypeName> importAliases) {
        TypeName typeName = importAliases.get(name);
        return Objects.isNull(typeName) ? name : typeName.name();
    }

    private static String cleanLine(BufferedReader reader,
                                    List<String> preComments,
                                    Map<String, TypeName> importAliases) throws IOException {
        String line = reader.readLine();
        if (Objects.isNull(line)) {
            return null;
        }

        String trimmedline = line.trim();
        if (Objects.nonNull(preComments)) {
            boolean incomment = trimmedline.startsWith("//") || trimmedline.startsWith("/*");
            boolean inempty = trimmedline.isEmpty();
            while (incomment || inempty) {
                preComments.add(line);
                incomment = incomment && !trimmedline.endsWith("*/") && !trimmedline.startsWith("//");

                line = reader.readLine();
                if (Objects.isNull(line)) {
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

    private static String cleanLine(String line) {
        if (Objects.isNull(line)) {
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

    /**
     * Assigns the name to this descriptor.
     *
     * @param name the new name
     * @return this descriptor, as a fluent builder style
     */
    public SimpleModuleDescriptor name(String name) {
        assert (!readOnly);
        assert (Objects.isNull(this.name) || DEFAULT_MODULE_NAME.equals(this.name)) : "name is already set to " + getName();
        this.name = (Objects.isNull(name)) ? DEFAULT_MODULE_NAME : name;
        return this;
    }

    /**
     * @return the name for this descriptor.
     */
    public String getName() {
        return name;
    }

    /**
     * Adds a new item to the descriptor, or merges in the new attributes if the item already exists.
     *
     * @param item the item to add or merge in
     * @return the item that was ultimately added, which may be different than what was passed in
     */
    public SimpleModuleDescriptor.Item add(Item item) {
        assert (!readOnly);
        if (item.ordering() != ordering) {
            item = item.cloneToOrdering(ordering);
        }
        Item prev = items.put(Objects.requireNonNull(item).target, item);
        if (Objects.nonNull(prev)) {
            prev = prev.merge(item);
            items.put(item.target, prev);
            return prev;
        }
        return item;
    }

    /**
     * Virtually the same as {@link #add(SimpleModuleDescriptor.Item)}, but will
     * forgo adding if the item already exists.
     *
     * @param item the item to add or merge in
     * @return the item that was ultimately added, which may be different than what was passed in
     */
    public SimpleModuleDescriptor.Item addIfAbsent(Item item) {
        assert (!readOnly);
        Item prev = items.get(Objects.requireNonNull(item).target);
        if (Objects.isNull(prev)) {
            return add(item);
        }
        return null;
    }

    SimpleModuleDescriptor templateName(String name) {
        assert (!readOnly);
        this.templateName = Objects.isNull(name) ? TemplateHelper.DEFAULT_TEMPLATE_NAME : name;
        return this;
    }

    protected String getTemplateName() {
        return templateName;
    }

    /**
     * Sets the header comment for the descriptor.
     *
     * @param headerComment the header comment
     * @return this descriptor instance, fluent style
     */
    public SimpleModuleDescriptor headerComment(String headerComment) {
        assert (!readOnly);
        this.headerComment = headerComment;
        return this;
    }

    /**
     * @return the header comment.
     */
    public String getHeaderComment() {
        return headerComment;
    }

    /**
     * Sets the description. Descriptions come after the header comments.
     *
     * @param description the description
     * @return this descriptor instance, fluent style
     */
    public SimpleModuleDescriptor description(String description) {
        assert (!readOnly);
        this.description = description;
        return this;
    }

    /**
     * @return the descriptor
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the items that comprise this descriptor
     */
    public Set<Item> getItems() {
        return new LinkedHashSet<>(items.values());
    }

    /**
     * @return The contents (source code body) for this descriptor.
     */
    public String getContents() {
        Map<String, Object> subst = new HashMap<>();
        subst.put("name", getName());
        subst.put("items", getItems());
        subst.put("generatedanno",
                  (Ordering.NATURAL_PRESERVE_COMMENTS == ordering || Objects.nonNull(getHeaderComment()))
                                    ? null : TemplateHelper.getDefaultGeneratedSticker(getClass().getName()));
        subst.put("header", getHeaderComment());
        subst.put("description", getDescription());
        String template = TemplateHelper.safeLoadTemplate(getTemplateName(), SERVICE_PROVIDER_MODULE_INFO_HBS);
        String contents = TemplateHelper.applySubstitutions(System.err, template, subst).trim();
        contents = contents.replace("module " + getName() + " { ", "module " + getName() + " {");
        return contents;
    }

    @Override
    public String toString() {
        return getContents();
    }

    /**
     * The descriptor is comprised of items, where the target for the item is the fully qualified package, module, or
     * class name in question.
     */
    @Getter
    @Setter
    @SuperBuilder
    @Accessors(fluent = true)
    public static class Item {
        /*@Singular("precomment")*/ private List<String> precomments;
        private final String target;
        private boolean requires;
        private boolean uses;
        private boolean isTransitive;
        private boolean isStatic;
        private boolean exports;
        private boolean opens;
        private boolean provides;
        private final Ordering ordering;
        private final Set<String> withOrTo;

        /**
         * Creates an item using just the target name.
         *
         * @param target the target name
         */
        public Item(String target) {
            this(target, Ordering.NATURAL);
        }

        /**
         * Creates a new item for inclusion.
         *
         * @param target the fully qualified class, package, or module name
         * @param ordering optionally, the ordering to assign - applicable to targets that use 'with' or 'to'
         */
        public Item(String target, Ordering ordering) {
            this.target = Objects.requireNonNull(target);
            this.ordering = Objects.requireNonNull(ordering);
            this.withOrTo = (Ordering.SORTED != ordering) ? new LinkedHashSet<>() : new TreeSet<>();
        }

        /**
         * Clones an item, optionally providing a new ordering.
         *
         * @param src       the instance to clone
         * @param ordering  optionally, the new ordering to apply
         */
        private Item(Item src, Ordering ordering) {
            this(src.target(), ordering);
            this.precomments(src.precomments());
            this.requires = src.requires();
            this.uses = src.uses();
            this.isTransitive = src.isTransitive();
            this.isStatic = src.isStatic();
            this.exports = src.exports();
            this.opens = src.opens();
            this.provides = src.provides();
            this.withOrTo.addAll(src.withOrTo());
        }

        /**
         * Clones an item, optionally providing a new ordering.
         *
         * @param ordering  optionally, the new ordering to apply
         * @return the cloned item
         */
        public Item cloneToOrdering(Ordering ordering) {
            return new Item(this, ordering);
        }

        /**
         * Creates a new item declaring it to use some external module's contract definition.
         *
         * @param externalContract the external contract definition
         * @return the item created
         */
        public static Item usesExternalContract(Class<?> externalContract) {
            return usesExternalContract(externalContract.getName());
        }

        /**
         * Creates a new item declaring it to use some external module's contract definition.
         *
         * @param externalContract the external contract definition
         * @return the item created
         */
        public static Item usesExternalContract(String externalContract) {
            return new Item(externalContract).uses(true);
        }

        /**
         * Creates a new item declaring it to provide some contract from this module definition.
         *
         * @param contract the contract definition being provided
         * @return the item created
         */
        public static Item providesContract(Class<?> contract) {
            return providesContract(contract.getName());
        }

        /**
         * Creates a new item declaring it to provide some contract from this module definition.
         *
         * @param contract the contract definition being provided
         * @return the item created
         */
        public static Item providesContract(String contract) {
            return new Item(contract).provides(true);
        }

        /**
         * Creates a new item declaring it to provide some contract from this module definition, along with an
         * optional 'with' declaration.
         *
         * @param contract  the contract definition being provided
         * @param with      the optional with declaration
         * @return the item created
         */
        public static Item providesContract(Class<?> contract, String with) {
            return providesContract(contract).with(with);
        }

        /**
         * Creates a new item declaring an external module usage.
         *
         * @param moduleName the module name to declare usage for
         * @return the item created
         */
        public static Item requiresModuleName(String moduleName) {
            return new Item(moduleName).requires(true);
        }

        /**
         * Creates a new item declaring an external module usage.
         *
         * @param moduleName    the module name to declare usage for
         * @param comments      any comments to ascribe to the item
         * @param isTransitive  true if the requires declaration is transitive
         * @param isStatic      true if the requires declaration is static
         * @return the item created
         */
        public static Item requiresModuleName(String moduleName, List<String> comments, boolean isTransitive, boolean isStatic) {
            return requiresModuleName(moduleName).precomments(comments).isTransitive(isTransitive).isStatic(isStatic);
        }

        /**
         * Creates a new item declaring a package being exported.
         *
         * @param typeName the type name exported
         * @return the item created
         */
        public static Item exportsPackage(TypeName typeName) {
            return exportsPackage(typeName.packageName());
        }

        /**
         * Creates a new item declaring a package being exported.
         *
         * @param pkg the package name exported
         * @return the item created
         */
        public static Item exportsPackage(String pkg) {
            return new Item(pkg).exports(true);
        }

        /**
         * Assigns a 'with' clause to the item.
         *
         * @param with the 'with' clause
         * @return this item, fluent style
         */
        public Item with(String with) {
            this.withOrTo.add(Objects.requireNonNull(with));
            return this;
        }

        /**
         * Assigns a 'with' clause to the item.
         *
         * @param with the 'with' clause
         * @return this item, fluent style
         */
        public Item with(Collection<String> with) {
            this.withOrTo.addAll(Objects.requireNonNull(with));
            return this;
        }

        /**
         * Assigns a 'to' clause to the item.
         *
         * @param to the 'to' clause
         * @return this item, fluent style
         */
        public Item to(String to) {
            this.withOrTo.add(Objects.requireNonNull(to));
            return this;
        }

        /**
         * Assigns a 'to' clause to the item.
         *
         * @param to the 'to' clause
         * @return this item, fluent style
         */
        public Item to(Collection<String> to) {
            this.withOrTo.addAll(Objects.requireNonNull(to));
            return this;
        }

        /**
         * Assigns a pre-comment to the item's header.
         *
         * @param precomment the optional precomment
         * @return this item, fluent style
         */
        public Item precomment(CharSequence precomment) {
            if (Objects.isNull(precomment)) {
                return this;
            }
            return precomments(Collections.singleton(precomment.toString()));
        }

        /**
         * Assigns a pre-comment to the item's header.
         *
         * @param precomments the optional precomments
         * @return this item, fluent style
         */
        public Item precomments(Collection<String> precomments) {
            if (Objects.isNull(precomments)) {
                return this;
            }
            Set<String> merged = Objects.isNull(this.precomments) ? new LinkedHashSet<>() : new LinkedHashSet<>(this.precomments);
            merged.addAll(precomments);
            this.precomments = new ArrayList<>(merged);
            return this;
        }

        /**
         * Merges another item into this one.
         *
         * @param another the other item
         * @return the merged item
         */
        public Item merge(Item another) {
            assert (target.equals(another.target));
            precomments(another.precomments);
            requires |= another.requires;
            isTransitive |= another.isTransitive;
            isStatic |= another.isStatic;
            exports |= another.exports;
            opens |= another.opens;
            provides |= another.provides;
            with(another.withOrTo);
            return this;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            boolean handled = false;
            if (uses) {
                assert (!requires);
                assert (!isTransitive);
                assert (!isStatic);
                assert (!opens);
                assert (!exports);
                builder.append("uses ");
                handled = true;
            }
            if (provides) {
                assert (!requires);
                assert (!isTransitive);
                assert (!isStatic);
                assert (!opens);
                assert (!exports);
                if (builder.length() > 0) {
                    builder.append(target).append(";\n    ");
                }
                builder.append("provides ");
                if (Objects.nonNull(withOrTo) && !withOrTo.isEmpty()) {
                    builder.append(Objects.requireNonNull(target));
                    builder.append(" with ").append(CommonUtils.toString(withOrTo, null, ",\n\t\t\t"));
                    return builder.toString();
                }
                handled = true;
            }
            if (opens) {
                assert (!requires);
                assert (!isTransitive);
                assert (!isStatic);
                assert (!exports);
                builder.append("opens ");
                handled = true;
            }
            if (requires) {
                assert (!exports);
                assert (!(isTransitive && isStatic));
                builder.append("requires ");
                if (isTransitive) {
                    builder.append("transitive ");
                } else if (isStatic) {
                    builder.append("static ");
                }
                handled = true;
            }
            if (exports) {
                assert (!isTransitive);
                assert (!isStatic);
                builder.append("exports ");
                if (Objects.nonNull(withOrTo) && !withOrTo.isEmpty()) {
                    builder.append(Objects.requireNonNull(target));
                    builder.append(" to ").append(CommonUtils.toString(withOrTo));
                    return builder.toString();
                }
                handled = true;
            }
            assert (handled) : target;
            builder.append(target);
            return builder.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(target);
        }

        @Override
        public boolean equals(Object other) {
            return (other instanceof Item && target.equals(((Item) other).target));
        }

    }

}
