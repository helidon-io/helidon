/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.helidon.config.metadata.docs.CmPage.Kind;
import io.helidon.config.metadata.docs.CmPage.Row;
import io.helidon.config.metadata.docs.CmPage.Table;
import io.helidon.config.metadata.docs.CmPage.Tables;
import io.helidon.config.metadata.docs.CmPage.Usage;
import io.helidon.config.metadata.model.CmModel;
import io.helidon.config.metadata.model.CmModel.CmEnum;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;
import io.helidon.config.metadata.model.CmNode;
import io.helidon.config.metadata.model.CmNode.CmOptionNode;
import io.helidon.config.metadata.model.CmNode.CmPathNode;
import io.helidon.config.metadata.model.CmResolver;

/**
 * Config metadata page resolver.
 */
final class CmPageResolver {
    private static final System.Logger LOGGER = System.getLogger(CmPageResolver.class.getName());

    private static final String ROOT_PAGE_KEY = "root/root";
    private static final String N_A = "<code>N/A</code>";

    private final CmResolver resolver;
    private final CmDocNames names;
    private final String rootPageName;
    private final String fileExt;
    private final Map<String, Set<String>> dependentTypeNames = new TreeMap<>();
    private final Map<String, CmType> configTypeNames = new TreeMap<>();
    private final Map<String, String> configTypes = new TreeMap<>();
    private final Set<String> contractTypeNames = new TreeSet<>();
    private final Map<String, String> contracts = new TreeMap<>();
    private final Map<String, CmEnum> enumsByName = new TreeMap<>();
    private final Map<String, String> enumTypes = new TreeMap<>();
    private final Map<String, String> syntheticTypes = new TreeMap<>();
    private final Map<CmNode, String> pageKeys = new IdentityHashMap<>();
    private final Map<String, PageBuilder> pageBuilders = new LinkedHashMap<>();
    private final List<CmPage> pages = new ArrayList<>();
    private final CmPage rootPage;

    CmPageResolver(CmModel metadata,
                   String rootPageName,
                   String fileExt,
                   String... reservedFileNames) {
        this.resolver = CmResolver.create(metadata);
        this.rootPageName = rootPageName;
        this.fileExt = fileExt;
        this.names = new CmDocNames(join(rootPageName, reservedFileNames));
        initKnownTypes();
        initDependentTypeNames(metadata);
        initRealPages();
        assignTreePages();
        assignTypeNames();
        assignFileNames();
        buildRows();
        resolveLinks();
        resolveManifests();
        rootPage = buildRootPage();
        for (var page : pageBuilders.values()) {
            pages.add(page.build());
        }
        pages.sort(Comparator.comparing(CmPage::fileName));
    }

    CmPage rootPage() {
        return rootPage;
    }

    List<CmPage> pages() {
        return pages;
    }

    Map<String, String> configTypes() {
        return configTypes;
    }

    Map<String, String> contracts() {
        return contracts;
    }

    Map<String, String> enumTypes() {
        return enumTypes;
    }

    Map<String, String> syntheticTypes() {
        return syntheticTypes;
    }

    private void initKnownTypes() {
        for (var type : resolver.types()) {
            configTypeNames.put(type.typeName(), type);
        }
        for (var type : resolver.enums()) {
            enumsByName.put(type.typeName(), type);
        }
        contractTypeNames.addAll(resolver.contracts());
    }

    private void initRealPages() {
        for (var type : configTypeNames.values()) {
            var page = new PageBuilder(Kind.CONFIG, type, null, null, type.typeName());
            page.dependentTypeNames.addAll(dependentTypeNames.getOrDefault(type.typeName(), Set.of()));
            pageBuilders.put(configPageKey(type.typeName()), page);
        }
        for (var typeName : contractTypeNames) {
            var builder = new PageBuilder(Kind.CONTRACT, null, null, null, typeName);
            pageBuilders.put(contractPageKey(typeName), builder);
        }
        for (var type : enumsByName.values()) {
            var page = new PageBuilder(Kind.ENUM, null, type, null, type.typeName());
            pageBuilders.put(enumPageKey(type.typeName()), page);
        }
    }

