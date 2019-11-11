/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

class CustomConstructor extends Constructor {

    private static final Map<Class<?>, Class<?>> childMapTypes = new HashMap<>();
    private static final Map<Class<?>, Class<?>> childMapOfListTypes = new HashMap<>();

    static {
        childMapTypes.put(Paths.class, PathItem.class);
        childMapTypes.put(Callback.class, PathItem.class);
        childMapTypes.put(Content.class, MediaType.class);
        childMapTypes.put(APIResponses.class, APIResponse.class);
        childMapTypes.put(ServerVariables.class, ServerVariable.class);
        childMapTypes.put(Scopes.class, String.class);
        childMapOfListTypes.put(SecurityRequirement.class, String.class);
    }

    CustomConstructor(TypeDescription td) {
        super(td);
    }

    @Override
    protected void constructMapping2ndStep(MappingNode node, Map<Object, Object> mapping) {
        Class<?> parentType = node.getType();
        if (childMapTypes.containsKey(parentType)) {
            Class<?> childType = childMapTypes.get(parentType);
            node.getValue().forEach(tuple -> {
                Node valueNode = tuple.getValueNode();
                if (valueNode.getType() == Object.class) {
                    valueNode.setType(childType);
                }
            });
        } else if (childMapOfListTypes.containsKey(parentType)) {
            Class<?> childType = childMapOfListTypes.get(parentType);
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
