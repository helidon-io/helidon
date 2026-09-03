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

package io.helidon.openapi.v30;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OpenApiOperationIdValidator {
    private static final Set<String> OPERATION_FIELDS = Set.of("get",
                                                               "put",
                                                               "post",
                                                               "delete",
                                                               "options",
                                                               "head",
                                                               "patch",
                                                               "trace",
                                                               "query");

    private final OpenApiReferenceResolver resolver;
    private final IdentityHashMap<Map<String, Object>, Node> pathItems = new IdentityHashMap<>();
    private final IdentityHashMap<Map<String, Object>, Node> callbacks = new IdentityHashMap<>();
    private final List<Node> nodes = new ArrayList<>();
    private final List<Root> roots = new ArrayList<>();
    private final ArrayDeque<Node> pending = new ArrayDeque<>();
    private final Map<String, Location> operationIds = new LinkedHashMap<>();

    private OpenApiOperationIdValidator(Map<String, Object> document) {
        resolver = OpenApiReferenceResolver.create(document);
    }

    static void validate(Map<String, Object> document) {
        OpenApiOperationIdValidator validator = new OpenApiOperationIdValidator(document);
        validator.addPathItemRoots("paths", object(document.get("paths")), true);
        validator.addPathItemRoots("webhooks", object(document.get("webhooks")), false);
        validator.buildGraph();
        validator.assignComponents();
        validator.validateGraph();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private void addPathItemRoots(String location,
                                  Map<String, Object> pathItemValues,
                                  boolean skipExtensions) {
        pathItemValues.forEach((name, value) -> {
            if (skipExtensions && name.startsWith("x-")) {
                return;
            }
            Node node = node(Kind.PATH_ITEM, object(value));
            if (node != null) {
                roots.add(new Root(node, Location.root(location + "." + name)));
            }
        });
    }

    private Node node(Kind kind, Map<String, Object> value) {
        if (value.isEmpty()) {
            return null;
        }
        IdentityHashMap<Map<String, Object>, Node> indexedNodes = kind == Kind.PATH_ITEM ? pathItems : callbacks;
        Node result = indexedNodes.get(value);
        if (result == null) {
            result = new Node(nodes.size(), kind, value);
            indexedNodes.put(value, result);
            nodes.add(result);
            pending.addLast(result);
        }
        return result;
    }

    private void addEdge(Node source, Node target, String segment) {
        if (target == null) {
            return;
        }
        Edge edge = new Edge(source, target, segment);
        source.outgoing.add(edge);
        target.incoming.add(edge);
    }

    private void buildGraph() {
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (node.kind == Kind.PATH_ITEM) {
                describePathItem(node);
            } else {
                describeCallback(node);
            }
        }
    }

    private void describePathItem(Node node) {
        node.value.forEach((field, value) -> {
            if (OPERATION_FIELDS.contains(field)) {
                describeOperation(node, "." + field, object(value));
            }
        });
        object(node.value.get("additionalOperations")).forEach((method, operation) -> describeOperation(
                node,
                ".additionalOperations." + method,
                object(operation)));
        OpenApiReferenceResolver.Resolution resolution = resolver.resolveReference(node.value);
        if (resolution.status() == OpenApiReferenceResolver.Status.RESOLVED
                && resolution.value() != node.value) {
            addEdge(node, node(Kind.PATH_ITEM, resolution.value()), ".$ref");
        }
    }

    private void describeOperation(Node pathItem, String segment, Map<String, Object> operation) {
        if (operation.isEmpty()) {
            return;
        }
        if (operation.get("operationId") instanceof String operationId) {
            pathItem.operations.add(new Operation(operationId, segment));
        }
        object(operation.get("callbacks")).forEach((name, callback) -> addEdge(
                pathItem,
                node(Kind.CALLBACK, object(callback)),
                segment + ".callbacks." + name));
    }

    private void describeCallback(Node node) {
        if (node.value.get("$ref") instanceof String) {
            OpenApiReferenceResolver.Resolution resolution = resolver.resolveReference(node.value);
            if (resolution.status() == OpenApiReferenceResolver.Status.RESOLVED
                    && resolution.value() != node.value) {
                addEdge(node, node(Kind.CALLBACK, resolution.value()), ".$ref");
            }
            return;
        }
        node.value.forEach((expression, pathItem) -> {
            if (!expression.startsWith("x-")) {
                addEdge(node, node(Kind.PATH_ITEM, object(pathItem)), "." + expression);
            }
        });
    }

    private void assignComponents() {
        List<Node> finished = finishOrder();
        boolean[] assigned = new boolean[nodes.size()];
        for (int i = finished.size() - 1; i >= 0; i--) {
            Node start = finished.get(i);
            if (assigned[start.id]) {
                continue;
            }
            Component component = new Component();
            ArrayDeque<Node> stack = new ArrayDeque<>();
            assigned[start.id] = true;
            stack.addLast(start);
            while (!stack.isEmpty()) {
                Node node = stack.removeLast();
                node.component = component;
                for (Edge edge : node.incoming) {
                    Node source = edge.source;
                    if (!assigned[source.id]) {
                        assigned[source.id] = true;
                        stack.addLast(source);
                    }
                }
            }
        }
    }

    private List<Node> finishOrder() {
        boolean[] visited = new boolean[nodes.size()];
        List<Node> result = new ArrayList<>(nodes.size());
        for (Node start : nodes) {
            if (visited[start.id]) {
                continue;
            }
            ArrayDeque<Frame> stack = new ArrayDeque<>();
            visited[start.id] = true;
            stack.addLast(new Frame(start));
            while (!stack.isEmpty()) {
                Frame frame = stack.getLast();
                if (frame.nextEdge < frame.node.outgoing.size()) {
                    Node target = frame.node.outgoing.get(frame.nextEdge++).target;
                    if (!visited[target.id]) {
                        visited[target.id] = true;
                        stack.addLast(new Frame(target));
                    }
                } else {
                    result.add(stack.removeLast().node);
                }
            }
        }
        return result;
    }

    private void validateGraph() {
        ArrayDeque<ComponentOccurrence> occurrences = new ArrayDeque<>();
        roots.forEach(root -> occurrences.addLast(new ComponentOccurrence(root.node.component,
                                                                          root.node,
                                                                          root.location)));
        while (!occurrences.isEmpty()) {
            ComponentOccurrence occurrence = occurrences.removeFirst();
            if (occurrence.component.visits == 2) {
                continue;
            }
            occurrence.component.visits++;
            validateComponent(occurrence, occurrences);
        }
    }

    private void validateComponent(ComponentOccurrence occurrence,
                                   ArrayDeque<ComponentOccurrence> occurrences) {
        IdentityHashMap<Node, Location> locations = new IdentityHashMap<>();
        ArrayDeque<NodeLocation> componentNodes = new ArrayDeque<>();
        locations.put(occurrence.entry, occurrence.location);
        componentNodes.addLast(new NodeLocation(occurrence.entry, occurrence.location));
        while (!componentNodes.isEmpty()) {
            NodeLocation current = componentNodes.removeFirst();
            for (Operation operation : current.node.operations) {
                validateOperationId(operation.id, current.location.child(operation.segment));
            }
            for (Edge edge : current.node.outgoing) {
                Location targetLocation = current.location.child(edge.segment);
                if (edge.target.component == occurrence.component) {
                    if (!locations.containsKey(edge.target)) {
                        locations.put(edge.target, targetLocation);
                        componentNodes.addLast(new NodeLocation(edge.target, targetLocation));
                    }
                } else {
                    occurrences.addLast(new ComponentOccurrence(edge.target.component,
                                                                edge.target,
                                                                targetLocation));
                }
            }
        }
    }

    private void validateOperationId(String operationId, Location location) {
        Location previousLocation = operationIds.putIfAbsent(operationId, location);
        if (previousLocation != null) {
            throw new IllegalStateException("Duplicate OpenAPI operationId " + operationId
                                                    + " at " + previousLocation.text()
                                                    + " and " + location.text());
        }
    }

    private enum Kind {
        PATH_ITEM,
        CALLBACK
    }

    private static final class Node {
        private final int id;
        private final Kind kind;
        private final Map<String, Object> value;
        private final List<Operation> operations = new ArrayList<>();
        private final List<Edge> outgoing = new ArrayList<>();
        private final List<Edge> incoming = new ArrayList<>();
        private Component component;

        private Node(int id, Kind kind, Map<String, Object> value) {
            this.id = id;
            this.kind = kind;
            this.value = value;
        }
    }

    private static final class Component {
        private int visits;
    }

    private static final class Edge {
        private final Node source;
        private final Node target;
        private final String segment;

        private Edge(Node source, Node target, String segment) {
            this.source = source;
            this.target = target;
            this.segment = segment;
        }
    }

    private static final class Operation {
        private final String id;
        private final String segment;

        private Operation(String id, String segment) {
            this.id = id;
            this.segment = segment;
        }
    }

    private static final class Root {
        private final Node node;
        private final Location location;

        private Root(Node node, Location location) {
            this.node = node;
            this.location = location;
        }
    }

    private static final class Frame {
        private final Node node;
        private int nextEdge;

        private Frame(Node node) {
            this.node = node;
        }
    }

    private static final class ComponentOccurrence {
        private final Component component;
        private final Node entry;
        private final Location location;

        private ComponentOccurrence(Component component, Node entry, Location location) {
            this.component = component;
            this.entry = entry;
            this.location = location;
        }
    }

    private static final class NodeLocation {
        private final Node node;
        private final Location location;

        private NodeLocation(Node node, Location location) {
            this.node = node;
            this.location = location;
        }
    }

    private static final class Location {
        private final Location parent;
        private final String segment;

        private Location(Location parent, String segment) {
            this.parent = parent;
            this.segment = segment;
        }

        private static Location root(String value) {
            return new Location(null, value);
        }

        private Location child(String value) {
            return new Location(this, value);
        }

        private String text() {
            ArrayDeque<String> segments = new ArrayDeque<>();
            for (Location current = this; current != null; current = current.parent) {
                segments.addFirst(current.segment);
            }
            StringBuilder result = new StringBuilder();
            segments.forEach(result::append);
            return result.toString();
        }
    }
}
