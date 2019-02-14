package io.helidon.grpc.server;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author jk  2017.11.02
 */
public class RpcStatus
        implements Serializable
    {
    // ----- constructors ---------------------------------------------------

    /**
     * Create a {@link RpcStatus} from the specified {@link Code}.
     *
     * @param code      the {@link Code} for this {@link RpcStatus}
     */
    private RpcStatus(Code code)
        {
        this(code, null, null);
        }

    /**
     * Create a {@link RpcStatus}.
     *
     * @param code      the {@link Code} for this {@link RpcStatus}
     * @param sMessage  the optional status message
     * @param cause     the optional cause of this status
     */
    private RpcStatus(Code code, String sMessage, Throwable cause)
        {
        m_code     = Objects.requireNonNull(code);
        m_sMessage = sMessage;
        m_cause    = cause;
        }

    // ----- RpcStatus methods ----------------------------------------------

    /**
     * Obtain a copy of this {@link RpcStatus}
     * with the addition of the specified message.
     *
     * @param sMessage  the message to assign to the new {@link RpcStatus}
     *
     * @return  a copy of this {@link RpcStatus}
     *          with the addition of the specified message
     */
    public RpcStatus withMessage(String sMessage)
        {
        return Objects.equals(sMessage, m_sMessage) ? this : new RpcStatus(m_code, sMessage, m_cause);
        }

    /**
     * Obtain a copy of this {@link RpcStatus}
     * with the addition of the specified cause.
     *
     * @param throwable  the cause to assign to the new {@link RpcStatus}
     *
     * @return  a copy of this {@link RpcStatus}
     *          with the addition of the specified cause
     */
    public RpcStatus withCause(Throwable throwable)
        {
        return Objects.equals(throwable, m_cause) ? this : new RpcStatus(m_code, m_sMessage, throwable);
        }

    /**
     * Obtain the status {@link Code} for this {@link RpcStatus}.
     *
     * @return  the status {@link Code} for this {@link RpcStatus}
     */
    public Code getCode()
        {
        return m_code;
        }

    /**
     * Obtain the status message for this {@link RpcStatus}.
     *
     * @return  the status message for this {@link RpcStatus}
     */
    public String getMessage()
        {
        return m_sMessage;
        }

    /**
     * Obtain the status exception message for this {@link RpcStatus}.
     *
     * @return  the status exception message for this {@link RpcStatus}
     */
    public String getExceptionMessage()
        {
        return m_sMessage == null ? m_code.toString() : m_code + ": " + m_sMessage;
        }

    /**
     * Obtain the optional {@link Throwable} cause
     * associated with this {@link RpcStatus}.
     *
     * @return  the optional {@link Throwable} cause
     *          associated with this {@link RpcStatus}
     */
    public Throwable getCause()
        {
        return m_cause;
        }

    // ----- object methods -------------------------------------------------

    @Override
    public boolean equals(Object other)
        {
        if (this == other)
            {
            return true;
            }

        if (other == null || getClass() != other.getClass())
            {
            return false;
            }

        RpcStatus rpcStatus = (RpcStatus) other;

        return m_code == rpcStatus.m_code &&
                Objects.equals(m_sMessage, rpcStatus.m_sMessage) &&
                Objects.equals(m_cause, rpcStatus.m_cause);
        }

    @Override
    public int hashCode()
        {
        return Objects.hash(m_code, m_sMessage, m_cause);
        }

    @Override
    public String toString()
        {
        return "RpcStatus(" + m_code
                + (m_sMessage == null ? "" : " " + m_sMessage)
                + (m_cause == null ? "" : " " + m_cause.getMessage())
                + ")";
        }

    // ----- helper methods -------------------------------------------------

    /**
     * Obtain this {@link RpcStatus} as an {@link RpcRuntimeException}.
     *
     * @return  this {@link RpcStatus} as an {@link RpcRuntimeException}
     */
    public RpcRuntimeException asException()
        {
        return new RpcRuntimeException(this);
        }

    /**
     * Obtain a {@link RpcStatus} from the matching code.
     * <p>
     * If the {@code code} parameter does not match any
     * {@link Code} enum value then {@link #UNKNOWN} is
     * returned.
     *
     * @param nCode  the code to use to create a {@link RpcStatus}
     *
     * @return  a {@link RpcStatus} from the matching code
     */
    public static RpcStatus fromCode(int nCode)
        {
        Code code = Arrays.stream(Code.values())
                          .filter(c -> c.m_nValue == nCode)
                          .findFirst()
                          .orElse(Code.UNKNOWN);

        return new RpcStatus(code);
        }

    // ----- inner enum: Code -----------------------------------------------

    /**
     * An enum representing status codes.
     */
    public enum Code
        {
        /**
         * The operation completed successfully.
         */
        OK(0),
        /**
         * The operation was cancelled (typically by the caller).
         */
        CANCELLED(1),
        /**
         * Unknown error.
         */
        UNKNOWN(2),
        /**
         * Client specified an invalid argument.
         */
        INVALID_ARGUMENT(3),
        /**
         * Deadline expired before operation could complete.
         */
        DEADLINE_EXCEEDED(4),
        /**
         * Some requested entity (e.g., file or directory) was not found.
         */
        NOT_FOUND(5),
        /**
         * Some entity that we attempted to create (e.g., file or directory)
         * already exists.
         */
        ALREADY_EXISTS(6),
        /**
         * The caller does not have permission to execute the specified operation.
         */
        PERMISSION_DENIED(7),
        /**
         * Some resource has been exhausted, perhaps a per-user quota, or perhaps the
         * entire file system is out of space.
         */
        RESOURCE_EXHAUSTED(8),
        /**
         * Operation was rejected because the system is not in a state required
         * for the operation's execution.
         */
        FAILED_PRECONDITION(9),
        /**
         * The operation was aborted, typically due to a concurrency issue
         * like sequencer check failures, transaction aborts, etc.
         */
        ABORTED(10),
        /**
         * Operation was attempted past the valid range.
         */
        OUT_OF_RANGE(11),
        /**
         * Operation is not implemented or not supported/enabled in this service.
         */
        UNIMPLEMENTED(12),
        /**
         * Internal errors.
         */
        INTERNAL(13),
        /**
         * The service is currently unavailable.
         */
        UNAVAILABLE(14),
        /**
         * Unrecoverable data loss or corruption.
         */
        DATA_LOSS(15),
        /**
         * The request does not have valid authentication credentials for the operation.
         */
        UNAUTHENTICATED(16);

        // ----- constructors -----------------------------------------------

        Code(int nValue)
            {
            m_nValue = nValue;
            }

        // ----- Code methods -----------------------------------------------

        public int value()
            {
            return m_nValue;
            }

        // ----- data members -----------------------------------------------

        /**
         * The {@code int} value of this {@link Code}.
         */
        private final int m_nValue;
        }

    // ----- constants ------------------------------------------------------

    /**
     * The operation completed successfully.
     */
    public static final RpcStatus OK = new RpcStatus(Code.OK);

    /**
     * The operation was cancelled (typically by the caller).
     */
    public static final RpcStatus CANCELLED = new RpcStatus(Code.CANCELLED);

    /**
     * Unknown error. See Status.Code.UNKNOWN.
     */
    public static final RpcStatus UNKNOWN = new RpcStatus(Code.UNKNOWN);

    /**
     * Client specified an invalid argument.
     */
    public static final RpcStatus INVALID_ARGUMENT = new RpcStatus(Code.INVALID_ARGUMENT);

    /**
     * Deadline expired before operation could complete.
     */
    public static final RpcStatus DEADLINE_EXCEEDED = new RpcStatus(Code.DEADLINE_EXCEEDED);

    /**
     * Some requested entity (e.g., file or directory) was not found.
     */
    public static final RpcStatus NOT_FOUND = new RpcStatus(Code.NOT_FOUND);

    /**
     * Some entity that we attempted to create (e.g., file or directory) already exists.
     */
    public static final RpcStatus ALREADY_EXISTS = new RpcStatus(Code.ALREADY_EXISTS);

    /**
     * The caller does not have permission to execute the specified operation.
     */
    public static final RpcStatus PERMISSION_DENIED = new RpcStatus(Code.PERMISSION_DENIED);

    /**
     * Some resource has been exhausted, perhaps a per-user quota, or perhaps the entire file system is out of space.
     */
    public static final RpcStatus RESOURCE_EXHAUSTED = new RpcStatus(Code.RESOURCE_EXHAUSTED);

    /**
     * Operation was rejected because the system is not in a state required for the
     * operation's execution.
     */
    public static final RpcStatus FAILED_PRECONDITION = new RpcStatus(Code.FAILED_PRECONDITION);

    /**
     * The operation was aborted, typically due to a concurrency issue like sequencer
     * check failures, transaction aborts, etc
     */
    public static final RpcStatus ABORTED = new RpcStatus(Code.ABORTED);

    /**
     * Operation was attempted past the valid range.
     */
    public static final RpcStatus OUT_OF_RANGE = new RpcStatus(Code.OUT_OF_RANGE);

    /**
     * Operation is not implemented or not supported/enabled in this service.
     */
    public static final RpcStatus UNIMPLEMENTED = new RpcStatus(Code.UNIMPLEMENTED);

    /**
     * Internal errors.
     */
    public static final RpcStatus INTERNAL = new RpcStatus(Code.INTERNAL);

    /**
     * The service is currently unavailable.
     */
    public static final RpcStatus UNAVAILABLE = new RpcStatus(Code.UNAVAILABLE);

    /**
     * Unrecoverable data loss or corruption.
     */
    public static final RpcStatus DATA_LOSS = new RpcStatus(Code.DATA_LOSS);

    /**
     * The request does not have valid authentication credentials for the operation.
     */
    public static final RpcStatus UNAUTHENTICATED = new RpcStatus(Code.UNAUTHENTICATED);

    // ----- data members ---------------------------------------------------

    /**
     * The status {@link Code}
     */
    private final Code m_code;

    /**
     * An optional description of this status.
     */
    private String m_sMessage;

    /**
     * An optional cause of this status.
     */
    private Throwable m_cause;
    }