    private void initDependentTypeNames(CmModel metadata) {
        for (var module : metadata.modules()) {
            for (var type : module.types()) {
                var dependentTypeName = type.typeName();
                if (!configTypeNames.containsKey(dependentTypeName)) {
                    continue;
                }
                for (var inheritedTypeName : type.inherits()) {
                    dependentTypeName(inheritedTypeName, dependentTypeName);
                }
                for (var option : type.options()) {
                    if (option.merge()) {
                        dependentTypeName(option.typeName(), dependentTypeName);
                    }
                }
            }
        }
    }

    private void dependentTypeName(String typeName, String dependentTypeName) {
        if (typeName.equals(dependentTypeName)
            || !configTypeNames.containsKey(typeName)
            || !configTypeNames.containsKey(dependentTypeName)) {
            return;
        }
        dependentTypeNames.computeIfAbsent(typeName, ignored -> new TreeSet<>()).add(dependentTypeName);
    }

    private void assignTreePages() {
        for (var root : resolver.roots()) {
            root.visit((node, ignored) -> {
                if (!isPageProducing(node)) {
                    if (node instanceof CmPathNode pathNode && pathNode.children().isEmpty()) {
                        LOGGER.log(Level.WARNING, "Skipping empty synthetic path node: {0}", pathNode.path());
                    }
                    return true;
                }
                var pageKey = resolvePageKey(node);
                pageKeys.put(node, pageKey);
                var page = new PageBuilder(Kind.CONFIG, null, null, node.path(), null);
                var resolvedPage = pageBuilders.computeIfAbsent(pageKey, key -> page);
                if (resolvedPage.kind == Kind.CONFIG) {
                    resolvedPage.nodes.add(node);
                }
                if (resolvedPage.isSynthetic() && node.types().size() > 1) {
                    for (var type : node.types()) {
                        resolvedPage.mergedTypeNames.add(type.typeName());
                    }
                }
                addUsage(node, resolvedPage);
                return true;
            }, null);
        }
    }

    private void addUsage(CmNode node, PageBuilder page) {
        var parent = node.parent().orElse(null);
        if (parent == null) {
            page.rawUsages.putIfAbsent(node.path(), new RawUsage(node, node.path(), null));
            return;
        }
        var parentPageKey = pageKeys.get(parent);
        if (parentPageKey != null) {
            page.rawUsages.putIfAbsent(node.path(), new RawUsage(node, node.path(), parentPageKey));
        }
    }

    private void assignTypeNames() {
        for (var page : pageBuilders.values()) {
            if (!page.isSynthetic()) {
                names.reserveTypeName(page.typeName);
            }
        }
        var pages = new ArrayList<PageBuilder>();
        for (var page : pageBuilders.values()) {
            if (page.isSynthetic()) {
                pages.add(page);
            }
        }
        pages.sort(Comparator.comparing(it -> it.path));
        for (var page : pages) {
            page.typeName = names.syntheticTypeName(page.path);
        }
    }

    private void assignFileNames() {
        var pages = new ArrayList<>(pageBuilders.values());
        pages.sort(Comparator.comparing(it -> it.typeName));
        for (var page : pages) {
            page.fileName = names.fileName(page.typeName, fileExt);
        }
    }

