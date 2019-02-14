package io.helidon.grpc.server;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author jk  2017.11.02
 */
public class RpcRuntimeException
        extends RuntimeException
        implements Serializable
    {
    // ----- constructors ---------------------------------------------------

    public RpcRuntimeException(RpcStatus status)
        {
        super(Objects.requireNonNull(status).getExceptionMessage(), status.getCause());

        m_status = status;
        }

    // ----- RpcRuntimeException methods ------------------------------------

    public RpcStatus getStatus()
        {
        return m_status;
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

        RpcRuntimeException that = (RpcRuntimeException) other;

        return Objects.equals(m_status, that.m_status);
        }

    @Override
    public int hashCode()
        {
        return Objects.hash(m_status);
        }

    // ----- data members ---------------------------------------------------

    private RpcStatus m_status;
    }
