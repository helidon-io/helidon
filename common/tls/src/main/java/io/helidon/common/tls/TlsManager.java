/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.tls;

import java.util.function.Consumer;

import io.helidon.common.config.NamedService;

/**
 * Implementors of this contract are responsible for managing the {@link Tls} instance lifecycle. When the instance changes, it
 * is then responsible to notify all registered consumers to accept the new Tls instance.
 */
// TODO: ok to remove RuntimeType.Api<>?
public interface TlsManager extends NamedService /*, RuntimeType.Api<TlsManagerConfig>*/ {

    // TODO: the point of this is whether we should exposed a "force reload now" type of behavior for all tls managers.
//    /**
//     * The implementation should trigger an attempt to reload the underlying {@link Tls} instance by the manager implementation.
//     * A return value of {@code true} then indicates that the reload was successful and subsequently the next {@link #tls()}
//     * invocation will return the newly created {@link Tls} instance.
//     *
//     * @return {@code true} if the implementation was able to reload a new Tls instance, {@code false} otherwise.
//     */
//    boolean reload();

    /**
     * The implementation should maintain a registry of consumers that are interested in knowing when the {@link Tls} changes
     *
     * @param tlsConsumer the consumer to be called when the tls instance changes
     */
    void register(Consumer<Tls> tlsConsumer);

    /**
     * The current {@link Tls} (re)loaded and/or created instance. The instance depends upon the nature of the implementation
     * and how the manager sourced that instance.
     *
     * @return the current tls instance
     */
    Tls tls();

    /**
     * Called when the underlying Tls instance has changed.
     *
     * @param tls the new tls instance
     */
    void tls(Tls tls);

}