    private void buildRows() {
        for (var page : pageBuilders.values()) {
            switch (page.kind) {
                case CONFIG -> {
                    if (page.isSynthetic()) {
                        var node = page.nodes.getFirst();
                        page.description = page.mergedTypeNames.isEmpty()
                                ? pathDescription(page.path)
                                : mergedDescription(page.path);
                        for (var child : node.children()) {
                            page.rows.add(row(page, child));
                        }
                        break;
                    }
                    page.description = description(page.configType);
                    page.rowNode = page.nodes.stream()
                            .filter(this::hasSegmentedChildren)
                            .min(Comparator.comparing(CmNode::path))
                            .orElse(null);
                    if (page.rowNode == null) {
                        for (var option : page.configType.options()) {
                            page.rows.add(new RowBuilder(
                                    option.key().orElseThrow(),
                                    page.typeName,
                                    optionType(option),
                                    option.defaultValue().orElse(""),
                                    description(option),
                                    option.deprecated(),
                                    option.experimental(),
                                    realTargetPageKey(option)));
                        }
                        break;
                    }
                    for (var child : page.rowNode.children()) {
                        page.rows.add(row(page, child));
                    }
                }
                case CONTRACT -> {
                    for (var cmTypes : resolver.providers(page.typeName).values()) {
                        for (var type : cmTypes) {
                            page.rows.add(new RowBuilder(
                                    type.prefix().orElseThrow(),
                                    type.typeName(),
                                    "",
                                    "",
                                    description(type),
                                    false,
                                    false,
                                    configPageKey(type.typeName())));
                        }
                    }
                }
                case ENUM -> {
                    for (var value : page.enumType.values()) {
                        page.rows.add(new RowBuilder(
                                value.value(),
                                page.typeName + ":" + value.value(),
                                "",
                                "",
                                value.description(),
                                false,
                                false,
                                null));
                    }
                }
                default -> throw new IllegalStateException("Unexpected page: " + page.kind);
            }
        }
    }

    private CmPage buildRootPage() {
        var rows = resolver.roots().stream()
                .filter(this::isPageProducing)
                .map(this::rootRow)
                .toList();
        var tables = new Tables(
                table(rows),
                table(List.of()),
                table(List.of()));
        return new CmPage(
                Kind.ROOT,
                ROOT_PAGE_KEY,
                "Config Reference",
                rootPageName,
                null,
                tables,
                Map.of(),
                Map.of(),
                List.of());
    }

    private Row rootRow(CmNode node) {
        var targetPageKey = pageKey(node);
        var targetPage = page(targetPageKey);
        return new Row(
                node.key(),
                "",
                "",
                rootDescription(node),
                targetPage.fileName,
                names.anchor(rootPageName, node.key(), node.identity()));
    }

    private void resolveLinks() {
        for (var page : pageBuilders.values()) {
            for (var typeName : page.mergedTypeNames) {
                var mergedPage = page(configPageKey(typeName));
                page.mergedTypes.put(typeName, mergedPage.fileName);
            }
            for (var typeName : page.dependentTypeNames) {
                var dependentPage = page(configPageKey(typeName));
                page.dependentTypes.put(typeName, dependentPage.fileName);
            }
            for (var row : page.rows) {
                if (row.targetPageKey == null) {
                    continue;
                }
                var targetPage = page(row.targetPageKey);
                row.fileName = targetPage.fileName;
                if (page.kind == Kind.CONFIG) {
                    row.anchor = names.anchor(page.fileName, row.key, row.identity);
                }
            }
            for (var usage : page.rawUsages.values()) {
                if (usage.pageKey == null) {
                    var anchor = names.anchor(rootPageName, usage.node.key(), usage.node.identity());
                    page.usages.add(new Usage(usage.path, rootPageName, anchor));
                } else {
                    var targetPage = page(usage.pageKey);
                    var anchor = names.anchor(targetPage.fileName, usage.node.key(), targetPage.rowIdentity(usage.node));
                    page.usages.add(new Usage(usage.path, targetPage.fileName, anchor));
                }
            }
        }
    }

    private void resolveManifests() {
        for (var page : pageBuilders.values()) {
            switch (page.kind) {
                case CONFIG -> {
                    if (page.configType != null) {
                        configTypes.put(page.typeName, page.fileName);
                    } else {
                        syntheticTypes.put(page.typeName, page.fileName);
                    }
                }
                case CONTRACT -> contracts.put(page.typeName, page.fileName);
                case ENUM -> enumTypes.put(page.typeName, page.fileName);
                default -> {
                    // no-op
                }
            }
        }
    }

