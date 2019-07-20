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
 */
package io.helidon.integrations.cdi.jpa;

import javax.enterprise.context.spi.Context;

/**
 * An interface whose implementations indicate indirectly whether JTA
 * is available in the environment.
 *
 * @see NoTransactionSupport
 *
 * @see JtaTransactionSupport
 */
interface TransactionSupport {

    /**
     * Returns {@code true} if JTA facilities are available.
     *
     * @return {@code true} if JTA facilities are available; {@code
     * false} otherwise
     */
    boolean isActive();

    /**
     * Returns the {@linkplain Context#isActive() active} {@link
     * Context}, if any, that supports JTA facilities, or {@code null}
     *
     * <p>Implementations of this method may return {@code null}.</p>
     *
     * @return the {@link Context}, if any, that supports JTA
     * facilities, or {@code null}
     */
    Context getContext();

    /**
     * Returns {@code true} if a JTA transaction is currently in
     * effect.
     *
     * @return {@code true} if a JTA transaction is currently in
     * effect; {@code false} otherwise
     */
    boolean inTransaction();

}
