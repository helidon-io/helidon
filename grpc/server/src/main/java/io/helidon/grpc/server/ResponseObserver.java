/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */

package io.helidon.grpc.server;


import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Objects;


/**
 * Adapts gRPC {@link StreamObserver} to {@link Response}.
 *
 * @author Aleksandar Seovic  2017.09.19
 */
public class ResponseObserver<T>
        implements Response<T>
    {
    // ----- constructors ---------------------------------------------------

    /**
     * Construct {@link ResponseObserver} instance.
     *
     * @param observer the stream observer to adapt
     */
    public ResponseObserver(StreamObserver<T> observer)
        {
        this.observer = observer;
        }

    // ----- Response methods -----------------------------------------------

    @SuppressWarnings("unchecked")
    @Override
    public void onNext(T value)
        {
        Objects.requireNonNull(value);
        try
            {
            observer.onNext(value);
            }
        catch (Throwable t)
            {
            throw toRpcRuntimeException(t);
            }
        }

    @Override
    public void onError(Throwable t)
        {
        try
            {
            Errors.handleError(t, observer, () -> "Caught exception sending onError", true);
            }
        catch (IllegalStateException e)
            {
            if (!e.getMessage().equals("call already closed"))
                {
                throw e;
                }
            }
        catch (Throwable throwable)
            {
            throw toRpcRuntimeException(throwable);
            }
        }

    @Override
    public void onCompleted()
        {
        try
            {
            observer.onCompleted();
            }
        catch (IllegalStateException e)
            {
            if (!e.getMessage().equals("call already closed"))
                {
                throw e;
                }
            }
        catch (Throwable t)
            {
            throw toRpcRuntimeException(t);
            }
        }

    // ----- helper methods -------------------------------------------------

    /**
     * Ensure that the specified {@link Throwable} is an
     * instance of a {@link StatusException}.
     *
     * @param t  the {@link Throwable} to ensure
     *
     * @return  the {@link Throwable} if it is a {@link StatusException}
     *          otherwise a new {@link StatusException} wrapping the
     *          {@link Throwable}.
     */
    StatusException toStatusException(Throwable t)
        {
        if (t == null || t instanceof StatusException)
            {
            return (StatusException) t;
            }

        Status status;

        if (t instanceof SecurityException)
            {
            status = Status.PERMISSION_DENIED.withCause(t);
            }
        else
            {
            status = Status.fromThrowable(t);
            }

        return new StatusException(status);
        }

    /**
     * Ensure that the specified {@link Throwable} is an
     * instance of a {@link RpcRuntimeException}.
     *
     * @param throwable  the {@link Throwable} to ensure
     *
     * @return  the {@link Throwable} if it is a {@link RpcRuntimeException}
     *          otherwise a new {@link RpcRuntimeException} wrapping the
     *          {@link Throwable}.
     */
    RpcRuntimeException toRpcRuntimeException(Throwable throwable)
        {
        if (throwable instanceof RpcRuntimeException)
            {
            return (RpcRuntimeException) throwable;
            }

        Status status;
        if (throwable instanceof StatusException)
            {
            status = ((StatusException) throwable).getStatus();
            }
        else if (throwable instanceof StatusRuntimeException)
            {
            status = ((StatusRuntimeException) throwable).getStatus();
            }
        else
            {
            status = Status.UNKNOWN;
            }


        if (status == null)
            {
            status = Status.UNKNOWN;
            }

        return RpcStatus.fromCode(status.getCode().value()).withCause(throwable).asException();
        }

    // ----- data members ---------------------------------------------------

    /**
     * The wrapped {@link StreamObserver}.
     */
    private StreamObserver<T> observer;
    }
