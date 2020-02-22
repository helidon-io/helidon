/*
 * Copyright (c) 2019-2020 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;

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
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Specialized constructor for building {@code Node} objects that include properties of type {@code Map}
 * that also correspond to specific domain types.
 * <p>
 * Left to its default implementation, SnakeYAML would create a node corresponding to a {@code Map} instead of the correct
 * specific type. These methods intercept those attempts and, instead, build nodes that correspond to the appropriate specific
 * type (if any).
 * </p>
 */
class CustomConstructor extends Constructor {

    private static final Map<Class<?>, Class<?>> CHILD_MAP_TYPES = new HashMap<>();
    private static final Map<Class<?>, Class<?>> CHILD_MAP_OF_LIST_TYPES = new HashMap<>();

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
        super.constructMapping2ndStep(node, mapping);
    }
}
