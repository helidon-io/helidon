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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static javax.transaction.xa.XAException.XAER_DUPID;
import static javax.transaction.xa.XAException.XAER_INVAL;
import static javax.transaction.xa.XAException.XAER_NOTA;
import static javax.transaction.xa.XAException.XAER_PROTO;
import static javax.transaction.xa.XAException.XAER_RMERR;
import static javax.transaction.xa.XAResource.TMENDRSCAN;
import static javax.transaction.xa.XAResource.TMFAIL;
import static javax.transaction.xa.XAResource.TMJOIN;
import static javax.transaction.xa.XAResource.TMNOFLAGS;
import static javax.transaction.xa.XAResource.TMONEPHASE;
import static javax.transaction.xa.XAResource.TMRESUME;
import static javax.transaction.xa.XAResource.TMSTARTRSCAN;
import static javax.transaction.xa.XAResource.TMSUCCESS;
import static javax.transaction.xa.XAResource.TMSUSPEND;
import static javax.transaction.xa.XAResource.XA_OK;
import static javax.transaction.xa.XAResource.XA_RDONLY;

public final class LocalXAResource2 implements XAResource {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = Logger.getLogger(LocalXAResource.class.getName());

    private static final Xid[] EMPTY_XID_ARRAY = new Xid[0];


    /*
     * Instance fields.
     */


    private final Function<? super Xid, ? extends Connection> connectionFunction;

    private final Map<Xid, Association> associations;


    /*
     * Constructors.
     */


    public LocalXAResource2(Function<? super Xid, ? extends Connection> connectionFunction) {
        super();
        this.connectionFunction = Objects.requireNonNull(connectionFunction, "connectionFunction");
        this.associations = new ConcurrentHashMap<>();
    }


    /*
     * Instance methods.
     */


