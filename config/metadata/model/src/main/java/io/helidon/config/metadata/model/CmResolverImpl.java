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
package io.helidon.config.metadata.model;

import java.lang.System.Logger.Level;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.config.metadata.model.CmModel.CmAllowedValue;
import io.helidon.config.metadata.model.CmModel.CmEnum;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;
import io.helidon.config.metadata.model.CmModelImpl.CmTypeImpl;
import io.helidon.config.metadata.model.CmNode.CmOptionNode;
import io.helidon.config.metadata.model.CmNodeImpl.CmOptionNodeImpl;
import io.helidon.config.metadata.model.CmNodeImpl.CmPathNodeImpl;

import static io.helidon.config.metadata.model.CmModel.CmOption.DEFAULT_TYPE;
import static java.util.function.Predicate.not;

/**
 * Config metadata resolver.
 */
final class CmResolverImpl implements CmResolver {

    private static final System.Logger LOGGER =
            System.getLogger(CmResolverImpl.class.getName());

    private final CmModel metadata;
    private final Map<String, List<CmType>> prefixes = new TreeMap<>();
    private final Map<String, CmType> types = new HashMap<>();
    private final Map<String, CmType> resolvedTypes = new TreeMap<>();
    private final Map<String, Map<String, List<CmType>>> providers = new TreeMap<>();
    private final Set<String> contracts = new TreeSet<>();
    private final Set<String> errors = new HashSet<>();
    private final Map<String, CmEnum> enums = new TreeMap<>();
    private final Map<String, Set<CmNode>> usages = new HashMap<>();
    private final List<CmNode> tree = new ArrayList<>();

    CmResolverImpl(CmModel metadata) {
        this.metadata = metadata;
        initTypes();
        resolveTypes();
        initTree();
    }

    @Override
    public Optional<CmType> type(String typeName) {
        return Optional.ofNullable(resolvedTypes.get(typeName));
    }

    @Override
    public List<String> contracts() {
        return List.copyOf(contracts);
    }

    @Override
    public Set<CmNode> usage(String typeName) {
        return Collections.unmodifiableSet(usages.getOrDefault(typeName, Set.of()));
    }

    @Override
    public Map<String, List<CmType>> providers(String contractTypeName) {
        return providers.getOrDefault(contractTypeName, Map.of());
    }

    @Override
    public List<CmNode> roots() {
        return List.copyOf(tree);
    }

    @Override
    public List<CmType> types() {
        return List.copyOf(resolvedTypes.values());
    }

    @Override
    public List<CmEnum> enums() {
        return List.copyOf(enums.values());
    }

    @Override
    public boolean isKnownType(String typeName) {
        return resolvedTypes.containsKey(typeName)
                || enums.containsKey(typeName)
                || contracts.contains(typeName);
    }

    private void initTypes() {
        for (var module : metadata.modules()) {
            for (var type : module.types()) {
                types.put(type.typeName(), type);
                initContracts(type);
            }
        }
    }

    private void initContracts(CmType type) {
        for (var option : type.options()) {
            if (!option.provider()) {
                continue;
            }
            var contractTypeName = option.typeName();
            if (!DEFAULT_TYPE.equals(contractTypeName)) {
                contracts.add(contractTypeName);
            }
        }
    }

    private void resolveTypes() {
        var providerTypes = new TreeMap<String, List<CmType>>();
        for (var entry : types.entrySet()) {
            var typeName = entry.getKey();
            var type = resolveType(entry.getValue());
            for (var provide : type.provides()) {
                providerTypes.computeIfAbsent(provide, k -> new ArrayList<>()).add(type);
            }
            if (type.standalone()) {
                var prefix = type.prefix().orElse(null);
                if (prefix != null) {
                    prefixes.computeIfAbsent(prefix, k -> new ArrayList<>()).add(type);
                } else {
                    LOGGER.log(Level.WARNING, "Standalone type does not have a prefix: {0}", type.typeName());
                }
            }
            resolvedTypes.put(typeName, type);
        }
        initProviders(providerTypes);
    }

