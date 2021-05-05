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
package io.helidon.examples.openapi.internal;

import java.util.Map;

import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;

/**
 * Example OpenAPI filter which hides a single endpoint from the OpenAPI document.
 */
public class SimpleAPIFilter implements OASFilter {

    @Override
    public PathItem filterPathItem(PathItem pathItem) {
        for (Map.Entry<PathItem.HttpMethod, Operation> methodOp
                : pathItem.getOperations().entrySet()) {
            if (SimpleAPIModelReader.DOOMED_OPERATION_ID
                    .equals(methodOp.getValue().getOperationId())) {
                return null;
            }
        }
        return OASFilter.super.filterPathItem(pathItem);
    }
}
