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


    /*
     * Static fields.
     */


    /**
     * Equal in value and meaning to {@link
     * javax.transaction.Status#STATUS_ACTIVE}.
     */
    int STATUS_ACTIVE = 0;

    /**
     * Equal in value and meaning to {@link
     * javax.transaction.Status#STATUS_MARKED_ROLLBACK}.
     */
    int STATUS_MARKED_ROLLBACK = 1;

    /**
     * Equal in value and meaning to {@link
     * javax.transaction.Status#STATUS_PREPARED}.
     */
    int STATUS_PREPARED = 2;

    /**
     * Equal in value and meaning to {@link
     * javax.transaction.Status#STATUS_COMMITTED}.
     */
    int STATUS_COMMITTED = 3;

    /**
     * Equal in value and meaning to {@link
     * javax.transaction.Status#STATUS_ROLLEDBACK}.
     */
    int STATUS_ROLLEDBACK = 4;

    /**
     * Equal in value and meaning to {@link
     * javax.transaction.Status#STATUS_UNKNOWN}.
     */
    int STATUS_UNKNOWN = 5;

    /**
     * Equal in value and meaning to {@link
     * javax.transaction.Status#STATUS_NO_TRANSACTION}.
     */
    int STATUS_NO_TRANSACTION = 6;

    /**
     * Equal in value and meaning to {@link
     * javax.transaction.Status#STATUS_PREPARING}.
     */
    int STATUS_PREPARING = 7;

    /**
     * Equal in value and meaning to {@link
     * javax.transaction.Status#STATUS_COMMITTING}.
     */
    int STATUS_COMMITTING = 8;

    /**
     * Equal in value and meaning to {@link
     * javax.transaction.Status#STATUS_ROLLING_BACK}.
     */
    int STATUS_ROLLING_BACK = 9;


    /*
     * Method signatures.
     */


    /**
     * Returns {@code true} if JTA facilities are available.
     *
     * @return {@code true} if JTA facilities are available; {@code
     * false} otherwise
     */
    boolean isEnabled();

    /**
     * Returns the {@linkplain Context#isActive() active} {@link
     * Context}, if any, that supports JTA facilities at the moment of
     * invocation, or {@code null}.
     *
     * <p>Implementations of this method may, and often do, return
     * {@code null}.</p>
     *
     * <p>The {@link Context} returned by implementations of this
     * method may become active or inactive at any moment.</p>
     *
     * @return the {@link Context}, if any, that supports JTA
     * facilities, or {@code null}
     *
     * @see Context#isActive()
     */
    Context getContext();

    /**
     * Returns a constant indicating the current transaction status.
     *
     * <p>Implementations of this method must return {@link
     * #STATUS_NO_TRANSACTION} ({@code 6}) if JTA is not supported.</p>
     *
     * @return a JTA {@link javax.transaction.Status} constant
     * indicating the current transaction status
     */
    int getStatus();

}
