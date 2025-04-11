/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.transaction;

/**
 * Transaction event listener service.
 * Notifications from the transactions implemented in {@link TxSupport} interface.
 * Implementation of this interface must be a service in service registry.
 * <p>
 * Each transaction method life-cycle contains exactly two events:<ul>
 *     <li><i>start</i> when transaction method was started</li>
 *     <li><i>end</i> when transaction method was finished</li>
 * </ul>
 * Each new transaction life-cycle contains exactly two events:<ul>
 *     <li><i>begin</i> when transaction was started</li>
 *     <li><i>commit/rollback</i> when transaction was finished</li>
 * </ul>
 */
public interface TxLifeCycle {

    /**
     * Transaction method was started.
     *
     * @param type the type of the transaction API support, {@link TxSupport#type()},
     *             passed from {@link TxSupport} implementation to the {@link TxLifeCycle}
     *            implementation
     */
    void start(String type);

    /**
     * Transaction method was finished.
     */
    void end();

    /**
     * New transaction was created.
     *
     * @param txIdentity transaction identifier
     */
    void begin(String txIdentity);

    /**
     * Current transaction was completed.
     *
     * @param txIdentity transaction identifier
     */
    void commit(String txIdentity);

    /**
     * Current transaction was rolled back.
     *
     * @param txIdentity transaction identifier
     */
    void rollback(String txIdentity);

    /**
     * Current transaction was suspended.
     *
     * @param txIdentity transaction identifier
     */
    void suspend(String txIdentity);

    /**
     * Current transaction was resumed.
     *
     * @param txIdentity transaction identifier
     */
    void resume(String txIdentity);

}