    private void initProviders(Map<String, List<CmType>> providerTypes) {
        for (var entry : providerTypes.entrySet()) {
            var grouped = new TreeMap<String, List<CmType>>();
            for (var impl : entry.getValue()) {
                var typeName = impl.typeName();
                var key = impl.prefix().orElse(null);
                if (key == null) {
                    LOGGER.log(Level.WARNING,
                            "Provider type does not have a prefix: {0}",
                            typeName);
                } else {
                    grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(impl);
                }
            }
            var sorted = new TreeMap<String, List<CmType>>();
            for (var providerEntry : grouped.entrySet()) {
                var values = providerEntry.getValue();
                values.sort(CmType::compareTo);
                sorted.put(providerEntry.getKey(), List.copyOf(values));
            }
            providers.put(entry.getKey(), Collections.unmodifiableMap(sorted));
        }
    }

    private void initTree() {
        // read-write root node
        var root = new Slot("", "");

        // split dotted root prefixes
        for (var e : prefixes.entrySet()) {
            addPrefix(root, e.getKey(), e.getValue());
        }

        // traverse all types
        initTree(root);

        // build read-only tree
        for (var entry : root.children.values()) {
            tree.add(entry.build());
        }

        // record usages
        for (var rootNode : tree) {
            rootNode.visit((node, ignored) -> {
                for (var type : node.types()) {
                    usage(usages, type.typeName(), node);
                }
                if (node instanceof CmOptionNode optionNode) {
                    var option = optionNode.option();
                    var optionTypeName = option.typeName();
                    var optionType = optionNode.type().orElse(null);
                    if (option.provider()) {
                        if (!DEFAULT_TYPE.equals(optionTypeName)) {
                            usage(usages, optionTypeName, optionNode);
                        }
                    } else if (optionType != null || enums.containsKey(optionTypeName)) {
                        usage(usages, optionTypeName, optionNode);
                    }
                }
                return true;
            }, null);
        }
    }

    private void addPrefix(Slot root, String prefix, List<CmType> exactTypes) {
        var slot = materializePath(root, prefix, "config prefix " + prefix);
        if (slot == root) {
            throw new IllegalArgumentException(
                    "Config prefix does not contain a valid path segment: " + prefix);
        }
        slot.types.addAll(exactTypes);
    }

    private Slot materializePath(Slot parent, String path, String source) {
        var current = parent;
        for (var segment : path.split("\\.")) {
            if (segment.isBlank()) {
                continue;
            }
            if (!hasValidPathCharacters(segment)) {
                error("invalid-path-segment:" + source + ":" + segment,
                        "Skipping invalid config path segment {0} in {1}",
                        segment,
                        source);
                continue;
            }
            var child = current.children.get(segment);
            if (child == null) {
                child = new Slot(path(current.path, segment), segment);
                current.children.put(segment, child);
            }
            current = child;
        }
        return current;
    }

    private void initTree(Slot root) {
        // depth-first traversal of TypeContext
        var stack = new ArrayDeque<TypeContext>();

        // traverse the root to populate the stack
        root.visit(node -> {
            for (var it : node.types.descendingSet()) {
                stack.push(new TypeContext(node, it, Set.of(it.typeName())));
            }
        });

        while (!stack.isEmpty()) {
            var e = stack.pop();
            for (var option : e.type.options()) {
                var optionKey = option.key().orElse(null);
                if (optionKey == null) {
                    LOGGER.log(Level.WARNING,
                            "Type contains an option without key: {0}",
                            e.type.typeName());
                    continue;
                }
                var optionTypeName = option.typeName();
                var optionType = type(optionTypeName).orElse(null);
                var slot = materializePath(e.node, optionKey, "option key " + e.type.typeName() + "#" + optionKey);
                if (slot == e.node) {
                    error("option-no-path:" + e.type.typeName() + "#" + optionKey,
                            "Option key does not contain a valid path segment: {0}#{1}",
                            e.type.typeName(),
                            optionKey);
                    continue;
                }

                // merge option
                if (slot.option != null) {
                    if (!matches(slot.option, slot.optionType, optionTypeName, optionType, option.provider())) {
                        if (matches(slot.option, optionTypeName, option.provider()) && slot.optionType == null) {
                            slot.option = option;
                            slot.optionType = optionType;
                        } else {
                            error("option-conflict:" + slot.path,
                                    "Conflicting option metadata for path {0}: keeping {1}, ignoring {2}",
                                    slot.path,
                                    slot.option.typeName(),
                                    optionTypeName);
                        }
                    }
                } else {
                    slot.option = option;
                    slot.optionType = optionType;
                }

                // handle provider
                if (option.provider()) {
                    if (DEFAULT_TYPE.equals(optionTypeName)) {
                        error("provider-no-type:" + e.type.typeName() + "#" + optionKey,
                                "Provider option without type: {0}#{1}",
                                e.type.typeName(),
                                optionKey);
                        continue;
                    }
                    var providers = providers(optionTypeName);
                    if (providers.isEmpty()) {
                        error("provider-no-impl:" + optionTypeName,
                                "Provider contract does not have implementations: {0}",
                                optionTypeName);
                        continue;
                    }

                    // add provider
                    for (var entry : providers.entrySet()) {
                        var node = slot.children.get(entry.getKey());
                        if (node == null) {
                            node = new Slot(path(slot.path, entry.getKey()), entry.getKey());
                            slot.children.put(entry.getKey(), node);
                        }
                        for (var type : entry.getValue()) {
                            if (node.types.add(type)) {
                                var next = e.next(node, type);
                                if (next != null) {
                                    stack.push(next);
                                }
                            }
                        }
                    }
                } else if (optionType != null) {
                    var next = e.next(slot, optionType);
                    if (next != null) {
                        stack.push(next);
                    }
                }
            }
        }
    }

