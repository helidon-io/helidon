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

package io.helidon.inject.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

/**
 * Provides the basic formation for {@code module-info.java} creation and manipulation.
 *
 * @see java.lang.module.ModuleDescriptor
 */
@Prototype.Blueprint
@Prototype.CustomMethods(ModuleInfoDescriptorSupport.class)
interface ModuleInfoDescriptorBlueprint {

    /**
     * The default module name (i.e., "unnamed").
     */
    String DEFAULT_MODULE_NAME = "unnamed";

    /**
     * The base module-info name.
     */
    String MODULE_INFO_NAME = "module-info";

    /**
     * The java module-info name.
     */
    String DEFAULT_MODULE_INFO_JAVA_NAME = MODULE_INFO_NAME + ".java";

    /**
     * The module name.
     *
     * @return the module name
     */
    @Option.Default(DEFAULT_MODULE_NAME)
    String name();

    /**
     * The template name to apply. The default is {@link TemplateHelper#DEFAULT_TEMPLATE_NAME}.
     *
     * @return the template name
     */
    @Option.Default(TemplateHelper.DEFAULT_TEMPLATE_NAME)
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
    @Option.Default("NATURAL")
    ModuleInfoOrdering ordering();

    /**
     * The items contained by this module-info.
     *
     * @return the items
     */
    @Option.Singular
    List<ModuleInfoItem> items();

    /**
     * The items that were not handled (due to parsing outages, etc.).
     *
     * @return the list of unhandled lines
     */
    @Option.Singular
    List<String> unhandledLines();

    /**
     * Any throwable/error that were encountered during parsing.
     *
     * @return optionally any error encountered during parsing
     */
    Optional<Throwable> error();

    /**
     * Returns {@code true} if last parsing operation was successful (i.e., if there were no instances of
     * {@link #unhandledLines()} or {@link #error()}'s encountered).
     *
     * @return true if any parsing of the given module-info descriptor appears to be full and complete
     */
    default boolean handled() {
        return error().isEmpty() && unhandledLines().isEmpty();
    }

    /**
     * Returns true if the name currently set is the same as the {@link #DEFAULT_MODULE_NAME}.
     *
     * @return true if the current name is the default name
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
    default ModuleInfoDescriptor mergeCreate(ModuleInfoDescriptor another) {
        if (another == this) {
            throw new IllegalArgumentException("can't merge with self");
        }

        ModuleInfoDescriptor.Builder newOne = ModuleInfoDescriptor.builder((ModuleInfoDescriptor) this);
        for (ModuleInfoItem itemThere : another.items()) {
            Optional<ModuleInfoItem> itemHere = first(itemThere);
            if (itemHere.isPresent()) {
                int index = newOne.items().indexOf(itemHere.get());
                newOne.items().remove(index);
                ModuleInfoItem mergedItem = itemHere.get().mergeCreate(itemThere);
                newOne.items().add(index, mergedItem);
            } else {
                newOne.addItem(itemThere);
            }
        }

        return newOne.build();
    }

    /**
     * Saves the descriptor source to the provided path.
     *
     * @param path the target path
     * @throws ToolsException if there is any exception encountered
     */
    default void save(Path path) {
        try {
            Files.writeString(path, contents());
        } catch (IOException e) {
            throw new ToolsException("Unable to save: " + path, e);
        }
    }

    /**
     * Retrieves the first item matching the target requested.
     *
     * @param item the item to find
     * @return the item or empty if not found
     */
    default Optional<ModuleInfoItem> first(ModuleInfoItem item) {
        return items().stream()
                .filter(it -> (item.uses() && it.uses())
                        || (item.opens() && it.opens())
                        || (item.exports() && it.exports())
                        || (item.provides() && it.provides())
                        || (item.requires() && it.requires()))
                .filter(it -> it.target().equals(item.target()))
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
     * @return the contents (source code body) for this descriptor
     */
    default String contents() {
        return contents(true);
    }

    /**
     * Provides the content of the description appropriate to write out.
     *
     * @param wantAnnotation flag determining whether the Generated annotation comment should be present
     * @return the contents (source code body) for this descriptor
     */
    default String contents(boolean wantAnnotation) {
        TemplateHelper helper = TemplateHelper.create();

        Map<String, Object> subst = new HashMap<>();
        subst.put("name", name());
        List<ModuleInfoItem> items = items();
        if (!items.isEmpty()) {
            if (ModuleInfoOrdering.SORTED == ordering()) {
                ArrayList<ModuleInfoItem> newItems = new ArrayList<>();
                items.forEach(i -> newItems.add(ModuleInfoItem.builder(i).ordering(ModuleInfoOrdering.SORTED).build()));
                items = newItems;
                items.sort(Comparator.comparing(ModuleInfoItem::target));
            }
            subst.put("items", items);
        }
        if (wantAnnotation) {
            TypeName generator = TypeName.create(ModuleInfoDescriptor.class);
            subst.put("generatedanno",
                      (ModuleInfoOrdering.NATURAL_PRESERVE_COMMENTS == ordering() || headerComment().isPresent())
                              ? null : helper.generatedStickerFor(generator,
                                                                  generator,
                                                                  TypeName.create("module-info")));
        }
        headerComment().ifPresent(it -> subst.put("header", it));
        descriptionComment().ifPresent(it -> subst.put("description", it));
        subst.put("hasdescription", descriptionComment().isPresent());
        String template = helper.safeLoadTemplate(templateName(), ModuleUtils.SERVICE_PROVIDER_MODULE_INFO_HBS);
        String contents = helper.applySubstitutions(template, subst, true);
        contents = CommonUtils.trimLines(contents);
        if (!wantAnnotation) {
            contents = contents.replace("\t", "    ");
        }
        return contents;
    }

}
