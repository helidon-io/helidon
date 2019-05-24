/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import io.helidon.common.context.Context;

/**
 * A registry for context objects. Enables instance localization between several <i>services / components / ...</i> integrated in
 * a particular known scope. ContextualRegistry instance is intended to be associated with a scope aware object such as
 * WebServer, ServerRequest or ClientRequest.
 *
 * <p>Context contains also a notion of <i>classifiers</i>. Classifier is any object defining additional <i>key</i> for registered
 * objects. To obtain such registered object, the same classifier (precisely, any equal object) has to be used.
 *
 * <p>Classifiers can be used as follows:<ol>
 * <li>As an additional identifier for registered objects of common types, like a {@link String}, ...<br>
 * <pre>{@code
 * // User detail provider service
 * registry.register("NAME_PARAM_ID", "Smith");
 * registry.register("GENDER_PARAM_ID", "male");
 * ...
 * // User consumer service
 * String name = registry.get("name", String.class);
 * }</pre></li>
 * <li>As an access control mechanism where only owners of the classifier can retrieve such contextual instance.<br>
 * <pre>{@code
 * // In some central security service.
 * registry.register(securityFrameworkInternalInstance, new AuthenticatedInternalIdentity(...));
 * ...
 * // In some authorization filter known by a central security service
 * AuthenticatedInternalIdentity auth = registry.get(securityFrameworkInternalInstance, AuthenticatedInternalIdentity.class);
 * }</pre></li>
 * </ol>
 */
public interface ContextualRegistry extends Context {

    /**
     * Creates a new empty instance.
     *
     * @return new instance
     */
    static ContextualRegistry create() {
        return new ListContextualRegistry();
    }

    /**
     * Creates a new empty instance backed by its parent read-through {@link ContextualRegistry}.
     *
     * <p>Parent {@code registry} is used only for get methods and only if this registry doesn't have registered required type.
     *
     * @param parent a parent registry
     * @return new instance
     */
    static ContextualRegistry create(Context parent) {
        return new ListContextualRegistry(parent);
    }
}