    private RowBuilder row(PageBuilder page, CmNode node) {
        return switch (node) {
            case CmPathNode pathNode -> new RowBuilder(
                    pathNode.key(),
                    page.rowIdentity(pathNode),
                    "",
                    "",
                    pathDescription(pathNode.key()),
                    false,
                    false,
                    pageKeys.get(pathNode));
            case CmOptionNode optionNode -> new RowBuilder(
                    optionNode.key(),
                    page.rowIdentity(optionNode),
                    optionType(optionNode.option()),
                    optionNode.option().defaultValue().orElse(""),
                    description(optionNode.option()),
                    optionNode.option().deprecated(),
                    optionNode.option().experimental(),
                    pageKeys.get(optionNode));
        };
    }

    private String realTargetPageKey(CmOption option) {
        if (option.provider() && contractTypeNames.contains(option.typeName())) {
            return contractPageKey(option.typeName());
        }
        if (enumsByName.containsKey(option.typeName())) {
            return enumPageKey(option.typeName());
        }
        if (configTypeNames.containsKey(option.typeName())) {
            return configPageKey(option.typeName());
        }
        return null;
    }

    private String resolvePageKey(CmNode node) {
        if (node instanceof CmOptionNode optionNode) {
            var option = optionNode.option();
            if (option.provider() && contractTypeNames.contains(option.typeName())) {
                return contractPageKey(option.typeName());
            }
            if (enumsByName.containsKey(option.typeName())) {
                return enumPageKey(option.typeName());
            }
        }
        if (node.types().size() > 1) {
            return configPageKey(node.path());
        }
        if (node.types().size() == 1) {
            return configPageKey(node.types().getFirst().typeName());
        }
        if (node instanceof CmOptionNode optionNode && configTypeNames.containsKey(optionNode.option().typeName())) {
            return configPageKey(optionNode.option().typeName());
        }
        return configPageKey(node.path());
    }

