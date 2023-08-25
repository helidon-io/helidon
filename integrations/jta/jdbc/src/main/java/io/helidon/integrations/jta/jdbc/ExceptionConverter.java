/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.jta.jdbc;

import javax.transaction.xa.XAException;

import static javax.transaction.xa.XAException.XAER_ASYNC;
import static javax.transaction.xa.XAException.XAER_DUPID;
import static javax.transaction.xa.XAException.XAER_INVAL;
import static javax.transaction.xa.XAException.XAER_NOTA;
import static javax.transaction.xa.XAException.XAER_OUTSIDE;
import static javax.transaction.xa.XAException.XAER_PROTO;
import static javax.transaction.xa.XAException.XAER_RMERR;
import static javax.transaction.xa.XAException.XAER_RMFAIL;
import static javax.transaction.xa.XAException.XA_HEURCOM;
import static javax.transaction.xa.XAException.XA_HEURHAZ;
import static javax.transaction.xa.XAException.XA_HEURMIX;
import static javax.transaction.xa.XAException.XA_HEURRB;
import static javax.transaction.xa.XAException.XA_NOMIGRATE;
import static javax.transaction.xa.XAException.XA_RBCOMMFAIL;
import static javax.transaction.xa.XAException.XA_RBDEADLOCK;
import static javax.transaction.xa.XAException.XA_RBINTEGRITY;
import static javax.transaction.xa.XAException.XA_RBOTHER;
import static javax.transaction.xa.XAException.XA_RBPROTO;
import static javax.transaction.xa.XAException.XA_RBROLLBACK;
import static javax.transaction.xa.XAException.XA_RBTIMEOUT;
import static javax.transaction.xa.XAException.XA_RBTRANSIENT;
import static javax.transaction.xa.XAException.XA_RDONLY;
import static javax.transaction.xa.XAException.XA_RETRY;

/**
 * A {@linkplain FunctionalInterface functional interface} whose implementations can convert a kind of {@link Exception}
 * encountered in the context of an {@linkplain XARoutine XA routine} to an appropriate {@link XAException}, according
 * to the rules in the <a href="https://pubs.opengroup.org/onlinepubs/009680699/toc.pdf">XA specification</a> as
 * expressed in the {@linkplain javax.transaction.xa.XAResource documentation for the <code>XAResource</code>
 * interface}.
 *
 * @see #convert(XARoutine, Exception)
 *
 * @see XARoutine
 *
 * @see XAException#errorCode
 *
 * @see javax.transaction.xa.XAResource
 *
 * @see <a href="https://pubs.opengroup.org/onlinepubs/009680699/toc.pdf">The XA Specification</a>
 */
@FunctionalInterface
public interface ExceptionConverter {


    /**
     * Converts the supplied {@link Exception} encountered in the context of the supplied {@link XARoutine} to an {@link
     * XAException} with {@linkplain XAException#errorCode an appropriate error code}, idiomatically following the rules
     * of the <a href="https://pubs.opengroup.org/onlinepubs/009680699/toc.pdf">XA specification</a>.
     *
     * @param xaRoutine the {@link XARoutine}; must not be {@code null}
     *
     * @param exception the {@link Exception} to convert; most commonly a variety of {@link java.sql.SQLException} or
     * {@link RuntimeException}; <strong>if supplied with an {@link XAException} this {@link ExceptionConverter} must
     * simply return it</strong>; if supplied with {@code null} a new {@link XAException} with a general-purpose error
     * codemust be returned
     *
     * @return a suitable non-{@code null} {@link XAException}
     *
     * @exception NullPointerException if {@code routine} is {@code null}
     *
     * @see XAException
     *
     * @see javax.transaction.xa.XAResource
     */
    XAException convert(XARoutine xaRoutine, Exception exception);


    /*
     * Static methods.
     */


    /**
     * Returns a non-{@code null} {@link String} representation of the supplied code.
     *
     * <p>The format of the returned {@link String} is left deliberately undefined and may change between versions of
     * this interface without prior notice.</p>
     *
     * @param code a code; usually the value of a {@code static} field in the {@link XAException} class
     *
     * @return a non-{@code null} {@link String} representation of the supplied code
     */
    static String codeToString(int code) {
        switch (code) {
        case XA_HEURCOM:
            return "XA_HEURCOM";
        case XA_HEURHAZ:
            return "XA_HEURHAZ";
        case XA_HEURMIX:
            return "XA_HEURMIX";
        case XA_HEURRB:
            return "XA_HEURRB";
        case XA_NOMIGRATE:
            return "XA_NOMIGRATE";
        case XA_RBCOMMFAIL:
            return "XA_RBCOMMFAIL";
        case XA_RBDEADLOCK:
            return "XA_RBDEADLOCK";
        case XA_RBINTEGRITY:
            return "XA_RBINTEGRITY";
        case XA_RBOTHER:
            return "XA_RBOTHER";
        case XA_RBPROTO:
            return "XA_RBPROTO";
        case XA_RBROLLBACK:
            return "XA_RBROLLBACK";
        case XA_RBTIMEOUT:
            return "XA_RBTIMEOUT";
        case XA_RBTRANSIENT:
            return "XA_RBTRANSIENT";
        case XA_RDONLY:
            return "XA_RDONLY";
        case XA_RETRY:
            return "XA_RETRY";
        case XAER_ASYNC:
            return "XAER_ASYNC";
        case XAER_DUPID:
            return "XAER_DUPID";
        case XAER_INVAL:
            return "XAER_INVAL";
        case XAER_NOTA:
            return "XAER_NOTA";
        case XAER_OUTSIDE:
            return "XAER_OUTSIDE";
        case XAER_PROTO:
            return "XAER_PROTO";
        case XAER_RMERR:
            return "XAER_RMERR";
        case XAER_RMFAIL:
            return "XAER_RMFAIL";
        default:
            return String.valueOf(code);
        }
    }


    /*
     * Inner and nested classes.
     */


    /**
     * An enum describing XA routines modeled by an {@link javax.transaction.xa.XAResource} implementation.
     */
    enum XARoutine {

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#start(Xid, int)} method.
         */
        START,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#end(Xid, int)} method.
         */
        END,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#prepare(Xid)} method.
         */
        PREPARE,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#commit(Xid, boolean)} method.
         */
        COMMIT,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#rollback(Xid)} method.
         */
        ROLLBACK,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#recover(int)} method.
         */
        RECOVER,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#forget(Xid)} method.
         */
        FORGET;

    }

}