    private CmType resolveType(CmType type) {
        var hierarchy = new ArrayList<CmType>();
        for (var inheritedTypeName : type.inherits()) {
            var inheritedType = types.get(inheritedTypeName);
            if (inheritedType == null) {
                LOGGER.log(Level.WARNING,
                        "Cannot resolve inherited type of {0}: {1}",
                        type.typeName(),
                        inheritedTypeName);
            } else {
                hierarchy.addFirst(inheritedType);
            }
        }
        hierarchy.addLast(type);

        var options = new HashMap<String, CmOption>();
        for (var currentType : hierarchy) {
            for (var option : resolveOptions(currentType)) {
                var key = option.key().orElse(null);
                if (key != null) {
                    options.put(key, resolveOption(type, option));
                } else {
                    LOGGER.log(Level.WARNING,
                            "Type contains an option without key: {0}",
                            currentType.typeName());
                }
            }
        }

        return new CmTypeImpl(type.typeName(),
                List.copyOf(options.values()),
                type.description(),
                type.prefix(),
                type.standalone(),
                List.of(),
                type.provides());
    }

    private List<CmOption> resolveOptions(CmType type) {
        var merged = new ArrayList<CmOption>();
        var stack = new ArrayDeque<>(type.options());
        while (!stack.isEmpty()) {
            var option = stack.pop();
            if (option.merge()) {
                var optionTypeName = option.typeName();
                var resolvedType = types.get(optionTypeName);
                if (resolvedType != null) {
                    var options = resolvedType.options();
                    for (int i = options.size() - 1; i >= 0; i--) {
                        stack.push(options.get(i));
                    }
                } else {
                    merged.add(option);
                    LOGGER.log(Level.WARNING,
                            "Cannot resolve merge option type: {0}",
                            option.typeName());
                }
            } else {
                merged.add(option);
            }
        }
        return merged;
    }

    private CmOption resolveOption(CmType type, CmOption option) {
        var optionType = option.typeName();
        var optionKey = option.key().orElseThrow();
        var defaultValue = option.defaultValue().orElse(null);
        if (defaultValue != null) {
            try {
                parseValue(optionType, defaultValue);
            } catch (NumberFormatException ex) {
                LOGGER.log(Level.WARNING,
                        "Unable to parse {0} as {1}",
                        defaultValue,
                        optionType);
            }
        }
        var allowedValues = option.allowedValues();
        if (!allowedValues.isEmpty()) {
            if (isSyntheticEnum(option)) {
                optionType = syntheticTypeName(type.typeName(), optionKey);
                enums.put(optionType, CmEnum.of(optionType, allowedValues));
            } else {
                enums.putIfAbsent(optionType, CmEnum.of(optionType, allowedValues));
            }
        }
        var description = option.description().orElse(null);
        return CmOption.builder()
                .key(optionKey)
                .type(optionType)
                .kind(option.kind())
                .description(description)
                .defaultValue(defaultValue)
                .merge(option.merge())
                .deprecated(option.deprecated())
                .experimental(option.experimental())
                .required(option.required())
                .provider(option.provider())
                .build();
    }