    private boolean hasSegmentedChildren(CmNode node) {
        for (var child : node.children()) {
            if (child instanceof CmPathNode
                || child instanceof CmOptionNode optionNode
                   && !child.key().equals(optionNode.option().key().orElseThrow())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPageProducing(CmNode node) {
        return switch (node) {
            case CmPathNode pathNode -> !pathNode.children().isEmpty();
            case CmOptionNode optionNode -> {
                var option = optionNode.option();
                var typeName = option.typeName();
                yield !optionNode.types().isEmpty()
                      || !optionNode.children().isEmpty()
                      || option.provider() && contractTypeNames.contains(typeName)
                      || enumsByName.containsKey(typeName)
                      || configTypeNames.containsKey(typeName);
            }
        };
    }

    private String description(CmType type) {
        var description = type.description().orElse(null);
        if (description == null || description.isBlank()) {
            LOGGER.log(Level.WARNING, "Type does not have a description: {0}", type.typeName());
            return N_A;
        }
        return description;
    }

    private String description(CmOption option) {
        var description = option.description().orElse(null);
        if (description == null || description.isBlank()) {
            LOGGER.log(Level.WARNING, "Option does not have a description: {0}", option.key().orElse("<merge>"));
            return N_A;
        }
        return description;
    }

    private String pageKey(CmNode node) {
        var pageKey = pageKeys.get(node);
        if (pageKey == null) {
            throw new IllegalStateException("Missing page key: " + node.path());
        }
        return pageKey;
    }

    private PageBuilder page(String pageKey) {
        var page = pageBuilders.get(pageKey);
        if (page == null) {
            throw new IllegalStateException("Missing page: " + pageKey);
        }
        return page;
    }

    private static Tables tables(List<RowBuilder> rows) {
        var standard = new ArrayList<Row>();
        var experimental = new ArrayList<Row>();
        var deprecated = new ArrayList<Row>();
        for (var row : rows) {
            var pageRow = row.build();
            if (row.deprecated) {
                deprecated.add(pageRow);
            } else if (row.experimental) {
                experimental.add(pageRow);
            } else {
                standard.add(pageRow);
            }
        }
        return new Tables(table(standard), table(experimental), table(deprecated));
    }

    private static Table table(List<Row> rows) {
        return new Table(rows,
                rows.stream().anyMatch(row -> !row.type().isEmpty()),
                rows.stream().anyMatch(row -> !row.defaultValue().isEmpty()));
    }

    private static String rootDescription(CmNode node) {
        return switch (node) {
            case CmPathNode pathNode -> pathDescription(pathNode.key());
            case CmOptionNode optionNode -> optionNode.option().description().orElse(N_A);
        };
    }

    private static String mergedDescription(String key) {
        return "Merged configuration for <code>%s</code>".formatted(key);
    }

    private static String pathDescription(String key) {
        return "Configuration for <code>%s</code>".formatted(key);
    }

    private static String optionType(CmOption option) {
        var typeName = option.simpleTypeName();
        return switch (option.kind()) {
            case VALUE -> typeName;
            case LIST -> "List<" + typeName + ">";
            case MAP -> "Map<String, " + typeName + ">";
        };
    }

    private static String configPageKey(String id) {
        return "config/" + id;
    }

    private static String contractPageKey(String typeName) {
        return "contract/" + typeName;
    }

    private static String enumPageKey(String typeName) {
        return "enum/" + typeName;
    }

    private static String[] join(String name, String... names) {
        var array = new String[names.length + 1];
        array[0] = name;
        System.arraycopy(names, 0, array, 1, names.length);
        return array;
    }

    private static final class PageBuilder {
        private final Kind kind;
        private final CmType configType;
        private final CmEnum enumType;
        private final String path;
        private final List<CmNode> nodes = new ArrayList<>();
        private final List<RowBuilder> rows = new ArrayList<>();
        private final List<Usage> usages = new ArrayList<>();
        private final Map<String, RawUsage> rawUsages = new LinkedHashMap<>();
        private final Set<String> mergedTypeNames = new TreeSet<>();
        private final Set<String> dependentTypeNames = new TreeSet<>();
        private final Map<String, String> mergedTypes = new TreeMap<>();
        private final Map<String, String> dependentTypes = new TreeMap<>();
        private String typeName;
        private String fileName;
        private String description;
        private CmNode rowNode;

        PageBuilder(Kind kind, CmType configType, CmEnum enumType, String path, String typeName) {
            this.kind = kind;
            this.configType = configType;
            this.enumType = enumType;
            this.path = path;
            this.typeName = typeName;
        }

        boolean isSynthetic() {
            return kind == Kind.CONFIG && configType == null;
        }

        String rowIdentity(CmNode node) {
            return isSynthetic() ? node.identity() : typeName;
        }

        CmPage build() {
            var key = switch (kind) {
                case CONFIG -> configPageKey(isSynthetic() ? path : typeName);
                case CONTRACT -> contractPageKey(typeName);
                case ENUM -> enumPageKey(typeName);
                case ROOT -> ROOT_PAGE_KEY;
            };
            return new CmPage(
                    kind,
                    key,
                    typeName,
                    fileName,
                    description,
                    tables(rows),
                    mergedTypes,
                    dependentTypes,
                    usages);
        }
    }

    private static final class RowBuilder {
        private final String key;
        private final String identity;
        private final String type;
        private final String defaultValue;
        private final String description;
        private final boolean deprecated;
        private final boolean experimental;
        private final String targetPageKey;
        private String fileName = "";
        private String anchor = "";

        RowBuilder(String key,
                   String identity,
                   String type,
                   String defaultValue,
                   String description,
                   boolean deprecated,
                   boolean experimental,
                   String targetPageKey) {
            this.key = key;
            this.identity = identity;
            this.type = type;
            this.defaultValue = defaultValue;
            this.description = description;
            this.deprecated = deprecated;
            this.experimental = experimental;
            this.targetPageKey = targetPageKey;
        }

        Row build() {
            return new Row(
                    key,
                    type,
                    defaultValue,
                    description,
                    fileName,
                    anchor);
        }
    }

    private record RawUsage(CmNode node, String path, String pageKey) {
    }
}
