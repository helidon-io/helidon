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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static javax.transaction.xa.XAException.XAER_PROTO;
import static javax.transaction.xa.XAException.XAER_RMERR;
import static javax.transaction.xa.XAException.XA_HEURCOM;
import static javax.transaction.xa.XAException.XA_HEURHAZ;
import static javax.transaction.xa.XAException.XA_HEURMIX;
import static javax.transaction.xa.XAException.XA_HEURRB;
import static javax.transaction.xa.XAException.XA_RBBASE;
import static javax.transaction.xa.XAException.XA_RBEND;
import static javax.transaction.xa.XAException.XA_RETRY;
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
 * An {@link XAResource} implementation that allows an XA-unaware
 * {@link Connection} to take part in JTA-managed transactions.
 *
 * @see #start(Xid, int)
 *
 * @see #prepare(Xid)
 *
 * @see #commit(Xid, boolean)
 *
 * @see #rollback(Xid)
 *
 * @see #end(Xid, int)
 *
 * @see #forget(Xid)
 *
 * @see #recover(int)
 *
 * @see XAResource
 *
 * @see javax.sql.XAConnection
 *
 * @see LocalXADataSource
 */
public final class LocalXAResource implements XAResource {


    /*

      digraph XA {
  s0 [label="Non-existent\nTransaction\n(s0)"];
  s1 [label="Active\n(s1)"];
  s2 [label="Idle\n(s2)"];
  s3 [label="Prepared\n(s3)"];
  s4 [label="Rollback\nOnly\n(s4)"];
  s5 [label="Heuristically\nCompleted\n(s5)"];
  
  s0 -> s1 [label="start()"];
  s0:ne -> s0:n [headlabel="recover()"];
  
  s1:n -> s1:nw [headlabel="recover()"];
  s1 -> s2 [label="end()"];
  s1 -> s4 [label="end() [RB]"];
  
  s2 -> s0 [label="prepare() [RB, RDONLY]"];
  s2 -> s0 [label="commit() [RB, OK, ERR]"];
  s2 -> s0 [label="rollback() [RB, OK, ERR]"];
  s2 -> s1 [label="start()"];
  s2 -> s2 [label="recover()"];
  s2 -> s3 [label="prepare()"];
  s2 -> s4 [label="start() [RB]"];
  s2 -> s5 [label="commit() [HEUR]"]

  s3 -> s0 [label="commit()"];
  s3 -> s0 [label="rollback()"];
  s3 -> s3 [headlabel="commit() [RETRY]"];
  s3:se -> s3:se [headlabel="recover()"];
  s3 -> s5 [label="commit() [HEUR]"]
  s3 -> s5 [label="rollback() [HEUR]"];

  s4 -> s0 [label="rollback() [RB, OK, ERR]"];
  s4 -> s4 [label="recover()"];
  s4 -> s5 [label="rollback() [HEUR]"];

  s5 -> s0 [label="forget()"];
  s5:sw -> s5:sw [headlabel="commit() [HEUR]"];
  s5:s -> s5:s [headlabel="forget() [ERR]"];
  s5:se -> s5:se [headlabel="rollback() [HEUR]"];
  s5 -> s5 [headlabel="recover()"];
}

     */


    /*
     * Static fields.
     */


    private static final Logger LOGGER = Logger.getLogger(LocalXAResource.class.getName());

    private static final Xid[] EMPTY_XID_ARRAY = new Xid[0];

    private static final VarHandle XID;

    static {
        try {
            XID = MethodHandles.lookup().findVarHandle(LocalXAResource.class, "xid", Xid.class);
        } catch (NoSuchFieldException | IllegalAccessException reflectiveOperationException) {
            throw new ExceptionInInitializerError(reflectiveOperationException);
        }
    }


    /*
     * Instance fields.
     */


    private final Connection physicalConnection;

    private volatile boolean oldAutoCommit;

