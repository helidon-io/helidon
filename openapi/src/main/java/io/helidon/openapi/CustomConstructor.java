/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.openapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.callbacks.Callback;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.security.Scopes;
import org.eclipse.microprofile.openapi.models.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.models.servers.ServerVariable;
import org.eclipse.microprofile.openapi.models.servers.ServerVariables;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Specialized SnakeYAML constructor for modifying {@code Node} objects for OpenAPI types that extend {@code Map} to adjust the
 * type of the child nodes of such nodes.
 * <p>
 * Several MicroProfile OpenAPI interfaces extend {@code Map}. For example, {@code Paths} extends {@code Map
 * <String, PathItem>} and {@code SecurityRequirement} extends {@code Map<String, List<String>>}. When SnakeYAML builds the node
 * corresponding to one of these types, it correctly creates each child node as a {@code MappingNode} but it assigns those
 * child nodes a type of {@code Object} instead of the mapped type -- {@code PathItem} in the example above.
 * </p>
 * <p>
 * This class customizes the preparation of the node tree in these situations by setting the types for the child nodes explicitly
 * to the corresponding child type. In OpenAPI 1.1.2 there are two situations, depending on whether the mapped-to type is a
 * {@code List} or not.
 * </p>
 * <p>
 * The MicroProfile OpenAPI 2.0 versions of the interfaces no longer use this construct of an interface extending {@code Map}, so
 * ideally we can remove this workaround when we adopt 2.0.
 * </p>
 */
final class CustomConstructor extends Constructor {

    // maps OpenAPI interfaces which extend Map<?, type> to the mapped-to type where that mapped-to type is NOT List
    private static final Map<Class<?>, Class<?>> CHILD_MAP_TYPES = new HashMap<>();

    // maps OpenAPI interfaces which extend Map<?, List<type>> to the type that appears in the list
    private static final Map<Class<?>, Class<?>> CHILD_MAP_OF_LIST_TYPES = new HashMap<>();

    private static final Logger LOGGER = Logger.getLogger(CustomConstructor.class.getName());

    static {
        CHILD_MAP_TYPES.put(Paths.class, PathItem.class);
        CHILD_MAP_TYPES.put(Callback.class, PathItem.class);
        CHILD_MAP_TYPES.put(Content.class, MediaType.class);
        CHILD_MAP_TYPES.put(APIResponses.class, APIResponse.class);
        CHILD_MAP_TYPES.put(ServerVariables.class, ServerVariable.class);
        CHILD_MAP_TYPES.put(Scopes.class, String.class);
        CHILD_MAP_OF_LIST_TYPES.put(SecurityRequirement.class, String.class);
    }

    CustomConstructor(TypeDescription td) {
        super(td);
    }

    @Override
    protected void constructMapping2ndStep(MappingNode node, Map<Object, Object> mapping) {
        Class<?> parentType = node.getType();
        if (CHILD_MAP_TYPES.containsKey(parentType)) {
            Class<?> childType = CHILD_MAP_TYPES.get(parentType);
            node.getValue().forEach(tuple -> {
                Node valueNode = tuple.getValueNode();
                if (valueNode.getType() == Object.class) {
                    valueNode.setType(childType);
                }
            });
        } else if (CHILD_MAP_OF_LIST_TYPES.containsKey(parentType)) {
            Class<?> childType = CHILD_MAP_OF_LIST_TYPES.get(parentType);
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
                    + "Please change the following; unquoted numeric values might be rejected in a future release: {0}",
                    numericHttpStatusMarks);
        }
    }
}
