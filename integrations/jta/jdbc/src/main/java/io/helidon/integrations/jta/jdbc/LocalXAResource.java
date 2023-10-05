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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import io.helidon.integrations.jdbc.SQLRunnable;
import io.helidon.integrations.jdbc.UncheckedSQLException;
import io.helidon.integrations.jta.jdbc.ExceptionConverter.XARoutine;

import static javax.transaction.xa.XAException.XAER_DUPID;
import static javax.transaction.xa.XAException.XAER_INVAL;
import static javax.transaction.xa.XAException.XAER_NOTA;
import static javax.transaction.xa.XAException.XAER_PROTO;
import static javax.transaction.xa.XAException.XAER_RMERR;
import static javax.transaction.xa.XAException.XAER_RMFAIL;
import static javax.transaction.xa.XAException.XA_RBROLLBACK;
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

/**
 * An {@link XAResource} that adapts an ordinary arbitrary {@link Connection} as much as possible to the {@link
 * XAResource} contract.
 *
 * <p><strong>Note:</strong> instances of this class are lossless in the presence of one-phase commit operations and
 * potentially lossy in the presence of two-phase commit operations.</p>
 *
 * <p>Instances of this class are safe for concurrent use by multiple threads.</p>
 */
final class LocalXAResource implements XAResource {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = Logger.getLogger(LocalXAResource.class.getName());

    private static final Xid[] EMPTY_XID_ARRAY = new Xid[0];

    // package-protected for testing only.
    static final ConcurrentMap<Xid, Association> ASSOCIATIONS = new ConcurrentHashMap<>();


    /*
     * Instance fields.
     */


    private final Function<? super Xid, ? extends Connection> connectionFunction;

