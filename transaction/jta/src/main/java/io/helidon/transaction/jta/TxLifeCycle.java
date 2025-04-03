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
package io.helidon.transaction.jta;

/**
 * Transaction event listener service.
 * Each JTA transaction method life-cycle contains exactly two events:<ul>
 *     <li><i>start</i> when transaction method was started</li>
 *     <li><i>end</i> when transaction method was finished</li>
 *  * </ul>
 * Each new JTA transaction life-cycle contains exactly two events:<ul>
 *     <li><i>begin</i> when transaction was started</li>
 *     <li><i>commit/rollback</i> when transaction was finished</li>
 * </ul>
 */
public interface TxLifeCycle {

    /**
     * JTA transaction method was started.
     */
    void start();

    /**
     * JTA transaction method was finished.
     */
    void end();

    /**
     * New JTA transaction was created.
     *
     * @param txIdentity JTA transaction identifier
     */
    void begin(String txIdentity);

    /**
     * New JTA transaction was completed.
     *
     * @param txIdentity JTA transaction identifier
     */
    void commit(String txIdentity);

    /**
     * New JTA transaction was rolled back.
     *
     * @param txIdentity JTA transaction identifier
     */
    void rollback(String txIdentity);

}