    @Override // XAResource
    public void start(Xid xid, int flags) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "start", new Object[] { xid, flagsToString(flags) });
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        BiFunction<Xid, Association, Association> remappingFunction;
        switch (flags) {
        case TMJOIN:
            remappingFunction = LocalXAResource2::join;
            break;
        case TMNOFLAGS:
            remappingFunction = this::start;
            break;
        case TMRESUME:
            remappingFunction = LocalXAResource2::resume;
            break;
        default:
            throw (XAException) new XAException(XAER_INVAL).initCause(new IllegalArgumentException("xid: " + xid
                                                                                                   + "; flags: "
                                                                                                   + flagsToString(flags)));
        }

        try {
            this.associations.compute(xid, remappingFunction);
        } catch (IllegalArgumentException illegalArgumentException) {
            // By the contract of ConcurrentMap::compute, the
            // remapping function must throw IllegalArgumentException
            // and no other kind of error in the normal course of
            // events.  From ConcurrentMap#compute(Object,
            // BiFunction): "IllegalArgumentException - if some
            // property of the specified key or value prevents it from
            // being stored in this map". We harvest the cause, which
            // will always be an XAException, and throw that.
            throw (XAException) illegalArgumentException.getCause();
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "start");
        }
    }

    private Association start(Xid x, Association a) {
        if (a == null) {
            Connection c;
            try {
                c = this.connectionFunction.apply(x);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(new XAException(XAER_RMERR).initCause(e));
            }
            try {
                return new Association(Association.BranchState.ACTIVE, x, c);
            } catch (UncheckedSQLException e) {
                throw new IllegalArgumentException(new XAException(XAER_RMERR).initCause(e.getCause()));
            }
        }
        throw new IllegalArgumentException(new XAException(XAER_DUPID)
                                           .initCause(new IllegalArgumentException("xid: " + x
                                                                                   + "; association: " + a)));
    }

    @Override // XAResource
    public void end(Xid xid, int flags) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "end", new Object[] { xid, flagsToString(flags) });
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        BiFunction<Xid, Association, Association> remappingFunction;
        switch (flags) {
        case TMFAIL:
        case TMSUCCESS:
            remappingFunction = LocalXAResource2::activeToIdle;
            break;
        case TMSUSPEND:
            remappingFunction = LocalXAResource2::suspend;
            break;
        default:
            throw (XAException) new XAException(XAER_INVAL).initCause(new IllegalArgumentException("xid: " + xid
                                                                                                   + "; flags: "
                                                                                                   + flagsToString(flags)));
        }

        try {
            this.associations.compute(xid, remappingFunction);
        } catch (IllegalArgumentException illegalArgumentException) {
            // The remapping function must throw
            // IllegalArgumentException and no other kind of error.
            // From ConcurrentMap#compute(Object, BiFunction):
            // "IllegalArgumentException - if some property of the
            // specified key or value prevents it from being stored in
            // this map". We harvest the cause, which will always be
            // an XAException, and throw that.
            throw (XAException) illegalArgumentException.getCause();
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "end");
        }
    }

    @Override // XAResource
    public int prepare(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "prepare", xid);
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        Object association;
        try {
            association =
                this.associations.compute(xid,
                                          (x, a) -> compute(x,
                                                            a,
                                                            Association.BranchState.IDLE,
                                                            LocalXAResource2::prepare));
        } catch (IllegalArgumentException illegalArgumentException) {
            // The remapping function must throw
            // IllegalArgumentException and no other kind of error.
            // From ConcurrentMap#compute(Object, BiFunction):
            // "IllegalArgumentException - if some property of the
            // specified key or value prevents it from being stored in
            // this map". We harvest the cause, which will always be
            // an XAException, and throw that.
            //
            // (Don't remove XID here.)
            throw (XAException) illegalArgumentException.getCause();
        }

        int returnValue = association == null ? XA_RDONLY : XA_OK;

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "prepare", returnValue);
        }
        return returnValue;
    }

    @Override // XAResource
    public void commit(Xid xid, boolean onePhase) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "commit", new Object[] { xid, onePhase });
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        try {
            this.associations.compute(xid,
                                      (x, a) -> compute(x,
                                                        a,
                                                        EnumSet.of(Association.BranchState.IDLE,
                                                                   Association.BranchState.PREPARED),
                                                        LocalXAResource2::commitAndReset));
        } catch (IllegalArgumentException illegalArgumentException) {
            // The remapping function must throw
            // IllegalArgumentException and no other kind of error.
            // From ConcurrentMap#compute(Object, BiFunction):
            // "IllegalArgumentException - if some property of the
            // specified key or value prevents it from being stored in
            // this map". We harvest the cause, which will always be
            // an XAException, and throw that.
            //
            // (Remove XID because even errors cause us to transist
            // back to T0.)
            this.associations.remove(xid);
            Throwable cause = illegalArgumentException.getCause();
            if (cause == null) {
                throw (XAException) new XAException(XAER_RMERR).initCause(illegalArgumentException);
            } else if (cause instanceof XAException xaException) {
                throw xaException;
            } else {
                throw (XAException) new XAException(XAER_RMERR).initCause(cause);
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "commit");
        }
    }

    @Override // XAResource
    public void rollback(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "rollback", xid);
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        try {
            this.associations.compute(xid,
                                      (x, a) -> compute(x,
                                                        a,
                                                        EnumSet.of(Association.BranchState.IDLE,
                                                                   Association.BranchState.PREPARED,
                                                                   Association.BranchState.ROLLBACK_ONLY),
                                                        LocalXAResource2::rollbackAndReset));
        } catch (IllegalArgumentException illegalArgumentException) {
            // From ConcurrentMap#compute(Object, BiFunction):
            // "IllegalArgumentException - if some property of the
            // specified key or value prevents it from being
            // stored in this map"
            //
            // (Remove XID because even errors cause us to transist
            // back to T0.)
            this.associations.remove(xid);
            Throwable cause = illegalArgumentException.getCause();
            if (cause == null) {
                throw (XAException) new XAException(XAER_RMERR).initCause(illegalArgumentException);
            } else if (cause instanceof XAException xaException) {
                throw xaException;
            } else {
                throw (XAException) new XAException(XAER_RMERR).initCause(cause);
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "rollback");
        }
    }

    @Override // XAResource
    public void forget(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "forget", xid);
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        try {
            this.associations.compute(xid, (x, a) -> compute(x,
                                                             a,
                                                             Association.BranchState.HEURISTICALLY_COMPLETED,
                                                             LocalXAResource2::forget));
        } catch (IllegalArgumentException illegalArgumentException) {
            // From ConcurrentMap#compute(Object, BiFunction):
            // "IllegalArgumentException - if some property of the
            // specified key or value prevents it from being
            // stored in this map"
            //
            // (Don't remove XID here.)
            Throwable cause = illegalArgumentException.getCause();
            if (cause == null) {
                throw (XAException) new XAException(XAER_RMERR).initCause(illegalArgumentException);
            } else if (cause instanceof XAException xaException) {
                throw xaException;
            } else {
                throw (XAException) new XAException(XAER_RMERR).initCause(cause);
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "forget");
        }
    }

    @Override // XAResource
    public Xid[] recover(int flags) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "recover", flagsToString(flags));
        }

        switch (flags) {
        case TMSTARTRSCAN:
        case TMENDRSCAN:
        case TMNOFLAGS:
            break;
        default:
            throw (XAException) new XAException(XAER_INVAL)
                .initCause(new IllegalArgumentException("flags: " + flagsToString(flags)));
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "recover", EMPTY_XID_ARRAY);
        }
        return EMPTY_XID_ARRAY;
    }

    @Override // XAResource
    public boolean isSameRM(XAResource xaResource) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "isSameRM", xaResource);
            LOGGER.exiting(this.getClass().getName(), "isSameRM", this == xaResource);
        }
        return this == xaResource;
    }

    @Override // XAResource
    public int getTransactionTimeout() {
        return 0;
    }

    @Override // XAResource
    public boolean setTransactionTimeout(int transactionTimeoutInSeconds) {
        return false;
    }


    /*
     * Static methods.
     */


    private static Association compute(Xid x,
                                       Association a,
                                       Association.BranchState legalBranchState0,
                                       UnaryOperator<Association> f) {
        return compute(x, a, EnumSet.of(legalBranchState0), f);
    }

    private static Association compute(Xid x,
                                       Association a,
                                       Association.BranchState legalBranchState0,
                                       Association.BranchState legalBranchState1,
                                       UnaryOperator<Association> f) {
        return compute(x, a, EnumSet.of(legalBranchState0, legalBranchState1), f);
    }

    private static Association compute(Xid x,
                                       Association a,
                                       Association.BranchState legalBranchState0,
                                       Association.BranchState legalBranchState1,
                                       Association.BranchState legalBranchState2,
                                       UnaryOperator<Association> f) {
        return compute(x, a, EnumSet.of(legalBranchState0, legalBranchState1, legalBranchState2), f);
    }

    private static Association compute(Xid x,
                                       Association a,
                                       EnumSet<Association.BranchState> legalBranchStates,
                                       UnaryOperator<Association> f) {
        if (a == null) {
            throw new IllegalArgumentException(new XAException(XAER_NOTA)
                                               .initCause(new IllegalStateException("xid: " + x
                                                                                    + "; association: " + a)));
        }
        if (legalBranchStates.contains(a.branchState())) {
            return f.apply(a);
        }
        throw new IllegalArgumentException(new XAException(XAER_PROTO)
                                           .initCause(new IllegalStateException("xid: " + x
                                                                                + "; association: " + a)));
    }

    // (Remapping BiFunction.)
    private static Association activeToIdle(Xid x, Association a) {
        return a.activeToIdle();
    }

    // (Remapping BiFunction.)
    private static Association suspend(Xid x, Association a) {
        return a.suspend();
    }

    // (Remapping BiFunction.)
    private static Association join(Xid x, Association a) {
        if (a != null && !a.suspended()) {
            switch (a.branchState()) {
            case ACTIVE:
                return a;
            case IDLE:
                try {
                    return a.idleToActive();
                } catch (UncheckedSQLException e) {
                    throw new IllegalArgumentException(new XAException(XAER_RMERR).initCause(e.getCause()));
                }
            }
        }
        throw new IllegalArgumentException(new XAException(XAER_PROTO)
                                           .initCause(new IllegalStateException("xid: " + x
                                                                                + "; association: " + a)));
    }

    // (Remapping BiFunction.)
    private static Association resume(Xid x, Association a) {
        if (a != null) {
            // TODO: actual resumption logic on a, then:
            try {
                return a.resume();
            } catch (UncheckedSQLException e) {
                throw new IllegalArgumentException(new XAException(XAER_RMERR).initCause(e.getCause()));
            } catch (IllegalTransitionException e) {
                throw new IllegalArgumentException(new XAException(XAER_PROTO).initCause(e));
            }
        }
        throw new IllegalArgumentException(new XAException(XAER_NOTA)
                                           .initCause(new IllegalArgumentException("xid: " + x
                                                                                   + "; association: " + a)));
    }

    // (UnaryOperator for supplying to compute() above.)
    private static Association commitAndReset(Association a) {
        try {
            a = a.commitAndReset();
        } catch (SQLException e) {
            throw new IllegalArgumentException(e);
        }
        assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
        // Remove the association.
        return null;
    }

    // (UnaryOperator for supplying to compute() above.)
    private static Association rollbackAndReset(Association a) {
        try {
            a = a.rollbackAndReset();
        } catch (SQLException sqlException) {
            throw new IllegalArgumentException(sqlException);
        }
        assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
        // Remove the association.
        return null;
    }

    // (UnaryOperator for supplying to compute() above.)
    private static Association prepare(Association a) {
        assert !a.suspended(); // can't be in T2
        try {
            if (a.connection().isReadOnly()) {
                a = a.reset();
                assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
                a = null;
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException(new XAException(XAER_RMERR).initCause(e));
        }
        return a;
    }

    // (UnaryOperator for supplying to compute() above.)
    private static Association forget(Association a) {
        try {
            a = a.reset();
        } catch (SQLException sqlException) {
            throw new IllegalArgumentException(sqlException);
        }
        assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
        return null;
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

    private static void sink(Object ignored) {}


    /*
     * Inner and nested classes.
     */


    private static final class UncheckedSQLException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private UncheckedSQLException(SQLException cause) {
            super(cause);
        }

        @Override // RuntimeException
        public SQLException getCause() {
            return (SQLException) super.getCause();
        }

    }

    private static final class IllegalTransitionException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        private IllegalTransitionException(String message) {
            super(message);
        }

    }

    private static record Association(BranchState branchState,
                                      Xid xid,
                                      boolean suspended,
                                      Connection connection,
                                      boolean priorAutoCommit) {

        // Branch Association States: (XA specification, table 6-2)
        // T0: Not Associated
        // T1: Associated
        // T2: Association Suspended

        // Branch States: (XA specification, table 6-4)
        // S0: Non-existent Transaction
        // S1: Active
        // S2: Idle
        // S3: Prepared
        // S4: Rollback Only
        // S5: Heuristically Completed

        private Association(BranchState branchState, Xid xid, Connection connection) {
            this(branchState, xid, false, connection);
        }

        private Association(BranchState branchState, Xid xid, boolean suspended, Connection connection) {
            this(branchState, xid, suspended, connection, true /* JDBC default; will be set from connection anyway */);
        }

        private Association {
            Objects.requireNonNull(xid, "xid");
            switch (branchState) {
            case IDLE:
                break;
            case NON_EXISTENT_TRANSACTION:
            case ACTIVE:
            case PREPARED:
            case ROLLBACK_ONLY:
            case HEURISTICALLY_COMPLETED:
                if (suspended) {
                    throw new IllegalArgumentException("suspended");
                }
                break;
            default:
                throw new IllegalArgumentException("branchState: " + branchState);
            }
            try {
                priorAutoCommit = connection.getAutoCommit();
                if (priorAutoCommit) {
                    connection.setAutoCommit(false);
                }
            } catch (SQLException sqlException) {
                throw new UncheckedSQLException(sqlException);
            }
            // T0, T1 or T2; S0 or S2
        }

        public boolean suspended() {
            assert this.suspended ? this.branchState() == BranchState.IDLE : true;
            return this.suspended;
        }

        private Association activeToIdle() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case ACTIVE:
                    // OK; end(*) was called and didn't fail with an
                    // XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated (T1 -> T1; unchanged)
                    // Active     -> Idle       (S1 -> S2)
                    return new Association(BranchState.IDLE,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association activeToRollbackOnly() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case ACTIVE:
                    // OK; end(*) was called and failed with an
                    // XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated    (T1 -> T1; unchanged)
                    // Active     -> Rollback Only (S1 -> S4)
                    return new Association(BranchState.ROLLBACK_ONLY,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association idleToActive() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case IDLE:
                    // OK; start(TMJOIN) was called and didn't fail
                    // with an XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated (T1 -> T1; unchanged)
                    // Idle       -> Active     (S2 -> S1)
                    return new Association(BranchState.ACTIVE,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                }
            }
            throw new IllegalTransitionException(this.toString());
        }


        private Association idleToRollbackOnly() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case IDLE:
                    // OK; start(*) was called and failed with an
                    // XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated    (T1 -> T1; unchanged)
                    // Idle       -> Rollback Only (S2 -> S4)
                    return new Association(BranchState.ROLLBACK_ONLY,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association suspend() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case ACTIVE:
                    // OK; end(TMSUSPEND) was called and we are not
                    // suspended
                    //
                    // Associated -> Association Suspended (T1 -> T2)
                    // Active     -> Idle                  (S1 -> S2)
                    return new Association(BranchState.IDLE,
                                           this.xid(),
                                           true,
                                           this.connection(),
                                           this.priorAutoCommit());
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association resume() {
            if (this.suspended()) {
                switch (this.branchState()) {
                case IDLE:
                    // OK; start(TMRESUME) was called and we are
                    // suspended
                    //
                    // Association Suspended -> Associated (T2 -> T1)
                    // Idle                  -> Active     (S2 -> S1)
                    return new Association(BranchState.ACTIVE,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association commitAndReset() throws SQLException {
            Connection c = this.connection();
            return this.runAndReset(c::commit, c::rollback);
        }

        private Association rollbackAndReset() throws SQLException {
            return this.runAndReset(this.connection()::rollback, null);
        }

        private Association runAndReset(SQLRunnable r, SQLRunnable rollbackRunnable)
            throws SQLException {
            Association a = null;
            SQLException sqlException = null;
            try {
                r.run();
            } catch (SQLException e) {
                sqlException = e;
                if (rollbackRunnable != null) {
                    try {
                        rollbackRunnable.run();
                    } catch (SQLException e2) {
                        e.setNextException(e2);
                    }
                }
            } finally {
                try {
                    a = this.reset();
                } catch (SQLException e) {
                    if (sqlException == null) {
                        sqlException = e;
                    } else {
                        sqlException.setNextException(e);
                    }
                } finally {
                    if (sqlException != null) {
                        throw sqlException;
                    }
                }
            }
            return a;
        }

        private Association forgetAndReset() throws SQLException {
            return this.reset();
        }

        private Association reset() throws SQLException {
            this.connection().setAutoCommit(this.priorAutoCommit());
            return new Association(BranchState.NON_EXISTENT_TRANSACTION,
                                   this.xid(),
                                   false,
                                   this.connection(),
                                   this.priorAutoCommit());
        }

        private static interface SQLRunnable {

            public void run() throws SQLException;

        }

        private static enum BranchState {
            NON_EXISTENT_TRANSACTION, // S0
            ACTIVE, // S1
            IDLE, // S2
            PREPARED, // S3
            ROLLBACK_ONLY, // S4
            HEURISTICALLY_COMPLETED; // S5
        }

    }

}
