/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;
import java.util.function.Supplier;

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
public interface ContextualRegistry {

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
    static ContextualRegistry create(ContextualRegistry parent) {
        return new ListContextualRegistry(parent);
    }

    /**
     * Register a new instance.
     *
     * @param instance an instance to register
     * @param <T>      a type of the registered instance
     * @throws NullPointerException if the registered object is {@code null}
     */
    <T> void register(T instance);

    /**
     * Register a new instance using a provided supplier. The supplier is guarantied to be called at most once when it's
     * requested by the {@link #get(Class)} method. The returned value is then registered and the supplier is never used again.
     *
     * @param type     a type of supplied instance
     * @param supplier a supplier of the instance to register
     * @param <T>      a type of supplied object
     * @throws NullPointerException if the {@code type} or the {@code supplier} is {@code null}
     */
    <T> void supply(Class<T> type, Supplier<T> supplier);

    /**
     * Optionally gets registered instance by its type.
     *
     * <p>More specifically, it returns <b>the last</b> registered instance without specified <i>classifier</i> which can be cast
     * to the requested type.
     *
     * @param type a type of requested instance
     * @param <T>  a type of requested instance
     * @return The last registered instance compatible with the specified type
     */
    <T> Optional<T> get(Class<T> type);

    /**
     * Register a new instance with specified classifier.
     *
     * <p>Registered instance can be obtained only using {@link #get(Object, Class)} method with a {@code classifier} equal with
     * the one used during registration.
     *
     * @param classifier an additional registered instance classifier
     * @param instance   an instance to register
     * @param <T>        a type of the registered instance
     * @throws NullPointerException if {@code classifier} or registered object is {@code null}
     */
    <T> void register(Object classifier, T instance);

    /**
     * Registers a new instance using a provided supplier. The supplier is guarantied to be called at most once when it's
     * requested by the {@link #get(Object, Class)} method. The returned value gets registered and the supplier is never called
     * again.
     *
     * <p>Registered instance can be obtained only using {@link #get(Object, Class)} method with a {@code classifier} equal with
     * the one used during registration.
     *
     * @param classifier an additional registered instance classifier
     * @param type       a type of requested instance
     * @param supplier   a supplier of the instance to register
     * @param <T>        a type of supplied object
     * @throws NullPointerException If any parameter is {@code null}.
     */
    <T> void supply(Object classifier, Class<T> type, Supplier<T> supplier);

    /**
     * Optionally gets a registered instance by its type.
     *
     * <p>More specifically, it returns <b>the last</b> registered instance with equal <i>classifier</i> which can be cast
     * to the requested type.
     *
     * @param classifier an additional registered instance classifier
     * @param type       a type of requested instance
     * @param <T>        a type of requested instance
     * @return the last registered instance compatible with the specified type
     * @throws NullPointerException If {@code classifier} is null.
     */
    <T> Optional<T> get(Object classifier, Class<T> type);
}
