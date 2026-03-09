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
import java.util.stream.Collectors;

import io.helidon.config.metadata.model.CmModel.CmAllowedValue;
import io.helidon.config.metadata.model.CmModel.CmEnum;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;
import io.helidon.config.metadata.model.CmModelImpl.CmTypeImpl;

import static java.util.function.Predicate.not;

/**
 * Config metadata resolver.
 */
final class CmResolverImpl implements CmResolver {

    private static final System.Logger LOGGER = System.getLogger(CmResolverImpl.class.getName());

    private final CmModel metadata;
    private final Map<String, List<CmType>> prefixes = new HashMap<>(); // standalone types by prefixes
    private final Map<String, CmType> types = new HashMap<>(); // unresolved types
    private final Map<String, CmType> resolvedTypes = new TreeMap<>(); // resolved types
    private final Map<String, List<CmType>> providers = new TreeMap<>(); // providers by contract
    private final Set<String> errors = new HashSet<>(); // errors logged once
    private final Map<String, CmEnum> enums = new TreeMap<>();
    private final Map<String, Set<CmNode>> usages = new HashMap<>(); // tree nodes by option type
    private final Set<String> unreachableTypes = new HashSet<>();
    private final List<CmNodeImpl> tree = new ArrayList<>();
    private final List<CmNode> readOnlyTree = Collections.unmodifiableList(tree);

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
    public List<CmType> providers(String typeName) {
        return Collections.unmodifiableList(providers.getOrDefault(typeName, List.of()));
    }

    @Override
    public List<String> contracts() {
        return List.copyOf(providers.keySet());
    }

    @Override
    public Set<CmNode> usage(String typeName) {
        return Collections.unmodifiableSet(usages.getOrDefault(typeName, Set.of()));
    }

