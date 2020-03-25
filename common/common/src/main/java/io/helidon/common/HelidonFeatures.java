/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import io.helidon.common.spi.HelidonFeatureProvider;

/**
 * Helidon Features support.
 * <p>
 * A feature can register using either {@link #register(HelidonFlavor, String...)} or {@link #register(String...)} methods
 * on this class, or using the {@link io.helidon.common.spi.HelidonFeatureProvider} service loader interface.
 * <p>
 * All registered features can be printed using {@link #print(HelidonFlavor, boolean)} as a simple line such as:
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
    private static final Map<HelidonFlavor, Set<String>> FEATURES = new EnumMap<>(HelidonFlavor.class);
    private static final Map<HelidonFlavor, Map<String, Node>> ROOT_FEATURE_NODES = new EnumMap<>(HelidonFlavor.class);

    private HelidonFeatures() {
    }

    /**
     * Register a feature for a flavor.
     * This should be called from a static initializer of a feature
     * class. In SE this would be one of the *Support classes or similar,
     * in MP most likely a CDI extension class.
     *
     * <p>Example for security providers (SE) - application with Oidc provider:
     * <ul>
     *     <li>Security calls {@code register(SE, "security")}</li>
     *     <li>OIDC provider calls {@code register(SE, "security", "authentication", "OIDC"}</li>
     *     <li>OIDC provider calls {@code register(SE, "security", "outbound", "OIDC"} if outbound is enabled</li>
     * </ul>
     *
     * @param flavor flavor to register a feature for
     * @param path path of the feature (single value for root level features)
     */
    public static void register(HelidonFlavor flavor, String... path) {
        if (path.length == 0) {
            throw new IllegalArgumentException("At least the root feature name must be provided, but path was empty");
        }
        if (path.length == 1) {
            FEATURES.computeIfAbsent(flavor, key -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))
                    .add(path[0]);
        }

        var rootFeatures = ROOT_FEATURE_NODES.computeIfAbsent(flavor, it -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        ensureNode(rootFeatures, path);
    }

    /**
     * Register a feature for all flavors.
     *
     * <p>Example for security providers (SE) - application with Oidc provider:
     * <ul>
     *     <li>Security calls {@code register(SE, "security")}</li>
     *     <li>OIDC provider calls {@code register("security", "authentication", "OIDC"}</li>
     *     <li>OIDC provider calls {@code register("security", "outbound", "OIDC"} if outbound is enabled</li>
     * </ul>
     *
     * @param path path of the feature (single value for root level features)
     */
    public static void register(String... path) {
        for (HelidonFlavor value : HelidonFlavor.values()) {
            register(value, path);
        }
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
     * @param details set to {@code true} to print the tree structure of sub-features
     */
    public static void print(HelidonFlavor flavor, boolean details) {
        // First scan all features
        ServiceLoader.load(HelidonFeatureProvider.class)
                .forEach(HelidonFeatureProvider::register);

        CURRENT_FLAVOR.compareAndSet(null, HelidonFlavor.SE);

        HelidonFlavor currentFlavor = CURRENT_FLAVOR.get();

        if (currentFlavor != flavor) {
            return;
        }

        if (!PRINTED.compareAndSet(false, true)) {
            return;
        }
        Set<String> strings = FEATURES.get(currentFlavor);
        if (null == strings) {
            LOGGER.info("Helidon " + currentFlavor + " " + Version.VERSION + " has no registered features");
        } else {
            LOGGER.info("Helidon " + currentFlavor + " " + Version.VERSION + " features: " + strings);
        }
        if (details) {
            LOGGER.info("Detailed feature tree:");
            FEATURES.get(currentFlavor)
                    .forEach(name -> printDetails(name, ROOT_FEATURE_NODES.get(currentFlavor).get(name), 0));
        }
    }

    private static void printDetails(String name, Node node, int level) {

        System.out.println("  ".repeat(level) + name);

        node.children.forEach((childName, childNode) -> printDetails(childName, childNode, level + 1));
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
    }
}
