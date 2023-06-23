/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.callbacks.Callback;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.security.SecurityRequirement;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Specialized SnakeYAML constructor for modifying {@code Node} objects for OpenAPI types needing special attention.
 * <p>
 *     Several MP OpenAPI types resemble maps with strings for keys and various child types as values. Such interfaces
 *     expose an {@code addX} method, where X is the child type (e.g., {@link Paths} exposes {@link Paths#addPathItem}.
 *     SnakeYAML parsing, left to itself, would incorrectly attempt to use the string keys as property names in converting OpenAPI
 *     documents to and from the in-memory POJO model. To prevent that, this custom constructor takes over the job of
 *     creating these parent instances and populating the children from the SnakeYAML node graph.
 * </p>
 */
final class CustomConstructor extends Constructor {

    // OpenAPI interfaces which resemble Map<?, type>, linked to info used to prepare the type description for that type where
    // the mapped-to type is NOT a list. For typing reasons (in ExpandedTypeDescription$MapLikeTypeDescription#create)
    // we provide type-specific factory functions as part of the type metadata here where we can specify the actual parent
    // and child types.
    static final Map<Class<?>, ChildMapType<?, ?>> CHILD_MAP_TYPES = Map.of(
            APIResponses.class, new ChildMapType<>(APIResponses.class,
                                                   APIResponse.class,
                                                   APIResponses::addAPIResponse,
                                                   impl -> ExpandedTypeDescription.MapLikeTypeDescription.create(
                                                           APIResponses.class,
                                                           impl,
                                                           APIResponse.class,
                                                           APIResponses::addAPIResponse)),
            Callback.class, new ChildMapType<>(Callback.class,
                                               PathItem.class,
                                               Callback::addPathItem,
                                               impl -> ExpandedTypeDescription.MapLikeTypeDescription.create(
                                                       Callback.class,
                                                       impl,
                                                       PathItem.class,
                                                       Callback::addPathItem)),
            Content.class, new ChildMapType<>(Content.class,
                                              MediaType.class,
                                              Content::addMediaType,
                                              impl -> ExpandedTypeDescription.MapLikeTypeDescription.create(
                                                      Content.class,
                                                      impl,
                                                      MediaType.class,
                                                      Content::addMediaType)),
            Paths.class, new ChildMapType<>(Paths.class,
                                            PathItem.class,
                                            Paths::addPathItem,
                                            impl -> ExpandedTypeDescription.MapLikeTypeDescription.create(
                                                    Paths.class,
                                                    impl,
                                                    PathItem.class,
                                                    Paths::addPathItem)));

    // OpenAPI interfaces which resemble Map<?, List<type>>, linked to info used to prepare the type description for that type
    // where the mapped-to type IS a list.
    static final Map<Class<?>, ChildMapListType<?, ?>> CHILD_MAP_OF_LIST_TYPES = Map.of(
            SecurityRequirement.class, new ChildMapListType<>(SecurityRequirement.class,
                                                              String.class,
                                                              SecurityRequirement::addScheme,
                                                              SecurityRequirement::addScheme,
                                                              SecurityRequirement::addScheme,
                                                              impl -> ExpandedTypeDescription.ListMapLikeTypeDescription.create(
                                                                      SecurityRequirement.class,
                                                                      impl,
                                                                      String.class,
                                                                      SecurityRequirement::addScheme,
                                                                      SecurityRequirement::addScheme,
                                                                      SecurityRequirement::addScheme)));

    /**
     * Adds a single named child to the parent.
     *
     * @param <P> parent type
     * @param <C> child type
     */
    @FunctionalInterface
    interface ChildAdder<P, C> {
        Object addChild(P parent, String name, C child);
    }

    /**
     * Adds a list of children to the parent.
     *
     * @param <P> parent type
     * @param <C> child type
     */
    @FunctionalInterface
    interface ChildListAdder<P, C> {
        Object addChildren(P parent, String name, List<C> children);
    }

    /**
     * Adds a valueless child name to the parent.
     *
     * @param <P> parent type
     */
    @FunctionalInterface
    interface ChildNameAdder<P> {
        P addChild(P parent, String name);
    }

    /**
     * Type information about a map-resembling interface.
     *
     * @param <P> parent type
     * @param <C> child type
     */
    record ChildMapType<P, C>(Class<P> parentType,
                              Class<C> childType,
                              ChildAdder<P, C> childAdder,
                              Function<Class<?>, ExpandedTypeDescription.MapLikeTypeDescription<P, C>> typeDescriptionFactory) { }

    /**
     * Type information about a map-resembling interface in which a child can have 0, 1, or more values (i.e., the child is
     * a list).
     *
     * @param <P> parent type
     * @param <C> child type
     */
    record ChildMapListType<P, C>(
            Class<P> parentType,
            Class<C> childType,
            ChildAdder<P, C> childAdder,
            ChildListAdder<P, C> childListAdder,
            ChildNameAdder<P> childNameAdder,
            Function<Class<?>, ExpandedTypeDescription.ListMapLikeTypeDescription<P, C>> typeDescriptionFunction) { }

    private static final System.Logger LOGGER = System.getLogger(CustomConstructor.class.getName());

    CustomConstructor(TypeDescription td) {
        super(td, new LoaderOptions());
        yamlClassConstructors.put(NodeId.mapping, new ConstructMapping());
    }

    @Override
    protected void constructMapping2ndStep(MappingNode node, Map<Object, Object> mapping) {
        Class<?> parentType = node.getType();
        if (CHILD_MAP_TYPES.containsKey(parentType)) {
            Class<?> childType = CHILD_MAP_TYPES.get(parentType).childType;
            node.getValue().forEach(tuple -> {
                Node valueNode = tuple.getValueNode();
                if (valueNode.getType() == Object.class) {
                    valueNode.setType(childType);
                }
            });
        } else if (CHILD_MAP_OF_LIST_TYPES.containsKey(parentType)) {
            Class<?> childType = CHILD_MAP_OF_LIST_TYPES.get(parentType).childType;
            node.getValue().forEach(tuple -> {
                Node valueNode = tuple.getValueNode();
                if (valueNode.getNodeId() == NodeId.sequence) {
                    SequenceNode seqNode = (SequenceNode) valueNode;
                    seqNode.setListType(childType);
                }
            });
        }

        // Older releases silently accepted numbers for APIResponse status values; they should be strings.
        if (parentType.equals(APIResponses.class)) {
            convertIntHttpStatuses(node);
        }
        super.constructMapping2ndStep(node, mapping);
    }

    private void convertIntHttpStatuses(MappingNode node) {
        List<Mark> numericHttpStatusMarks = new ArrayList<>();
        node.getValue().forEach(t -> {
            Node keyNode = t.getKeyNode();
            if (keyNode.getTag().equals(Tag.INT)) {
                numericHttpStatusMarks.add(keyNode.getStartMark());
                keyNode.setTag(Tag.STR);
            }
        });
        if (!numericHttpStatusMarks.isEmpty()) {
            LOGGER.log(Level.WARNING,
                       "Numeric HTTP status value(s) should be quoted. "
                               + "Please change the following; unquoted numeric values might be rejected in a future release: "
                               + "{0}",
                       numericHttpStatusMarks);
        }
    }

    /**
     * Override of SnakeYAML logic which constructs an object from a node.
     * <p>
     *     This class makes sure that parent/child relationships that resemble maps are handled correctly and defers to the
     *     superclass implementation in other cases.
     * </p>
     */
    class ConstructMapping extends Constructor.ConstructMapping {

        @Override
        public Object construct(Node node) {
            MappingNode mappingNode = (MappingNode) node;
            Class<?> parentType = mappingNode.getType();
            Map<String, Object> extensions = new HashMap<>();

            // If the element has extension properties, SnakeYAML will have prepared a MappingNode even if
            // the node type is actually a scalar, for example.
            if (Extensible.class.isAssignableFrom(parentType)) {
                // Save the extension property names and values, remove the corresponding child nodes,
                // let SnakeYAML process the adjusted node, then set the saved extension properties.
                var allTuples = mappingNode.getValue();
                List<NodeTuple> extensionTuples = new ArrayList<>();
                allTuples.forEach(tuple -> {
                    String name = ((ScalarNode) tuple.getKeyNode()).getValue();
                    if (name.startsWith("x-")) {
                        extensionTuples.add(tuple);
                        // Extension values can be scalars, sequences, or maps. Using constructObject here will create the
                        // correct value type.
                        Node valueNode = tuple.getValueNode();
                        Object value;
                        if (valueNode.getTag().equals(Tag.STR)) {
                            value = constructScalar((ScalarNode) valueNode);
                        } else if (valueNode.getTag().equals(Tag.SEQ)) {
                            value = constructSequence((SequenceNode) valueNode);
                        } else if (valueNode.getTag().equals(Tag.MAP)) {
                            value = constructMapping((MappingNode) valueNode);
                        } else {
                            value = constructObject(valueNode);
                        }
                        extensions.put(name, value);
                    }
                });
                allTuples.removeAll(extensionTuples);
            }

            Object result;
            if (CHILD_MAP_TYPES.containsKey(parentType) || CHILD_MAP_OF_LIST_TYPES.containsKey(parentType)) {
                // Following is inspired by SnakeYAML Constructor$ConstructMapping#construct
                // and Constructor#ConstructSequence#construct.
                if (mappingNode.isTwoStepsConstruction()) {
                    result = newMap(mappingNode);
                } else {
                    result = constructMapping(mappingNode);
                }
            } else {
                result = super.construct(mappingNode);
            }

            if (!extensions.isEmpty()) {
                ((Extensible<?>) result).setExtensions(extensions);
            }
            return result;
        }
    }
}