    private final ExceptionConverter exceptionConverter;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link LocalXAResource}.
     *
     * @param connectionFunction a {@link Function} that accepts a {@link Xid} (supplied by the {@link #start(Xid, int)}
     * method) and returns a {@link Connection} to associate with the global transaction; must not be {@code null}; must
     * never return {@code null}; must be safe for concurrent use by multiple threads; will never be invoked with a
     * {@code null} {@link Xid}
     *
     * @param exceptionConverter a {@link ExceptionConverter} that accepts a {@link XARoutine} and a {@link
     * SQLException} and converts the {@link SQLException} to an <em>appropriate</em> {@link XAException} following the
     * rules defined by the <a href="https://pubs.opengroup.org/onlinepubs/009680699/toc.pdf">XA Specification</a> as
     * interpreted by the specification of the {@code javax.transaction.xa} package and its classes; may be {@code null}
     * in which case a default implementation will be used instead
     *
     * @see #start(Xid, int)
     */
    LocalXAResource(Function<? super Xid, ? extends Connection> connectionFunction, ExceptionConverter exceptionConverter) {
        super();
        this.connectionFunction = Objects.requireNonNull(connectionFunction, "connectionFunction");
        this.exceptionConverter = exceptionConverter == null ? LocalXAResource::convert0 : exceptionConverter;
    }


    /*
     * Instance methods.
     */


    @Override // XAResource
    public void start(Xid xid, int flags) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "start", new Object[] {xid, flagsToString(flags)});
        }
        requireNonNullXid(xid);

        BiFunction<Xid, Association, Association> remappingFunction;
        switch (flags) {
        case TMJOIN:
            remappingFunction = LocalXAResource::join;
            break;
        case TMNOFLAGS:
            remappingFunction = this::start;
            break;
        case TMRESUME:
            remappingFunction = LocalXAResource::resume;
            break;
        default:
            // Bad flags.
            throw (XAException) new XAException(XAER_INVAL)
                .initCause(new IllegalArgumentException("xid: " + xid + "; flags: " + flagsToString(flags)));
        }

        this.computeAssociation(XARoutine.START, xid, remappingFunction);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "start");
        }
    }

    // (Remapping BiFunction, used in start() above and supplied to computeAssociation() below.)
    private Association start(Xid x, Association a) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "start", new Object[] {x, a});
        }
        assert x != null; // x has already been vetted and is known to be non-null
        if (a != null) {
            throw new UncheckedXAException((XAException) new XAException(XAER_DUPID)
                                           .initCause(new IllegalArgumentException("xid: " + x + "; association: " + a)));
        }
        Connection c;
        try {
            c = this.connectionFunction.apply(x);
        } catch (RuntimeException e) {
            // Weirdly, XAER_RMERR seems to be the "please retry" error code in this one case, not XAER_RMFAIL:
            // https://github.com/jbosstm/narayana/blob/8ccaf0f85c7a76c227941d26cc3aa3fa9f05b160/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/transaction/arjunacore/TransactionImple.java#L682-L689.
            throw new UncheckedXAException((XAException) new XAException(XAER_RMERR).initCause(e));
        }
        if (c == null) {
            // Weirdly, XAER_RMERR seems to be the "please retry" error code in this one case, not XAER_RMFAIL:
            // https://github.com/jbosstm/narayana/blob/8ccaf0f85c7a76c227941d26cc3aa3fa9f05b160/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/transaction/arjunacore/TransactionImple.java#L682-L689.
            throw new UncheckedXAException((XAException) new XAException(XAER_RMERR)
                                           .initCause(new NullPointerException("connectionFunction.apply(" + x + ")")));
        }
        a = new Association(Association.BranchState.ACTIVE, x, c);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.logp(Level.FINE, this.getClass().getName(), "start",
                        "Created new Association ({0}) for connection ({1}) in state ACTIVE", new Object[] {a, c});
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "start", a);
        }
        return a;
    }

    @Override // XAResource
    public void end(Xid xid, int flags) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "end", new Object[] {xid, flagsToString(flags)});
        }
        requireNonNullXid(xid);

        BiFunction<Xid, Association, Association> remappingFunction;
        switch (flags) {
        case TMFAIL:
        case TMSUCCESS:
            remappingFunction = LocalXAResource::activeToIdle;
            break;
        case TMSUSPEND:
            remappingFunction = LocalXAResource::suspend;
            break;
        default:
            // Bad flags.
            throw (XAException) new XAException(XAER_INVAL)
                .initCause(new IllegalArgumentException("xid: " + xid + "; flags: " + flagsToString(flags)));
        }

        // Any XAException thrown can have any error code. The transaction will be marked as rollback only. See
        // https://github.com/jbosstm/narayana/blob/8ccaf0f85c7a76c227941d26cc3aa3fa9f05b160/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/transaction/arjunacore/TransactionImple.java#L978-L992.
        this.computeAssociation(XARoutine.END, xid, remappingFunction);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "end");
        }
    }

    @Override // XAResource
    public int prepare(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "prepare", xid);
        }
        requireNonNullXid(xid);

        // Any XAException thrown can have basically any error code. See
        // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/resources/arjunacore/XAResourceRecord.java#L227-L261.
        Object association =
            this.computeAssociation(XARoutine.PREPARE,
                                    xid,
                                    EnumSet.of(Association.BranchState.IDLE),
                                    LocalXAResource::prepare,
                                    false); // don't remove association on error

        int returnValue = association == null ? XA_RDONLY : XA_OK;

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "prepare", returnValue);
        }
        return returnValue;
    }

    @Override // XAResource
    public void commit(Xid xid, boolean onePhase) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "commit", new Object[] {xid, onePhase});
        }
        requireNonNullXid(xid);

        // Error handling needs to be extraordinarily specific. XAER_RMERR indicates catastrophic failure (like if a
        // local rollback(), issued in response to a local commit() failure, occurs).  XAER_RMFAIL indicates a transient
        // error, i.e. we tried to Do The Thing but for now it Didn't Work.
        //
        // Concrete examples: You can see that XAER_RMERR and (the completely non-transient) XAER_PROTO (for example)
        // are both treated as Equally Bad Things:
        // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/resources/arjunacore/XAResourceRecord.java#L512-L514
        //
        // You can also see that XAER_RMFAIL does something different and is no different from XA_RETRY:
        // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/resources/arjunacore/XAResourceRecord.java#L525-L534
        //
        // Finally, if an error happens during commit, we issue a SQL/JDBC/local transaction rollback() command.  If
        // that works, then we return XA_RB*. If that fails, chances are we return XAER_RMERR, but
        this.computeAssociation(XARoutine.COMMIT,
                                xid,
                                EnumSet.of(Association.BranchState.IDLE,
                                           Association.BranchState.PREPARED),
                                a -> commitAndReset(a, onePhase),
                                true); // do remove association on error

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "commit");
        }
    }

    @Override // XAResource
    public void rollback(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "rollback", xid);
        }
        requireNonNullXid(xid);

        // An error during rollback is bad; in a two-phase situation where we've already prepared, then returning
        // XAER_RMERR will put us in HEURISTIC_HAZARD
        // (https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/resources/arjunacore/XAResourceRecord.java#L379-L420).
        // Doing XAER_RMFAIL will put us in FINISH_ERROR.
        this.computeAssociation(XARoutine.ROLLBACK,
                                xid,
                                EnumSet.of(Association.BranchState.IDLE,
                                           Association.BranchState.PREPARED,
                                           Association.BranchState.ROLLBACK_ONLY),
                                LocalXAResource::rollbackAndReset,
                                true); // do remove association on error

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "rollback");
        }
    }

    @Override // XAResource
    public void forget(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "forget", xid);
        }
        requireNonNullXid(xid);

        this.computeAssociation(XARoutine.FORGET,
                                xid,
                                EnumSet.of(Association.BranchState.HEURISTICALLY_COMPLETED),
                                LocalXAResource::forgetAndReset,
                                false); // don't remove association on error

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
        case TMENDRSCAN:
        case TMNOFLAGS:
        case TMSTARTRSCAN:
            break;
        default:
            // Bad flags.
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
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "getTransactionTimeout");
            LOGGER.exiting(this.getClass().getName(), "getTransactionTimeout", 0);
        }
        return 0;
    }

    @Override // XAResource
    public boolean setTransactionTimeout(int transactionTimeoutInSeconds) {
        // (Interesting note: this is the first method that is called by the TransactionManager, i.e. before #start(Xid,
        // int).)
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "setTransactionTimeout", transactionTimeoutInSeconds);
            LOGGER.exiting(this.getClass().getName(), "setTransactionTimeout", false);
        }
        return false;
    }

    private Association computeAssociation(XARoutine xaRoutine,
                                           Xid xid,
                                           BiFunction<? super Xid, ? super Association, ? extends Association> f)
        throws XAException {
        try {
            return ASSOCIATIONS.compute(xid, f);
        } catch (RuntimeException e) {
            throw this.convert(xaRoutine, e);
        }
    }

    private Association computeAssociation(XARoutine xaRoutine,
                                           Xid xid,
                                           EnumSet<Association.BranchState> legalBranchStates,
                                           UnaryOperator<Association> f,
                                           boolean removeAssociationOnError)
        throws XAException {
        try {
            return ASSOCIATIONS.compute(xid, (x, a) -> remap(x, a, legalBranchStates, f));
        } catch (RuntimeException e) {
            if (removeAssociationOnError) {
                ASSOCIATIONS.remove(xid);
            }
            throw this.convert(xaRoutine, e);
        }
    }

    private XAException convert(XARoutine xaRoutine, Throwable e) {
        XAException returnValue;
        if (e == null) {
            returnValue = new XAException(XAER_RMERR);
        } else if (e instanceof XAException xaException) {
            // Obviously if e is an XAException no conversion is necessary.
            returnValue = xaException;
        } else {
            Throwable cause = e.getCause();
            if (cause instanceof XAException xaException) {
                // No matter what, if the cause was an XAException then it is canonical.
                returnValue = xaException;
            } else if (e instanceof IllegalTransitionException) {
                // Any IllegalTransitionException is by definition an XA protocol problem.
                returnValue = (XAException) new XAException(XAER_PROTO).initCause(e);
            } else if (e instanceof SQLException sqlException) {
                returnValue = this.exceptionConverter.convert(xaRoutine, sqlException);
            } else if (cause instanceof SQLException sqlException) {
                returnValue = this.exceptionConverter.convert(xaRoutine, sqlException);
            } else {
                returnValue = (XAException) new XAException(XAER_RMERR).initCause(e);
            }
        }
        if (returnValue == null) {
            returnValue = (XAException) new XAException(XAER_RMERR).initCause(e);
        }
        return returnValue;
    }


    /*
     * Static methods.
     */


    private static void requireNonNullXid(Xid xid) throws XAException {
        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }
    }

    // (Used via method reference only when an exceptionConverter was not supplied at construction time. This is the
    // default implementation.)
    private static XAException convert0(XARoutine xaRoutine, Exception e) {
        if (e == null) {
            return new XAException(XAER_RMERR);
        } else if (e instanceof XAException xaException) {
            return xaException;
        } else {
            Throwable cause = e.getCause();
            if (cause instanceof XAException xaException) {
                // No matter what, if the cause was an XAException then it is canonical.
                return xaException;
            } else {
                SQLException sqlException;
                if (e instanceof SQLException s) {
                    sqlException = s;
                } else if (cause instanceof SQLException s) {
                    sqlException = s;
                } else {
                    sqlException = null;
                }
                if (sqlException != null) {
                    String sqlState = sqlException.getSQLState();
                    if (sqlState != null
                        && (sqlState.startsWith("080")
                            || sqlState.equalsIgnoreCase("08S01") // ("ess" not "five")
                            || sqlState.equalsIgnoreCase("JZ006"))) {
                        // Connection-related database error; might be transient; use XAER_RMFAIL instead of XAER_RMERR,
                        // apparently.  See, for example,
                        // https://github.com/pgjdbc/pgjdbc/commit/e5aab1cd3e49051f46298d8f1fd9f66af1731299. Also see
                        // https://github.com/pgjdbc/pgjdbc/blob/98c04a0c903e90f2d5d10a09baf1f753747b2556/pgjdbc/src/main/java/org/postgresql/xa/PGXAConnection.java#L651-L657
                        // and
                        // https://github.com/pgjdbc/pgjdbc/blob/98c04a0c903e90f2d5d10a09baf1f753747b2556/pgjdbc/src/main/java/org/postgresql/xa/PGXAConnection.java#L553. Also
                        // see
                        // https://github.com/ironjacamar/ironjacamar/blob/ff62b8b23f59f9fbb9c15be40fef38efb872c436/core/src/main/java/org/jboss/jca/core/tx/jbossts/LocalConnectableXAResourceImpl.java#L55-L61.
                        //
                        // But also note XAER_RMERR vs. XAER_RMFAIL changes semantics depending on the routine (start, end,
                        // commit, rollback, prepare, forget, recover).
                        return (XAException) new XAException(XAER_RMFAIL).initCause(e);
                    }
                }
            }
        }
        return (XAException) new XAException(XAER_RMERR).initCause(e);
    }

    // (Invoked only in context of a remapping BiFunction, from computeAssociation().)
    private static Association remap(Xid xid,
                                     Association a,
                                     EnumSet<Association.BranchState> legalBranchStates,
                                     UnaryOperator<Association> remapOperator) {
        if (a == null) {
            throw new UncheckedXAException((XAException) new XAException(XAER_NOTA)
                                           .initCause(new NullPointerException("xid: " + xid + "; association: null")));
        } else if (!legalBranchStates.contains(a.branchState())) {
            throw new UncheckedXAException((XAException) new XAException(XAER_PROTO)
                                           .initCause(new IllegalStateException("xid: " + xid + "; association: " + a)));
        }
        return remapOperator.apply(a);
    }

    // (Remapping BiFunction. Used in end() above.)
    private static Association activeToIdle(Xid x, Association a) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(Association.class.getName(), "activeToIdle", new Object[] {x, a});
        }
        a = a.activeToIdle();
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(Association.class.getName(), "activeToIdle", a);
        }
        return a;
    }

    // (Remapping BiFunction. Used in end() above.)
    private static Association suspend(Xid x, Association a) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(Association.class.getName(), "suspend", new Object[] {x, a});
        }
        a = a.suspend();
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(Association.class.getName(), "suspend", a);
        }
        return a;
    }

    // (Remapping BiFunction. Used in start() above.)
    private static Association join(Xid x, Association a) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(Association.class.getName(), "join", new Object[] {x, a});
        }
        if (a == null) {
            throw new UncheckedXAException((XAException) new XAException(XAER_NOTA)
                                           .initCause(new NullPointerException("xid: " + x + "; association: null")));
        } else if (a.suspended()) {
            assert a.branchState() == Association.BranchState.IDLE;
            throw new UncheckedXAException((XAException) new XAException(XAER_PROTO)
                                           .initCause(new IllegalStateException("xid: " + x + "; association: " + a)));
        }
        switch (a.branchState()) {
        case ACTIVE:
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, Association.class.getName(), "join",
                            "Joining Association ({0}) in state ACTIVE", a);
            }
            break;
        case IDLE:
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, Association.class.getName(), "join",
                            "Joining Association ({0}) and transitioning it from state IDLE to state ACTIVE", a);
            }
            a = a.idleToActive();
            break;
        default:
            throw new IllegalTransitionException("xid: " + x + "; association: " + a);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(Association.class.getName(), "join", a);
        }
        return a;
    }

    // (Remapping BiFunction. Used in start() above.)
    private static Association resume(Xid x, Association a) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(Association.class.getName(), "resume", new Object[] {x, a});
        }
        if (a == null) {
            throw new UncheckedXAException((XAException) new XAException(XAER_NOTA)
                                           .initCause(new NullPointerException("xid: " + x + "; association: null")));
        }
        a = a.resume();
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(Association.class.getName(), "resume", a);
        }
        return a;
    }

    // (Invoked during remap() above. Similar to the UnaryOperator-like methods, but not invoked via method reference.)
    private static Association commitAndReset(Association a, boolean onePhase) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(Association.class.getName(), "commitAndReset", new Object[] {a, onePhase});
        }
        assert a != null; // already vetted
        try {
            a = a.commitAndReset(onePhase);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        } catch (XAException e) {
            throw new UncheckedXAException(e);
        }
        assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.logp(Level.FINE, Association.class.getName(), "commitAndReset",
                        "Removing association {0}", a);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(Association.class.getName(), "commitAndReset", "null");
        }
        // Critically important: remove the association.
        return null;
    }

    // (UnaryOperator for supplying via method reference to remap() above.)
    private static Association rollbackAndReset(Association a) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(Association.class.getName(), "rollbackAndReset", a);
        }
        assert a != null; // already vetted
        try {
            a = a.rollbackAndReset();
        } catch (SQLException sqlException) {
            throw new UncheckedSQLException(sqlException);
        }
        assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.logp(Level.FINE, Association.class.getName(), "rollbackAndReset",
                        "Removing association {0}", a);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(Association.class.getName(), "rollbackAndReset", "null");
        }
        // Critically important: remove the association.
        return null;
    }

    // (UnaryOperator for supplying via method reference to remap() above.)
    private static Association prepare(Association a) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(Association.class.getName(), "prepare", a);
        }
        assert a != null; // already vetted
        assert !a.suspended(); // can't be in T2
        try {
            if (a.connection().isReadOnly()) {
                a = a.reset();
                assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, Association.class.getName(), "prepare",
                                "Removing association {0}", a);
                }
                // Critically important: remove the association.
                a = null;
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(Association.class.getName(), "prepare", a);
        }
        return a;
    }

    // (UnaryOperator for supplying via method reference to remap() above.)
    private static Association forgetAndReset(Association a) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(Association.class.getName(), "forgetAndReset", a);
        }
        assert a != null; // already vetted
        try {
            a = a.forgetAndReset();
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
        assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.logp(Level.FINE, Association.class.getName(), "forgetAndReset",
                        "Removing association {0}", a);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(Association.class.getName(), "forgetAndReset", "null");
        }
        // Critically important: remove the association.
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


    /*
     * Inner and nested classes.
     */


    static record Association(BranchState branchState,
                              Xid xid,
                              boolean suspended,
                              Connection connection,
                              boolean priorAutoCommit) {

        private static final Logger LOGGER = Logger.getLogger(Association.class.getName());

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

        Association(BranchState branchState, Xid xid, Connection connection) {
            this(branchState, xid, false, connection, autoCommit(connection));
        }

        Association {
            Objects.requireNonNull(xid, "xid");
            boolean autoCommit = false;
            switch (branchState) {
            case IDLE:
                break;
            case ACTIVE:
            case HEURISTICALLY_COMPLETED:
            case PREPARED:
            case ROLLBACK_ONLY:
                if (suspended) {
                    throw new IllegalArgumentException("suspended");
                }
                break;
            case NON_EXISTENT_TRANSACTION:
                if (suspended) {
                    throw new IllegalArgumentException("suspended");
                }
                autoCommit = priorAutoCommit;
                break;
            default:
                throw new IllegalArgumentException("branchState: " + branchState);
            }
            try {
                if (connection.getAutoCommit() != autoCommit) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.logp(Level.FINE, Association.class.getName(), "<init>",
                                    "Setting autoCommit to {0} on connection {1}",
                                    new Object[] {autoCommit, connection});
                    }
                    connection.setAutoCommit(autoCommit);
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
                    // OK; end(*) was called and didn't fail with an XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated (T1 -> T1; unchanged)
                    // Active     -> Idle       (S1 -> S2)
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.logp(Level.FINE, Association.class.getName(), "activeToIdle",
                                    "Transitioning Association ({0}) from state ACTIVE to state IDLE", this);
                    }
                    return new Association(BranchState.IDLE,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                  break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association activeToRollbackOnly() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case ACTIVE:
                    // OK; end(*) was called and failed with an XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated    (T1 -> T1; unchanged)
                    // Active     -> Rollback Only (S1 -> S4)
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.logp(Level.FINE, Association.class.getName(), "activeToRollbackOnly",
                                    "Transitioning Association ({0}) from state ACTIVE to state ROLLBACK_ONLY", this);
                    }
                    return new Association(BranchState.ROLLBACK_ONLY,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                  break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association idleToActive() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case IDLE:
                    // OK; start(TMJOIN) was called and didn't fail with an XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated (T1 -> T1; unchanged)
                    // Idle       -> Active     (S2 -> S1)
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.logp(Level.FINE, Association.class.getName(), "idleToActive",
                                    "Transitioning Association ({0}) from state IDLE to state ACTIVE", this);
                    }
                    return new Association(BranchState.ACTIVE,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                    break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association idleToRollbackOnly() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case IDLE:
                    // OK; start(*) was called and failed with an XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated    (T1 -> T1; unchanged)
                    // Idle       -> Rollback Only (S2 -> S4)
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.logp(Level.FINE, Association.class.getName(), "idleToRollbackOnly",
                                    "Transitioning Association ({0}) from state IDLE to state ROLLBACK_ONLY", this);
                    }
                    return new Association(BranchState.ROLLBACK_ONLY,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                    break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association suspend() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case ACTIVE:
                    // OK; end(TMSUSPEND) was called and we are not suspended
                    //
                    // Associated -> Association Suspended (T1 -> T2)
                    // Active     -> Idle                  (S1 -> S2)
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.logp(Level.FINE, Association.class.getName(), "suspend",
                                    "Suspending Association ({0}) and transitioning from state ACTIVE to state IDLE", this);
                    }
                    return new Association(BranchState.IDLE,
                                           this.xid(),
                                           true,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                    break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association resume() {
            if (this.suspended()) {
                switch (this.branchState()) {
                case IDLE:
                    // OK; start(TMRESUME) was called and we are suspended
                    //
                    // Association Suspended -> Associated (T2 -> T1)
                    // Idle                  -> Active     (S2 -> S1)
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.logp(Level.FINE, Association.class.getName(), "resume",
                                    "Resuming Association ({0}) from state IDLE to state ACTIVE", this);
                    }
                    return new Association(BranchState.ACTIVE,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                    break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association commitAndReset(boolean onePhase) throws SQLException, XAException {
            Connection c = this.connection();
            return this.runAndReset(c::commit, c::rollback, onePhase);
        }

        private Association rollbackAndReset() throws SQLException {
            try {
                return this.runAndReset(this.connection()::rollback, null, false);
            } catch (XAException e) {
                throw new AssertionError(e.getMessage(), e);
            }
        }

        private Association runAndReset(SQLRunnable r, SQLRunnable rollbackRunnable, boolean onePhaseCommit)
            throws SQLException, XAException {
            // If rollbackRunnable is non-null, then we're doing a commit.
            Association a;
            SQLException sqlException = null;
            try {
                r.run();
                if (LOGGER.isLoggable(Level.FINE)) {
                    boolean committed = rollbackRunnable != null;
                    StringBuilder message = new StringBuilder(committed ? "Committed " : "Rolled back ");
                    message.append("connection (").append(this.connection()).append(") ");
                    if (committed) {
                        message.append("with ").append(onePhaseCommit ? "one-phase " : "two-phase ").append("semantics ");
                    }
                    message.append("in Association ").append(this);
                    LOGGER.logp(Level.FINE, this.getClass().getName(), "runAndReset",
                                message.toString());
                }
            } catch (SQLException e) {
                sqlException = e;
                if (rollbackRunnable != null) {
                    try {
                        rollbackRunnable.run();
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.logp(Level.FINE, this.getClass().getName(), "runAndReset",
                                        "Rolled back connection ({0}) in Association {2}",
                                        new Object[] {this.connection(), this});
                        }
                        if (onePhaseCommit) {
                            // localXAResource.commit(someXid, true) caused us to try to call
                            // someConnection.commit(). That failed, and we successfully rolled back. Now we have to
                            // throw an XAException that indicates all this.
                            throw (XAException) new XAException(XA_RBROLLBACK).initCause(e);
                        }
                    } catch (SQLException e2) {
                        e.setNextException(e2);
                    }
                }
            } finally {
                try {
                    a = this.reset();
                } catch (SQLException e) {
                    a = null;
                    if (sqlException == null) {
                        sqlException = e;
                    } else if (sqlException != e) {
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
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, this.getClass().getName(), "reset",
                            "Transitioning Association {0} from state {1} to state NON_EXISTENT_TRANSACTION",
                            new Object[] {this, this.branchState()});
            }
            return new Association(BranchState.NON_EXISTENT_TRANSACTION,
                                   this.xid(),
                                   false,
                                   connection,
                                   this.priorAutoCommit());
        }

        private static boolean autoCommit(Connection c) {
            try {
                return c.getAutoCommit();
            } catch (SQLException e) {
                throw new UncheckedSQLException(e);
            }
        }

        // Transaction Branch States (XA specification, table 6-4):
        // S0: Non-existent Transaction
        // S1: Active
        // S2: Idle
        // S3: Prepared
        // S4: Rollback Only
        // S5: Heuristically Completed
        enum BranchState {
            NON_EXISTENT_TRANSACTION, // S0
            ACTIVE, // S1
            IDLE, // S2
            PREPARED, // S3
            ROLLBACK_ONLY, // S4
            HEURISTICALLY_COMPLETED; // S5
        }

    }

    private static final class IllegalTransitionException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        private IllegalTransitionException(String message) {
            super(message);
        }

    }

}