    private boolean isSyntheticEnum(CmOption option) {
        var typeName = option.typeName();
        if (typeName.startsWith("java.lang")) {
            return true;
        }
        var existing = enums.get(typeName);
        if (existing == null) {
            return false;
        }
        var expected = existing.values().stream()
                .map(CmAllowedValue::value)
                .collect(Collectors.toSet());
        var actual = option.allowedValues().stream()
                .map(CmAllowedValue::value)
                .collect(Collectors.toSet());
        return !actual.equals(expected);
    }

    private void error(String key, String message, Object... args) {
        if (errors.add(key)) {
            LOGGER.log(Level.WARNING, message, args);
        }
    }

    private static void usage(Map<String, Set<CmNode>> usages, String typeName, CmNode node) {
        usages.computeIfAbsent(typeName, ignored -> new TreeSet<>(Comparator.comparing(CmNode::path)
                .thenComparing(CmNode::key))).add(node);
    }

    private static String path(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }

    private static boolean hasValidPathCharacters(String segment) {
        return segment.chars().anyMatch(ch -> ch != '*');
    }

    private static boolean matches(CmOption option, String typeName, boolean provider) {
        return option.typeName().equals(typeName) && option.provider() == provider;
    }

    private static boolean matches(CmOption option, CmType optionType, String typeName, CmType type, boolean provider) {
        return matches(option, typeName, provider) && CmType.matches(optionType, type);
    }

    private static String syntheticTypeName(String typeName, String option) {
        return Arrays.stream(option.split("-"))
                .filter(not(String::isBlank))
                .map(str -> {
                    char c = Character.toUpperCase(str.charAt(0));
                    var capitalized = String.valueOf(c);
                    if (str.length() > 1) {
                        capitalized += str.substring(1);
                    }
                    return capitalized;
                })
                .collect(Collectors.joining("", typeName, ""));
    }

    @SuppressWarnings("UnusedReturnValue")
    private static Object parseValue(String typeName, String defaultValue) {
        return switch (typeName) {
            case "java.lang.Double" -> Double.parseDouble(defaultValue);
            case "java.lang.Boolean" -> Boolean.parseBoolean(defaultValue);
            case "java.lang.Byte" -> Byte.parseByte(defaultValue);
            case "java.lang.Short" -> Short.parseShort(defaultValue);
            case "java.lang.Float" -> Float.parseFloat(defaultValue);
            case "java.lang.Long" -> Long.parseLong(defaultValue);
            case "java.lang.Integer" -> Integer.parseInt(defaultValue);
            default -> null;
        };
    }

    private record TypeContext(Slot node, CmType type, Set<String> typeNames) {

        TypeContext next(Slot slot, CmType type) {
            var typeName = type.typeName();
            if (!typeNames.contains(typeName)) {
                var next = new HashSet<>(typeNames);
                next.add(typeName);
                return new TypeContext(slot, type, next);
            }
            return null;
        }
    }

    private record NodeContext(CmNode node, List<CmNode> children) {
    }

    private static final class Slot {
        private final TreeSet<CmType> types = new TreeSet<>();
        private final TreeMap<String, Slot> children = new TreeMap<>();
        private final String path;
        private final String key;
        private CmOption option;
        private CmType optionType;

        Slot(String path, String key) {
            this.path = path;
            this.key = key;
        }

        void visit(Consumer<Slot> visitor) {
            var stack = new ArrayDeque<Slot>();
            stack.push(this);
            while (!stack.isEmpty()) {
                var e = stack.pop();
                visitor.accept(e);
                for (var child : e.children.descendingMap().values()) {
                    stack.push(child);
                }
            }
        }

        NodeContext build(CmNode parent) {
            var children = new ArrayList<CmNode>();
            if (option != null) {
                var node = new CmOptionNodeImpl(
                        parent,
                        path,
                        key,
                        types,
                        children,
                        option,
                        optionType);
                return new NodeContext(node, children);
            }
            var node = new CmPathNodeImpl(
                    parent,
                    path,
                    key,
                    types,
                    children);
            return new NodeContext(node, children);
        }

        CmNode build() {
            var stack = new ArrayDeque<NodeContext>();
            var root = build(null);
            stack.push(root);
            visit(current -> {
                var view = stack.pop();
                for (var child : current.children.descendingMap().values()) {
                    var ctx = child.build(view.node);
                    stack.push(ctx);
                    view.children.addFirst(ctx.node);
                }
            });
            return root.node;
        }
    }
}