    @Override
    public List<CmNode> roots() {
        return readOnlyTree;
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
    public boolean isEnum(String typeName) {
        return enums.containsKey(typeName);
    }

    private void initTypes() {
        for (var module : metadata.modules()) {
            for (var type : module.types()) {
                types.put(type.type(), type);
            }
        }
    }

    private void resolveTypes() {
        types.forEach((k, v) -> {
            var type = resolveType(v);
            for (var provide : type.provides()) {
                providers.computeIfAbsent(provide, ignored -> new ArrayList<>()).add(type);
            }
            if (type.standalone()) {
                var prefix = type.prefix().orElse(null);
                if (prefix != null) {
                    prefixes.computeIfAbsent(prefix, ignored -> new ArrayList<>()).add(type);
                } else {
                    LOGGER.log(Level.WARNING, "Standalone type does not have a prefix: {0}", type.type());
                }
            }
            resolvedTypes.put(k, type);
            unreachableTypes.add(type.type());
        });
    }

    private void initTree() {
        for (var entry : prefixes.entrySet()) {
            var prefix = entry.getKey();
            for (var type : entry.getValue()) {
                var typeName = type.type();
                var resolvedType = resolvedTypes.get(typeName);
                var node = new CmNodeImpl(null, prefix, prefix, typeName, resolvedType, new ArrayList<>());
                usage(usages, typeName, node);
                tree.add(node);
            }
        }

        var types = traverse(tree, usages);
        types.forEach(unreachableTypes::remove);

        // process each unreachable type as a root
        // use a local usages map to avoid pollution
        var unreachableUsages = new HashMap<String, Set<CmNode>>();
        for (var typeName : unreachableTypes) {
            var resolvedType = resolvedTypes.get(typeName);
            if (resolvedType != null) {
                var key = resolvedType.prefix().orElse("<?>");
                var node = new CmNodeImpl(null, key, key, typeName, resolvedType, new ArrayList<>());
                types.addAll(traverse(List.of(node), unreachableUsages));
            }
        }
    }

    private List<String> traverse(List<CmNodeImpl> roots, Map<String, Set<CmNode>> usages) {
        // depth-first traversal
        var stack = new ArrayDeque<>(roots);
        var types = new ArrayList<String>();
        while (!stack.isEmpty()) {
            var node = stack.pop();
            var enclosingTypeName = node.typeName();
            types.add(enclosingTypeName);

            // process options
            var options = node.type().map(CmType::options).orElse(List.of());
            for (var i = options.size() - 1; i >= 0; i--) {
                var option = options.get(i);
                var optionTypeName = option.type();
                var optionType = type(optionTypeName).orElse(null);
                var optionKey = option.key().orElse(null);
                if (optionKey == null) {
                    LOGGER.log(Level.WARNING, "Type contains an option without key: {0}", enclosingTypeName);
                    continue;
                }
                if (optionType != null) {
                    types.add(optionTypeName);
                }
                var optionPath = node.path() + "." + optionKey;
                var optionNode = new CmNodeImpl(
                        node,
                        optionPath,
                        optionKey,
                        optionTypeName,
                        optionType,
                        new ArrayList<>());
                node.addChild(optionNode);

                // process provider implementations
                if (option.provider()) {
                    if (optionTypeName.equals(CmOption.DEFAULT_TYPE)) {
                        if (errors.add("provider-no-type: " + optionTypeName)) {
                            LOGGER.log(Level.WARNING, "Provider option without type: {0}#{1}", enclosingTypeName, optionKey);
                        }
                        continue;
                    }
                    var implTypes = providers.computeIfAbsent(optionTypeName, k -> List.of());
                    if (implTypes.isEmpty()) {
                        if (errors.add("provider-no-impl: " + optionTypeName)) {
                            LOGGER.log(Level.WARNING, "Provider contract does not have implementations: {0}", optionTypeName);
                        }
                        continue;
                    }
                    for (var implType : implTypes) {
                        var implTypeName = implType.type();
                        var implKey = implType.prefix().orElse(null);
                        if (implKey == null) {
                            LOGGER.log(Level.WARNING, "Provider type does not have a prefix: {0}", implTypeName);
                            continue;
                        }
                        var implPath = optionPath + "." + implKey;
                        var implNode = new CmNodeImpl(
                                optionNode,
                                implPath,
                                implKey,
                                implTypeName,
                                implType,
                                new ArrayList<>());
                        optionNode.addChild(implNode);
                        usage(usages, implTypeName, implNode);
                        stack.push(implNode);
                        types.add(implTypeName);
                    }
                    usage(usages, optionTypeName, optionNode);
                } else if (optionType != null) {
                    if (!optionTypeName.equals(node.typeName())) {
                        // an option may reference its enclosing type
                        // prevent infinite recursion
                        usage(usages, optionTypeName, optionNode);
                        stack.push(optionNode);
                    }
                } else if (enums.containsKey(optionTypeName)) {
                    usage(usages, optionTypeName, optionNode);
                }
            }
        }
        return types;
    }

    private CmType resolveType(CmType type) {
        // build the reverse hierarchy (parents first)
        var hierarchy = new ArrayList<CmType>();
        for (var e : type.inherits()) {
            var superTypeName = type.type();
            var t = types.get(e);
            if (t == null) {
                LOGGER.log(Level.WARNING, "Cannot resolve inherited type of {0}: {1}", superTypeName, e);
            } else {
                hierarchy.addFirst(t);
            }
        }
        hierarchy.addLast(type);

        // traverse the reverse hierarchy to override options
        var options = new HashMap<String, CmOption>();
        for (var t : hierarchy) {
            for (var e : resolveOptions(t)) {
                var key = e.key().orElse(null);
                if (key != null) {
                    var resolvedOption = resolveOption(type, e);
                    options.put(key, resolvedOption);
                } else {
                    LOGGER.log(Level.WARNING, "Type contains an option without key: {0}", t.type());
                }
            }
        }

        // return a copy with updates
        return new CmTypeImpl(
                type.type(),
                List.copyOf(options.values()),
                type.description(),
                type.prefix(),
                type.standalone(),
                List.of(), // empty inherits
                type.provides());
    }

    private List<CmOption> resolveOptions(CmType type) {
        var merged = new ArrayList<CmOption>();
        var stack = new ArrayDeque<>(type.options());
        while (!stack.isEmpty()) {
            var option = stack.pop();
            if (option.merge()) {
                var optionTypeName = option.type();
                var resolvedType = types.get(optionTypeName);
                if (resolvedType != null) {
                    var options = resolvedType.options();
                    for (int i = options.size() - 1; i >= 0; i--) {
                        stack.push(options.get(i));
                    }
                } else {
                    merged.add(option);
                    LOGGER.log(Level.WARNING, "Cannot resolve merge option type: {0}", option.type());
                }
            } else {
                merged.add(option);
            }
        }
        return merged;
    }

    private CmOption resolveOption(CmType type, CmOption option) {
        validateDefaultValue(option);
        var optionKey = option.key().orElseThrow();
        var optionType = option.type();
        var allowedValues = option.allowedValues();
        if (!allowedValues.isEmpty()) {
            if (isSyntheticEnum(option)) {
                optionType = syntheticTypeName(type.type(), optionKey);
                enums.put(optionType, CmEnum.of(optionType, allowedValues));
            } else {
                enums.putIfAbsent(optionType, CmEnum.of(optionType, allowedValues));
            }
        }
        var description = option.description().orElse(null);
        var defaultValue = option.defaultValue().orElse(null);
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
        var optionTypeName = option.type();
        if (optionTypeName.startsWith("java.lang")) {
            return true;
        } else {
            var existing = enums.get(optionTypeName);
            if (existing != null) {
                var expected = existing.values().stream()
                        .map(CmAllowedValue::value)
                        .collect(Collectors.toSet());
                var actual = option.allowedValues().stream()
                        .map(CmAllowedValue::value)
                        .collect(Collectors.toSet());
                return !actual.equals(expected);
            } else {
                return false;
            }
        }
    }

    private static void usage(Map<String, Set<CmNode>> usages, String typeName, CmNode node) {
        usages.computeIfAbsent(typeName, k -> new TreeSet<>(Comparator.comparing(CmNode::path)))
                .add(node);
    }

    private static void validateDefaultValue(CmOption option) {
        var optionType = option.type();
        var defaultValue = option.defaultValue().orElse(null);
        if (defaultValue != null) {
            try {
                parseValue(optionType, defaultValue);
            } catch (NumberFormatException ex) {
                LOGGER.log(Level.WARNING,
                        "Unable to parse \"%s\" as %s".formatted(defaultValue, optionType));
            }
        }
    }

    private static String syntheticTypeName(String typeName, String option) {
        return Arrays.stream(option.split("-"))
                .filter(not(String::isBlank))
                .map(str -> {
                    char c = Character.toUpperCase(str.charAt(0));
                    var captalized = String.valueOf(c);
                    if (str.length() > 1) {
                        captalized += str.substring(1);
                    }
                    return captalized;
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
}
