/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.logging.Logger;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionManager;

import static javax.transaction.xa.XAResource.TMENDRSCAN;
import static javax.transaction.xa.XAResource.TMFAIL;
import static javax.transaction.xa.XAResource.TMJOIN;
import static javax.transaction.xa.XAResource.TMNOFLAGS;
import static javax.transaction.xa.XAResource.TMONEPHASE;
import static javax.transaction.xa.XAResource.TMRESUME;
import static javax.transaction.xa.XAResource.TMSTARTRSCAN;
import static javax.transaction.xa.XAResource.TMSUCCESS;
import static javax.transaction.xa.XAResource.TMSUSPEND;

final class NoOpXAResource implements Synchronization, XAResource {

    private static final Logger LOGGER = Logger.getLogger(NoOpXAResource.class.getName());

    private static final Xid[] EMPTY_XID_ARRAY = new Xid[0];

    protected final TransactionManager tm;

    NoOpXAResource() {
        this(null);
    }

    NoOpXAResource(TransactionManager tm) {
        super();
        this.tm = tm;
    }

    @Override
    public void start(Xid xid, int flags) throws XAException {
        LOGGER.info("start " + xid + " (" + flagsToString(flags) + ")");
    }

    @Override // XAResource
    public void end(Xid xid, int flags) throws XAException {
        LOGGER.info("end " + xid + " (" + flagsToString(flags) + ")");
    }

    @Override // XAResource
    public int prepare(Xid xid) throws XAException {
        LOGGER.info("prepare " + xid);
        return XAResource.XA_OK;
    }

    @Override // XAResource
    public void commit(Xid xid, boolean onePhase) throws XAException {
        LOGGER.info("commit " + xid + (onePhase ? " one phase" : " two phase"));
    }

    @Override // XAResource
    public void rollback(Xid xid) throws XAException {
        LOGGER.info("rollback " + xid);
    }

    @Override // XAResource
    public void forget(Xid xid) throws XAException {
        LOGGER.info("forget " + xid);
    }

    @Override // XAResource
    public Xid[] recover(int flags) throws XAException {
        LOGGER.info("recover " + flagsToString(flags));
        return EMPTY_XID_ARRAY;
    }

    @Override // XAResource
    public boolean isSameRM(XAResource xaResource) throws XAException {
        LOGGER.info("isSameRM? this: " + this + "; xaResource: " + xaResource);
        return this == xaResource;
    }

    @Override // XAResource
    public int getTransactionTimeout() {
        LOGGER.info("getTransactionTimeout()");
        return 0;
    }

    @Override // XAResource
    public boolean setTransactionTimeout(int transactionTimeoutInSeconds) {
        LOGGER.info("setTransactionTimeout(" + transactionTimeoutInSeconds + ")");
        return false;
    }

    @Override // Synchronization
    public void beforeCompletion() {
        LOGGER.info("beforeCompletion()");
        if (this.tm != null) {
            try {
                this.tm.getTransaction().enlistResource(this); // Is this legal?
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    @Override // Synchronization
    public void afterCompletion(int status) {
        LOGGER.info("afterCompletion(" + status + ")");
    }

    private static String flagsToString(int flags) {
        switch (flags) {
        case TMENDRSCAN:
            return "TMENDRSCAN (" + flags + ")";
        case TMFAIL:
            return "TMFAIL (" + flags + ")";
        case TMJOIN:
            return "TMJOIN (" + flags + ")";
        case TMNOFLAGS:
            return "TMNOFLAGS (" + flags + ")";
        case TMONEPHASE:
            return "TMONEPHASE (" + flags + ")";
        case TMRESUME:
            return "TMRESUME (" + flags + ")";
        case TMSTARTRSCAN:
            return "TMSTARTRSCAN (" + flags + ")";
        case TMSUCCESS:
            return "TMSUCCESS (" + flags + ")";
        case TMSUSPEND:
            return "TMSUSPEND (" + flags + ")";
        default:
            return String.valueOf(flags);
        }
    }

}
