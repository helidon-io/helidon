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

package io.helidon.nima.common.tls;

import java.util.function.Consumer;

import io.helidon.common.config.NamedService;

/**
 * Implementors of this contract are responsible for managing the {@link Tls} lifecycle.
 */
public interface TlsManager extends NamedService /*, RuntimeType.Api<TlsManagerConfig>*/ {

    /**
     * The implementation should trigger/attempt a reload for the underlying TLS instance. If this returns {@code true} it is
     * expected that the next {@link #tls()} invocation will return a newly created instance.
     *
     * @return {@code true} if the implementation was able to reload a new Tls instance, {@code false} otherwise.
     */
    boolean reload();

    /**
     * The implementation should maintain a registry of consumers that are interested in knowing when the {@link Tls} changes
     *
     * @param tlsConsumer the consumer to be called when the tls instance changes
     */
    void register(Consumer<Tls> tlsConsumer);

    /**
     * The current tls instance loaded/created by this manager.
     *
     * @return the current tls instance
     */
    Tls tls();

}
