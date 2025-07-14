/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.features;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.common.NativeImageHelper;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.metadata.Aot;
import io.helidon.common.features.metadata.Deprecation;
import io.helidon.common.features.metadata.FeatureMetadata;
import io.helidon.common.features.metadata.FeatureStatus;
import io.helidon.common.features.metadata.Flavor;

/**
 * Helidon Features support.
 * <p>
 * A Helidon feature is added by its package name. In this version, only Helidon internal features are supported.
 * <p>
 * All registered features can be printed using {@link #print(HelidonFlavor, String, boolean)} as a simple line
 * such as:
 * <br>
 * {@code Helidon MP 2.0.0 features: [CDI, Config, JAX-RS, JPA, JTA, Server]}
 * <br>
 * Using this class' logger.
 * <p>
 * When details are enabled, an additional log statement is logged, such as:
 * <pre>
 * Detailed feature tree:
 * CDI
 * Config
 *   YAML
 * JAX-RS
 * JPA
 *   Hibernate
 * JTA
 * Server
 * </pre>
 */
public final class HelidonFeatures {
    static final AtomicBoolean PRINTED = new AtomicBoolean();
    static final AtomicReference<HelidonFlavor> CURRENT_FLAVOR = new AtomicReference<>();
    private static final System.Logger LOGGER = System.getLogger(HelidonFeatures.class.getName());
    private static final System.Logger INCUBATING = System.getLogger(HelidonFeatures.class.getName() + ".incubating");
    private static final System.Logger PREVIEW = System.getLogger(HelidonFeatures.class.getName() + ".preview");
    private static final System.Logger DEPRECATED = System.getLogger(HelidonFeatures.class.getName() + ".deprecated");
    private static final System.Logger INVALID = System.getLogger(HelidonFeatures.class.getName() + ".invalid");
    private static final AtomicBoolean SCANNED = new AtomicBoolean();
    private static final Map<HelidonFlavor, Set<FeatureMetadata>> FEATURES = new EnumMap<>(HelidonFlavor.class);
    private static final Map<HelidonFlavor, Map<String, Node>> ROOT_FEATURE_NODES = new EnumMap<>(HelidonFlavor.class);
    private static final List<FeatureMetadata> ALL_FEATURES = new LinkedList<>();

    private HelidonFeatures() {
    }

    /**
     * Print features for the current flavor.
     * If {@link #flavor(HelidonFlavor)} is called, this method
     * would only print the list if it matches the flavor provided.
     * This is to make sure we do not print SE flavors in MP, and at the
     * same time can have this method used from Web Server.
     * This method only prints feature the first time it is called.
     *
     * @param flavor  flavor to print features for
     * @param version version of Helidon
     * @param details set to {@code true} to print the tree structure of sub-features
     */
    public static void print(HelidonFlavor flavor, String version, boolean details) {
        // print features in another thread, so we do not block the main thread of the application (to reduce startup time)
        new Thread(() -> features(flavor, version, details), "features-thread")
                .start();
    }

    /**
     * Will scan all features and log errors and warnings for features that have a
     * native image limitation.
     * This method is automatically called when building a native image with Helidon.
     *
     * @param classLoader to look for features in
     */
    public static void nativeBuildTime(ClassLoader classLoader) {
        scan(classLoader);
        for (FeatureMetadata feat : ALL_FEATURES) {
            if (feat.aot().isPresent()) {
                Aot aot = feat.aot().get();
                if (!aot.supported()) {
                    LOGGER.log(Level.ERROR, "Feature '" + feat.name()
                            + "' for path '" + feat.stringPath()
                            + "' IS NOT SUPPORTED in native image. Image may still build and run.");
                }
                if (aot.description().isPresent()) {
                    LOGGER.log(Level.WARNING, "Feature '" + feat.name()
                            + "' for path '" + feat.stringPath()
                            + "' has limited support in native image: " + aot.description().get());
                }
            }
        }
    }

    /**
     * Set the current Helidon flavor. Features will only be printed for the
     * flavor configured.
     * <p>
     * The first flavor configured wins.
     *
     * @param flavor current flavor
     */
    public static void flavor(HelidonFlavor flavor) {
        CURRENT_FLAVOR.compareAndSet(null, flavor);
    }

    static Node ensureNode(Map<String, Node> rootFeatureNodes, List<String> path) {
        // last part of the path is the name
        if (path.size() == 1) {
            return rootFeatureNodes.computeIfAbsent(path.getFirst(), Node::new);
        }
        // we have a path, let's go through it

        // start with root
        Node lastNode = ensureNode(rootFeatureNodes, List.of(path.getFirst()));
        for (int i = 1; i < path.size(); i++) {
            String pathElement = path.get(i);
            lastNode = ensureNode(pathElement, lastNode);
        }

        return lastNode;
    }

