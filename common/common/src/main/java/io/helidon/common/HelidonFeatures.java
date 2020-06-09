/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Helidon Features support.
 * <p>
 * A Helidon feature is added by its package name. In this version, only Helidon internal features are supported.
 * <p>
 * All registered features can be printed using {@link #print(HelidonFlavor, String, boolean)} as a simple line such as:
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
    private static final Logger LOGGER = Logger.getLogger(HelidonFeatures.class.getName());
    private static final AtomicBoolean PRINTED = new AtomicBoolean();
    private static final AtomicReference<HelidonFlavor> CURRENT_FLAVOR = new AtomicReference<>();
    private static final Map<HelidonFlavor, Set<FeatureDescriptor>> FEATURES = new EnumMap<>(HelidonFlavor.class);
    private static final Map<HelidonFlavor, Map<String, Node>> ROOT_FEATURE_NODES = new EnumMap<>(HelidonFlavor.class);
    private static final List<FeatureDescriptor> ALL_FEATURES = new LinkedList<>();

    private HelidonFeatures() {
    }

    private static void register(FeatureDescriptor featureDescriptor) {
        for (HelidonFlavor flavor : featureDescriptor.flavors()) {

            String[] path = featureDescriptor.path();

            // all root features for a flavor
            if (path.length == 1) {
                FEATURES.computeIfAbsent(flavor, key -> new TreeSet<>(Comparator.comparing(FeatureDescriptor::name)))
                        .add(featureDescriptor);
            }
            var rootFeatures = ROOT_FEATURE_NODES.computeIfAbsent(flavor, it -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
            Node node = ensureNode(rootFeatures, path);
            node.descriptor(featureDescriptor);
        }
        ALL_FEATURES.add(featureDescriptor);
    }

    static Node ensureNode(Map<String, Node> rootFeatureNodes, String... path) {
        // last part of the path is the name
        if (path.length == 1) {
            return rootFeatureNodes.computeIfAbsent(path[0], Node::new);
        }
        // we have a path, let's go through it

        // start with root
        Node lastNode = ensureNode(rootFeatureNodes, path[0]);
        for (int i = 1; i < path.length; i++) {
            String pathElement = path[i];
            lastNode = ensureNode(pathElement, lastNode);
        }

        return lastNode;
    }

    static Node ensureNode(String name, Node parent) {
        return parent.children.computeIfAbsent(name, it -> new Node(name));
    }

    // testing only
    static Map<String, Node> rootFeatureNodes(HelidonFlavor flavor) {
        return ROOT_FEATURE_NODES.computeIfAbsent(flavor, it -> new HashMap<>());
    }

    /**
     * Print features for the current flavor.
     * If {@link #flavor(HelidonFlavor)} is called, this method
     * would only print the list if it matches the flavor provided.
     * This is to make sure we do not print SE flavors in MP, and at the
     * same time can have this method used from Web Server.
     * This method only prints feature the first time it is called.
     *
     * @param flavor flavor to print features for
     * @param version version of Helidon
     * @param details set to {@code true} to print the tree structure of sub-features
     */
    public static void print(HelidonFlavor flavor, String version, boolean details) {
        // print features in another thread, so we do not block the main thread of the application (to reduce startup time)
        new Thread(() -> features(flavor, version, details), "features-thread")
                .start();
    }

    private static void features(HelidonFlavor flavor, String version, boolean details) {
        CURRENT_FLAVOR.compareAndSet(null, HelidonFlavor.SE);

        HelidonFlavor currentFlavor = CURRENT_FLAVOR.get();

        if (currentFlavor != flavor) {
            return;
        }

        if (!PRINTED.compareAndSet(false, true)) {
            return;
        }

        scan();

        Set<FeatureDescriptor> features = FEATURES.get(currentFlavor);
        if (null == features) {
            LOGGER.info("Helidon " + currentFlavor + " " + version + " has no registered features");
        } else {
            String featureString = "[" + features.stream()
                    .map(FeatureDescriptor::name)
                    .collect(Collectors.joining(", "))
                    + "]";
            LOGGER.info("Helidon " + currentFlavor + " " + version + " features: " + featureString);
        }
        if (details) {
            LOGGER.info("Detailed feature tree:");
            FEATURES.get(currentFlavor)
                    .forEach(feature -> printDetails(feature.name(),
                                                     ROOT_FEATURE_NODES.get(currentFlavor).get(feature.path()[0]),
                                                     0));
        }
    }

    private static void scan() {
        // scan all packages for features
        // warn if in native image
        // this is the place to add support for package annotations in the future
        Package[] packages = Package.getPackages();

        for (Package aPackage : packages) {
            String packageName = aPackage.getName();

            Set<FeatureDescriptor> featureDescriptors = FeatureCatalog.get(packageName);
            if (featureDescriptors == null) {
                if (packageName.startsWith("io.helidon.")) {
                    LOGGER.warning("No catalog entry for package " + packageName);
                }
            } else {
                featureDescriptors.forEach(HelidonFeatures::register);
            }
        }

        if (NativeImageHelper.isRuntime()) {
            // make sure we warn about all features
            ALL_FEATURES.sort(Comparator.comparing(FeatureDescriptor::name));
            for (FeatureDescriptor feature : ALL_FEATURES) {
                if (feature.nativeSupported()) {
                    String desc = feature.nativeDescription();
                    if (desc != null && !desc.isBlank()) {
                        LOGGER.warning("Native image for feature "
                                               + feature.name()
                                               + "("
                                               + feature.stringPath()
                                               + "): "
                                               + desc);
                    }
                } else {
                    LOGGER.severe("You are using a feature not supported in native image: "
                                          + feature.name()
                                          + "("
                                          + feature.stringPath()
                                          + ")");
                }
            }
        }
    }

    private static void printDetails(String name, Node node, int level) {

        FeatureDescriptor feat = node.descriptor;
        if (feat == null) {
            System.out.println("  ".repeat(level) + name);
        } else {
            String prefix = " ".repeat(level * 2);
            // start on index 10 or a tab spaces after tree
            int len = prefix.length() + name.length();
            String suffix;
            if (len <= 8) {
                suffix = " ".repeat(10 - len);
            } else {
                suffix = "\t";
            }
            String nativeDesc = "";
            if (!feat.nativeSupported()) {
                nativeDesc = " (NOT SUPPORTED in native image)";
            } else {
                if (!feat.nativeDescription().isBlank()) {
                    nativeDesc = " (Native image: " + feat.nativeDescription() + ")";
                }
            }
            System.out.println(prefix + name + suffix + feat.description() + nativeDesc);
        }

        node.children.forEach((childName, childNode) -> {
            FeatureDescriptor descriptor = childNode.descriptor;
            String actualName;
            if (descriptor == null) {
                actualName = childName;
            } else {
                actualName = descriptor.name();
            }
            printDetails(actualName, childNode, level + 1);
        });
    }

    /**
     * Set the current Helidon flavor. Features will only be printed for the
     * flavor configured.
     *
     * The first flavor configured wins.
     *
     * @param flavor current flavor
     */
    public static void flavor(HelidonFlavor flavor) {
        CURRENT_FLAVOR.compareAndSet(null, flavor);
    }

    static final class Node {
        private final Map<String, Node> children = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private final String name;
        private FeatureDescriptor descriptor;

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

        void descriptor(FeatureDescriptor featureDescriptor) {
            this.descriptor = featureDescriptor;
        }
    }

}