    private volatile Xid xid;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link LocalXAResource} adapting the supplied
     * {@link Connection}.
     *
     * @param physicalConnection a {@link Connection}; must not be
     * {@code null}; must not be {@linkplain Connection#isClosed()
     * closed}
     *
     * @exception NullPointerException if {@code physicalConnection}
     * is {@code null}
     *
     * @exception SQLException if {@code physicalConnection}
     * {@linkplain Connection#isClosed() is closed}
     */
    public LocalXAResource(Connection physicalConnection) throws SQLException {
        super();
        if (physicalConnection.isClosed()) {
            throw new IllegalArgumentException("physicalConnection.isClosed()");
        }
        this.physicalConnection = physicalConnection;
    }


    /*
     * Instance methods.
     */


    /**
     * Called when a JTA transaction is starting.
     *
     * <p>If the supplied {@code xid} argument is {@code null}, this
     * method throws an {@link XAException}.</p>
     *
     * <p>When the supplied {@code flags} argument is {@link
     * XAResource#TMNOFLAGS TMNOFLAGS}, this is an indication that a
     * new transaction is beginning.  Consequently this method will
     * validate the state of this {@link LocalXAResource} and the
     * supplied arguments and will {@linkplain
     * Connection#setAutoCommit(boolean) turn off autocommit} on the
     * {@link Connection} that was supplied {@linkplain
     * #LocalXAResource(Connection) at construction time}.</p>
     *
     * <p>When the supplied {@code flags} argument is {@link
     * XAResource#TMRESUME TMRESUME}, this is an indication that a
     * prior transaction is resuming.  Consequently this method will
     * validate the state of this {@link LocalXAResource} and do
     * nothing else.</p>
     *
     * <p>When the supplied {@code flags} argument is {@link
     * XAResource#TMJOIN TMJOIN}, this method currently throws an
     * {@link XAException} indicating that this variant of invocation
     * is not currently supported.</p>
     *
     * <p>When the supplied {@code flags} argument is any other value,
     * this method throws an {@link XAException}.</p>
     *
     * @param xid the {@link Xid} identifying the transaction; must
     * not be {@code null}
     *
     * @param flags an {@code int} describing the kind of start; must
     * be one of {@link XAResource#TMNOFLAGS TMNOFLAGS}, {@link
     * XAResource#TMRESUME TMRESUME} or {@link XAResource#TMJOIN
     * TMJOIN}
     *
     * @exception XAException if {@code xid} is {@code null} or if
     * this {@link LocalXAResource} is not in a proper state or if an
     * error occurs while interacting with the underlying {@link
     * Connection}
     *
     * @see XAResource#start(Xid, int)
     */
    @Override // XAResource
    public void start(Xid xid, int flags) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "start", new Object[] { xid, flagsToString(flags) });
        }

        if (xid == null) {
            throw (XAException) new XAException(XAException.XAER_INVAL).initCause(new NullPointerException("xid"));
        }
        Xid existingXid = this.xid; // volatile read
        switch (flags) {

            /*

              https://jakarta.ee/specifications/transactions/2.0/jakarta-transactions-spec-2.0.html#transaction-association

              +--------------------+--------------------------------------------------------------------+
              | XAResource Methods | XAResource Transaction States                                      |
              |                    +---------------------+-----------------+----------------------------+
              |                    | Not Associated (T0) | Associated (T1) | Association Suspended (T2) |
              +--------------------+---------------------+-----------------+----------------------------+
              | start()            | T1                  |                 |                            |
              +--------------------+---------------------+-----------------+----------------------------+
              | start(TMRESUME)    |                     |                 | T1                         |
              +--------------------+---------------------+-----------------+----------------------------+
              | start(TMJOIN)      | T1                  |                 |                            |
              +--------------------+---------------------+-----------------+----------------------------+
              | end(TMSUSPEND)     |                     | T2              |                            |
              +--------------------+---------------------+-----------------+----------------------------+
              | end(TMFAIL)        |                     | T0              | T0                         |
              +--------------------+---------------------+-----------------+----------------------------+
              | end(TMSUCCESS)     |                     | T0              | T0                         |
              +--------------------+---------------------+-----------------+----------------------------+

              For example: if an XAResource is in state T1, and
              end(TMSUSPEND) is called, it transists to state T2. (T0,
              T1 and T2 are arbitrary state names, not transaction
              identifiers.)

            */

        case TMNOFLAGS:
            // A new transaction is starting.
            if (existingXid != null) {
                throw (XAException) new XAException(XAException.XAER_INVAL)
                    .initCause(new IllegalStateException("this.xid != null: " + existingXid));
            }
            try {
                Connection physicalConnection = this.physicalConnection; // volatile read
                boolean oldAutoCommit = physicalConnection.getAutoCommit();
                this.oldAutoCommit = oldAutoCommit; // volatile write
                if (oldAutoCommit) {
                  physicalConnection.setAutoCommit(false);
                }
            } catch (SQLException sqlException) {
                throw (XAException) new XAException(XAException.XAER_RMERR).initCause(sqlException);
            }
            this.xid = xid; // volatile write; move to state T1
            break;

        case TMRESUME:
            // A transaction was previously suspended and is now being
            // resumed.
            if (!xid.equals(existingXid)) {
                throw (XAException) new XAException(XAException.XAER_INVAL)
                    .initCause(new IllegalArgumentException("!xid.equals(this.xid): !" + xid + ".equals(" + existingXid + ")"));
            }
            break;

        case TMJOIN:
            // A transaction is in effect and it is desired that this
            // XAResource join it.  (I am not sure of this
            // interpretation, nor what to do.)
            throw (XAException) new XAException(XAException.XAER_RMERR)
                .initCause(new UnsupportedOperationException("xid: " + xid + "; flags: " + flagsToString(flags)));

        default:
            throw (XAException) new XAException(XAException.XAER_INVAL)
                .initCause(new IllegalArgumentException("flags: " + flagsToString(flags)));

        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "start");
        }
    }

    /**
     * Called when a JTA transaction is preparing to commit.
     *
     * <p>If the supplied {@code xid} argument is {@code null}, this
     * method throws an {@link XAException}.</p>
     *
     * <p>If {@link #start(Xid, int)} has not been previously invoked
     * by the controlling JTA transaction coordinator, this method
     * throws an {@link XAException}.</p>
     *
     * <p>Otherwise, this method <strong>does nothing further</strong>
     * and returns {@link XAResource#XA_OK XA_OK}.</p>
     *
     * @param xid the {@link Xid} identifying the transaction; must
     * not be {@code null}
     *
     * @exception XAException if {@code xid} is {@code null} or if
     * this {@link LocalXAResource} is not in a proper state
     *
     * @see #start(Xid, int)
     */
    @Override // XAResource
    public int prepare(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "prepare", xid);
        }

        if (xid == null) {
            throw (XAException) new XAException(XAException.XAER_INVAL).initCause(new NullPointerException("xid"));
        }
        Xid existingXid = this.xid; // volatile read
        if (existingXid == null) {
            // "Possible exception values are: XA_RB*, XAER_RMERR,
            // XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or XAER_PROTO"
            throw new XAException(XAException.XAER_PROTO);
        } else if (!xid.equals(existingXid)) {
            throw (XAException) new XAException(XAException.XAER_INVAL)
                .initCause(new IllegalArgumentException("!xid.equals(this.xid): !" + xid + ".equals(" + existingXid + ")"));
        }

        int returnValue;
        try {
            returnValue = this.physicalConnection.isReadOnly() ? XA_RDONLY : XA_OK;
        } catch (SQLException sqlException) {
            throw (XAException) new XAException(XAException.XAER_RMERR).initCause(sqlException);
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "prepare", returnValue);
        }
        return returnValue;
    }

    /**
     * Commits the current prepared transaction.
     *
     * <p>If the supplied {@code xid} argument is {@code null}, this
     * method throws an {@link XAException}.</p>
     *
     * <p>If {@link #start(Xid, int)} has not been previously invoked
     * by the controlling JTA transaction coordinator, this method
     * throws an {@link XAException}.</p>
     *
     * <p>Otherwise, {@link Connection#commit()} is invoked on the underlying {@link Connection}
     */
    @Override // XAResource
    public void commit(Xid xid, boolean onePhase) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "commit", new Object[] { xid, onePhase });
        }

        this.complete(xid, this.physicalConnection::commit);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "commit");
        }
    }

    @Override // XAResource
    public void rollback(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "rollback", xid);
        }

        this.complete(xid, this.physicalConnection::rollback);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "rollback");
        }
    }

    private void complete(Xid xid, SQLRunnable r) throws XAException {
        if (xid == null) {
            throw (XAException) new XAException(XAException.XAER_INVAL).initCause(new NullPointerException("xid"));
        }
        Xid existingXid = this.xid; // volatile read
        if (existingXid == null) {
            // "Possible XAExceptions [for commit; none specified for
            // rollback] are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB,
            // XA_HEURMIX, XAER_RMERR, XAER_RMFAIL, XAER_NOTA,
            // XAER_INVAL, or XAER_PROTO."
            throw new XAException(XAException.XAER_PROTO);
        } else if (!xid.equals(existingXid)) {
            throw (XAException) new XAException(XAException.XAER_INVAL)
                .initCause(new IllegalArgumentException("!xid.equals(this.xid): !" + xid + ".equals(" + existingXid + ")"));
        }
        Throwable t = null;
        try {
            r.run();
        } catch (Throwable t1) {
            t = t1;
        } finally {
            this.xid = null; // volatile write
            try {
                this.physicalConnection.setAutoCommit(this.oldAutoCommit); // volatile read
            } catch (Throwable t2) {
                if (t == null) {
                    if (t2 instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    } else if (t2 instanceof Error error) {
                        throw error;
                    } else {
                        throw (XAException) new XAException(XAException.XAER_RMERR).initCause(t2);
                    }
                } else {
                    t.addSuppressed(t2);
                    if (t instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    } else if (t instanceof Error error) {
                        throw error;
                    } else {
                        throw (XAException) new XAException(XAException.XAER_RMERR).initCause(t);
                    }
                }
            }
        }
    }

    @Override // XAResource
    public void end(Xid xid, int flags) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "end", new Object[] { xid, flagsToString(flags) });
        }

        if (xid == null) {
            throw (XAException) new XAException(XAException.XAER_INVAL).initCause(new NullPointerException("xid"));
        }
        switch (flags) {
        case TMFAIL:
        case TMSUCCESS:
        case TMSUSPEND:
            break;
        default:
            throw (XAException) new XAException(XAException.XAER_INVAL)
                .initCause(new IllegalArgumentException("flags: " + flagsToString(flags)));
        }
        Xid existingXid = this.xid; // volatile read
        if (existingXid == null) {
            throw new XAException(XAException.XAER_OUTSIDE);
        } else if (!xid.equals(existingXid)) {
            throw (XAException) new XAException(XAException.XAER_INVAL)
                .initCause(new IllegalArgumentException("!xid.equals(this.xid): !" + xid + ".equals(" + existingXid + ")"));
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "end");
        }
    }

    @Override // XAResource
    public void forget(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "forget", xid);
        }

        if (xid == null) {
            throw (XAException) new XAException(XAException.XAER_INVAL).initCause(new NullPointerException("xid"));
        }
        XID.compareAndSet(this, xid, null);

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
            throw (XAException) new XAException(XAException.XAER_INVAL)
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


    @FunctionalInterface
    private static interface SQLRunnable {

        public void run() throws SQLException;

    }

    // Unused at the moment; experimental
    private static class Branch {

        private volatile Xid xid;

        private final Connection physicalConnection;

        private volatile State state;

        private volatile AssociationState associationState;

        private Branch(Connection physicalConnection) {
            this(null, physicalConnection, State.NON_EXISTENT_TRANSACTION);
        }

        private Branch(Xid xid, Connection physicalConnection, State state) {
            super();
            this.physicalConnection = Objects.requireNonNull(physicalConnection, "physicalConnection");
            this.state = Objects.requireNonNull(state, "state");
            if (xid == null) {
                if (state != State.NON_EXISTENT_TRANSACTION) {
                    throw new IllegalArgumentException("state: " + state);
                }
                this.associationState = AssociationState.NOT_ASSOCIATED;
            } else if (state == State.ACTIVE) {
                // XA/Open Specification 6.3: "Any state listed in
                // Table 6-4 on page 62 [Transaction Branch States]
                // except state S1 [ACTIVE] is a valid initial state
                // for a thread of control."
                throw new IllegalArgumentException("state: " + state);
            } else {
                this.associationState = AssociationState.ASSOCIATION_SUSPENDED;
            }
        }

        private Xid xid() {
            return this.xid; // volatile read
        }

        private Connection physicalConnection() {
            return this.physicalConnection;
        }

        private State state() {
            return this.state; // volatile read
        }

        private AssociationState associationState() {
            return this.associationState; // volatile read
        }

        private State transist(EventType eventType, int code) throws XAException {
            State newState = this.state.transist(eventType, code); // volatile read
            this.state = newState; // volatile write
            if (newState == State.NON_EXISTENT_TRANSACTION) {
                this.xid = null; // volatile write
            }
            return newState;
        }

        private AssociationState transist(EventType eventType, int flags, int code) throws XAException {
            AssociationState newState = this.associationState.transist(eventType, flags, code); // volatile read
            this.associationState = newState; // volatile write
            if (newState == AssociationState.NOT_ASSOCIATED) {
                this.xid = null; // volatile write
            }
            return newState;
        }

        private static enum EventType {
            START() {
                @Override
                State transist(State state, int code) throws XAException {
                    switch (state) {
                    case NON_EXISTENT_TRANSACTION:
                        if (code == XA_OK) {
                            return State.ACTIVE;
                        }
                        break;
                    case IDLE:
                        if (code == XA_OK) {
                            return State.ACTIVE;
                        } else if (isRB(code)) {
                            return State.ROLLBACK_ONLY;
                        }
                        break;
                    default:
                        break;
                    }
                    return super.transist(state, code);
                }
                @Override
                AssociationState transist(AssociationState state, int flags, int code) throws XAException {
                    switch (state) {
                    case NOT_ASSOCIATED:
                        switch (flags) {
                        case TMNOFLAGS:
                            return AssociationState.ASSOCIATED;
                        default:
                            break;
                        }
                        break;
                    case ASSOCIATION_SUSPENDED:
                        switch (flags) {
                        case TMRESUME:
                            return isRB(code) ? AssociationState.NOT_ASSOCIATED : AssociationState.ASSOCIATED;
                        default:
                            break;
                        }
                        break;
                    default:
                        break;
                    }
                    return super.transist(state, flags, code);
                }
            },

            END() {
                @Override
                State transist(State state, int code) throws XAException {
                    switch (state) {
                    case ACTIVE:
                        if (code == XA_OK) {
                            return State.IDLE;
                        } else if (isRB(code)) {
                            return State.ROLLBACK_ONLY;
                        }
                        break;
                    default:
                        break;
                    }
                    return super.transist(state, code);
                }
                @Override
                AssociationState transist(AssociationState state, int flags, int code) throws XAException {
                    switch (state) {
                    case ASSOCIATED:
                        switch (flags) {
                        case TMSUSPEND:
                            return isRB(code) ? AssociationState.NOT_ASSOCIATED : AssociationState.ASSOCIATION_SUSPENDED;
                        case TMSUCCESS:
                        case TMFAIL:
                            return AssociationState.NOT_ASSOCIATED;
                        default:
                            break;
                        }
                        break;
                    case ASSOCIATION_SUSPENDED:
                        switch (flags) {
                        case TMSUCCESS:
                        case TMFAIL:
                            return AssociationState.NOT_ASSOCIATED;
                        default:
                            break;
                        }
                        break;
                    default:
                        break;
                    }
                    return super.transist(state, flags, code);
                }
            },

            PREPARE() {
                @Override
                    State transist(State state, int code) throws XAException {
                    switch (state) {
                    case IDLE:
                        switch (code) {
                        case XA_OK:
                            return State.PREPARED;
                        case XA_RDONLY:
                            return State.NON_EXISTENT_TRANSACTION;
                        case XAER_RMERR:
                            return State.IDLE;
                        default:
                            if (isRB(code)) {
                                return State.NON_EXISTENT_TRANSACTION;
                            }
                            break;
                        }
                        break;
                    default:
                        break;
                    }
                    return super.transist(state, code);
                }
            },

            COMMIT() {
                @Override
                State transist(State state, int code) throws XAException {
                    switch (state) {
                    case IDLE:
                        switch (code) {
                        case XA_OK:
                        case XAER_RMERR:
                            return State.NON_EXISTENT_TRANSACTION;
                        case XA_HEURCOM:
                        case XA_HEURHAZ:
                        case XA_HEURMIX:
                        case XA_HEURRB:
                            return State.HEURISTICALLY_COMPLETED;
                        default:
                            if (isRB(code)) {
                                return State.NON_EXISTENT_TRANSACTION;
                            }
                            break;
                        }
                        break;
                    case PREPARED:
                        switch (code) {
                        case XA_OK:
                        case XAER_RMERR:
                            return State.NON_EXISTENT_TRANSACTION;
                        case XA_RETRY:
                            return State.PREPARED;
                        case XA_HEURCOM:
                        case XA_HEURHAZ:
                        case XA_HEURMIX:
                        case XA_HEURRB:
                            return State.HEURISTICALLY_COMPLETED;
                        default:
                            break;
                        }
                        break;
                    case HEURISTICALLY_COMPLETED:
                        switch (code) {
                        case XA_HEURCOM:
                        case XA_HEURHAZ:
                        case XA_HEURMIX:
                        case XA_HEURRB:
                            return State.HEURISTICALLY_COMPLETED;
                        default:
                            break;
                        }
                        break;
                    default:
                        break;
                    }
                    return super.transist(state, code);
                }
            },

            ROLLBACK() {
                @Override
                State transist(State state, int code) throws XAException {
                    switch (state) {
                    case IDLE:
                        switch (code) {
                        case XA_OK:
                        case XAER_RMERR:
                            return State.NON_EXISTENT_TRANSACTION;
                        default:
                            if (isRB(code)) {
                                return State.NON_EXISTENT_TRANSACTION;
                            }
                            break;
                        }
                        break;
                    case PREPARED:
                    case ROLLBACK_ONLY:
                        switch (code) {
                        case XA_OK:
                        case XAER_RMERR:
                            return State.NON_EXISTENT_TRANSACTION;
                        case XA_HEURCOM:
                        case XA_HEURHAZ:
                        case XA_HEURMIX:
                        case XA_HEURRB:
                            return State.HEURISTICALLY_COMPLETED;
                        default:
                            if (isRB(code)) {
                                return State.NON_EXISTENT_TRANSACTION;
                            }
                            break;
                        }
                        break;
                    case HEURISTICALLY_COMPLETED:
                        switch (code) {
                        case XA_HEURCOM:
                        case XA_HEURHAZ:
                        case XA_HEURMIX:
                        case XA_HEURRB:
                            return State.HEURISTICALLY_COMPLETED;
                        default:
                            break;
                        }
                        break;
                    default:
                        break;
                    }
                    return super.transist(state, code);
                }
            },

            FORGET() {
                @Override
                State transist(State state, int code) throws XAException {
                    switch (state) {
                    case HEURISTICALLY_COMPLETED:
                        switch (code) {
                        case XA_OK:
                            return State.NON_EXISTENT_TRANSACTION;
                        case XAER_RMERR:
                            return State.HEURISTICALLY_COMPLETED;
                        default:
                            break;
                        }
                        break;
                    default:
                        break;
                    }
                    return super.transist(state, code);
                }
            },

            RECOVER() {
                @Override
                State transist(State state, int code) throws XAException {
                    switch (code) {
                    case XA_OK:
                        return state;
                    default:
                        break;
                    }
                    return super.transist(state, code);
                }
                @Override
                AssociationState transist(AssociationState state, int flags, int code) throws XAException {
                    if (state != null && flags == TMNOFLAGS && code == XA_OK) {
                        return state;
                    }
                    return super.transist(state, flags, code);
                }
            };

            State transist(State state, int code) throws XAException {
                throw (XAException) new XAException(XAER_PROTO)
                    .initCause(new IllegalStateException("state " + state + " cannot transist via event " + this + " and return code " + code));
            }

            AssociationState transist(AssociationState state, int flags, int code) throws XAException {
                throw (XAException) new XAException(XAER_PROTO)
                    .initCause(new IllegalStateException("state " + state + " cannot transist via event " + this + " with flags " + flagsToString(flags) + " and return code " + code));
            }

        }

        private static boolean isRB(int code) {
            return code >= XA_RBBASE && code <= XA_RBEND;
        }

        private static enum State {
            NON_EXISTENT_TRANSACTION,
            ACTIVE,
            IDLE,
            PREPARED,
            ROLLBACK_ONLY,
            HEURISTICALLY_COMPLETED;

            private State transist(EventType eventType, int code) throws XAException {
                return eventType.transist(this, code);
            }

        }

        private static enum AssociationState {
            NOT_ASSOCIATED,
            ASSOCIATED,
            ASSOCIATION_SUSPENDED;

            private AssociationState transist(EventType eventType, int flags, int code) throws XAException {
                return eventType.transist(this, flags, code);
            }
        }

        private static record Association(Xid xid, boolean suspended, Connection connection) {

            // States: (XA specification, table 6-2)
            // T0: Not Associated
            // T1: Associated
            // T2: Association Suspended
            
            private Association(Connection connection) {
                // T0
                this(null, false, connection);
            }
            
            private Association(Xid xid, Connection connection) {
                // T0 or T1
                this(xid, false, connection);
            }
            
            private Association {
                if (xid == null && suspended) {
                    // You can't suspend a not-associated transaction branch.
                    throw new IllegalArgumentException("xid: null; suspended: true");
                }
                // T0, T1 or T2
            }

            public boolean associated() {
                return this.xid() != null && !this.suspended();
            }
            
            private Association associate(Xid xid) {
                Objects.requireNonNull(xid, "xid");
                Connection connection = this.connection();
                if (connection == null || this.suspended()) {
                    throw new IllegalStateException();
                }
                Xid myXid = this.xid();
                if (myXid == null) {
                    return new Association(xid, false, connection);
                } else if (myXid.equals(xid)) {
                    return this;
                } else {
                    throw new IllegalStateException();
                }
            }

            private Association disassociate() {
                if (this.xid() == null) {
                    return this;
                }
                // T2 -> T0, T1 -> T0
                return new Association(null, false, this.connection());
            }
            
            private Association suspend() {
                if (this.suspended()) {
                    return this;
                }
                // T1 -> T2
                return new Association(this.xid(), true, this.connection());
            }

            private Association resume() {
                if (this.suspended()) {
                    // T2 -> T1
                    return new Association(this.xid(), false, this.connection());
                }
                return this;
            }

        }


        
    }

}