    static Node ensureNode(String name, Node parent) {
        return parent.children.computeIfAbsent(name, it -> new Node(name));
    }

    private static void register(FeatureMetadata featureDescriptor) {
        for (Flavor metadataFlavor : featureDescriptor.flavors()) {
            HelidonFlavor flavor = HelidonFlavor.map(metadataFlavor);
            List<String> path = featureDescriptor.path();

            // all root features for a flavor
            if (path.size() == 1) {
                FEATURES.computeIfAbsent(flavor, key -> new TreeSet<>(Comparator.comparing(FeatureMetadata::name)))
                        .add(featureDescriptor);
            }
            var rootFeatures = ROOT_FEATURE_NODES.computeIfAbsent(flavor, it -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
            Node node = ensureNode(rootFeatures, path);
            node.descriptor(featureDescriptor);
        }
        ALL_FEATURES.add(featureDescriptor);
    }

    static void features(HelidonFlavor flavor, String version, boolean details) {
        CURRENT_FLAVOR.compareAndSet(null, HelidonFlavor.SE);

        HelidonFlavor currentFlavor = CURRENT_FLAVOR.get();

        if (currentFlavor != flavor) {
            return;
        }

        if (!PRINTED.compareAndSet(false, true)) {
            return;
        }

        scan(Thread.currentThread().getContextClassLoader());

        Set<FeatureMetadata> features = FEATURES.get(currentFlavor);
        if (null == features) {
            LOGGER.log(Level.INFO, "Helidon " + currentFlavor + " " + version + " has no registered features");
        } else {
            String featureString = "[" + features.stream()
                    .map(FeatureMetadata::name)
                    .collect(Collectors.joining(", "))
                    + "]";
            LOGGER.log(Level.INFO, "Helidon " + currentFlavor + " " + version + " features: " + featureString);
        }

        List<FeatureMetadata> invalidFeatures = ALL_FEATURES.stream()
                .filter(feature -> feature.invalidFlavors().contains(currentFlavor))
                .collect(Collectors.toList());

        if (!invalidFeatures.isEmpty()) {
            INVALID.log(Level.WARNING, "Invalid modules are used:");
            invalidFeatures.forEach(HelidonFeatures::logInvalid);
        }

        if (details) {
            LOGGER.log(Level.INFO, "Detailed feature tree:");
            if (FEATURES.containsKey(currentFlavor)) {
                FEATURES.get(currentFlavor)
                        .forEach(feature -> printDetails(feature.name(),
                                                         ROOT_FEATURE_NODES.get(currentFlavor).get(feature.path().getFirst()),
                                                         0));
            }
        } else {
            List<FeatureMetadata> allIncubating = new ArrayList<>();
            List<FeatureMetadata> allDeprecated = new ArrayList<>();
            List<FeatureMetadata> allPreview = new ArrayList<>();

            if (ROOT_FEATURE_NODES.containsKey(currentFlavor)) {
                if (FEATURES.containsKey(currentFlavor)) {
                    FEATURES.get(currentFlavor)
                            .forEach(feature -> {
                                gatherIncubating(allIncubating,
                                                 ROOT_FEATURE_NODES.get(currentFlavor).get(feature.path().getFirst()));
                                gatherDeprecated(allDeprecated,
                                                 ROOT_FEATURE_NODES.get(currentFlavor).get(feature.path().getFirst()));
                                gatherPreview(allPreview,
                                              ROOT_FEATURE_NODES.get(currentFlavor).get(feature.path().getFirst()));
                            });
                }
            }

            if (!allIncubating.isEmpty()) {
                INCUBATING.log(Level.WARNING,
                               "You are using incubating features. These APIs are not production ready!");
                allIncubating
                        .forEach(it -> INCUBATING.log(Level.INFO,
                                                      "\tIncubating feature: "
                                                              + it.name()
                                                              + it.since().map(since -> " since " + since).orElse("")
                                                              + " ("
                                                              + it.stringPath()
                                                              + ")"));
            }
            if (!allDeprecated.isEmpty()) {
                DEPRECATED.log(Level.WARNING,
                               "You are using deprecated features. These APIs will be removed from Helidon!");
                allDeprecated
                        .forEach(it -> DEPRECATED.log(Level.INFO,
                                                      "\tDeprecated feature: "
                                                              + it.name()
                                                              + " since "
                                                              + it.deprecation().flatMap(Deprecation::since).orElse("?")
                                                              + " ("
                                                              + it.stringPath()
                                                              + ")"));
            }
            if (!allPreview.isEmpty()) {
                PREVIEW.log(Level.INFO,
                            "You are using preview features. These APIs are production ready, yet may change more "
                                    + "frequently. Please follow Helidon release changelog!");
                allPreview
                        .forEach(it -> PREVIEW.log(Level.INFO,
                                                   "\tPreview feature: "
                                                           + it.name()
                                                           + it.since().map(since -> " since " + since).orElse("")
                                                           + " ("
                                                           + it.stringPath()
                                                           + ")"));
            }
        }
    }

    private static void logInvalid(FeatureMetadata feature) {
        INVALID.log(Level.WARNING, "\tModule \""
                + feature.module() + "\" (" + feature.stringPath() + ")"
                + " is not designed for Helidon "
                + CURRENT_FLAVOR.get()
                + ", it should only be used in Helidon " + feature.flavors());
    }

    private static void gatherIncubating(List<FeatureMetadata> allIncubating, Node node) {
        if (node.descriptor != null && node.descriptor.status() == FeatureStatus.INCUBATING) {
            allIncubating.add(node.descriptor);
        }
        node.children().values().forEach(it -> gatherIncubating(allIncubating, it));
    }

    private static void gatherPreview(List<FeatureMetadata> allPreview, Node node) {
        if (node.descriptor != null && node.descriptor.status() == FeatureStatus.PREVIEW) {
            allPreview.add(node.descriptor);
        }
        node.children().values().forEach(it -> gatherPreview(allPreview, it));
    }

    private static void gatherDeprecated(List<FeatureMetadata> allDeprecated, Node node) {
        if (node.descriptor != null && node.descriptor.status() == FeatureStatus.DEPRECATED) {
            allDeprecated.add(node.descriptor);
        }
        node.children().values().forEach(it -> gatherDeprecated(allDeprecated, it));
    }

    private static void scan(ClassLoader classLoader) {
        if (!SCANNED.compareAndSet(false, true)) {
            // already scanned
            return;
        }
        // warn if in native image
        FeatureCatalog.features(classLoader).forEach(HelidonFeatures::register);

        if (NativeImageHelper.isRuntime()) {
            // make sure we warn about all features
            for (FeatureMetadata feature : ALL_FEATURES) {
                Optional<Aot> aot = feature.aot();
                if (aot.isEmpty()) {
                    continue;
                }

                String desc = aot.get().description().orElse(null);
                boolean supported = aot.get().supported();

                if (supported) {
                    if (desc != null && !desc.isBlank()) {
                        LOGGER.log(Level.WARNING, "Native image for feature "
                                + feature.name()
                                + "("
                                + feature.stringPath()
                                + "): "
                                + desc);
                    }
                } else {
                    if (desc == null || desc.isBlank()) {
                        LOGGER.log(Level.ERROR, "You are using a feature not supported in native image: "
                                + feature.name()
                                + "("
                                + feature.stringPath()
                                + ")");
                    } else {
                        LOGGER.log(Level.ERROR, "You are using a feature not supported in native image: "
                                + feature.name()
                                + "("
                                + feature.stringPath()
                                + "): "
                                + desc);
                    }
                }
            }
        }
    }

    private static void printDetails(String name, Node node, int level) {

        FeatureMetadata feat = node.descriptor;
        if (feat == null) {
            System.out.println("  ".repeat(level) + name);
        } else {
            String prefix = " ".repeat(level * 2);
            // start on index 10 or a tab spaces after tree
            int len = prefix.length() + name.length();
            String suffix;
            if (len <= 18) {
                suffix = " ".repeat(20 - len);
            } else {
                suffix = "\t";
            }
            String preview = feat.status() == FeatureStatus.PREVIEW ? "Preview - " : "";
            String incubating = feat.status() == FeatureStatus.INCUBATING ? "Incubating - " : "";
            String deprecated = feat.status() == FeatureStatus.DEPRECATED ? "Deprecated since "
                    + feat.deprecation().flatMap(Deprecation::since).orElse("?") + " - " : "";

            String nativeDesc = "";
            if (feat.aot().isPresent()) {
                var aot = feat.aot().get();
                if (!aot.supported()) {
                    nativeDesc = " (NOT SUPPORTED in native image)";
                } else {
                    if (aot.description().isPresent()) {
                        nativeDesc = " (Native image: " + aot.description().get() + ")";
                    }
                }
            }

            System.out.println(
                    prefix
                            + name
                            + suffix
                            + deprecated
                            + incubating
                            + preview
                            + feat.description().orElseGet(feat::name)
                            + nativeDesc);
        }

        node.children.forEach((childName, childNode) -> {
            FeatureMetadata descriptor = childNode.descriptor;
            String actualName;
            if (descriptor == null) {
                actualName = childName;
            } else {
                actualName = descriptor.name();
            }
            printDetails(actualName, childNode, level + 1);
        });
    }

    static final class Node {
        private final Map<String, Node> children = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private final String name;
        private FeatureMetadata descriptor;

        Node(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }

        // for tests
        Map<String, Node> children() {
            return children;
        }

        void descriptor(FeatureMetadata featureDescriptor) {
            this.descriptor = featureDescriptor;
        }
    }
}
