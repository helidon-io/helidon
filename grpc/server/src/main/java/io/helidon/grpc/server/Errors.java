package io.helidon.grpc.server;


import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Utility class of error handling methods.
 *
 * @author jk  2018.11.30
 */
public abstract class Errors
    {
    // ----- constructors ---------------------------------------------------

    /**
     * Private constructor for utility class.
     */
    private Errors()
        {
        }

    // ----- Errors methods ------------------------------------------------

    /**
     * Determine how to handle the specified error.
     *
     * @param error     the error to handle
     * @param observer  the {@link StreamObserver} to potentially notify of the error
     * @param suppMsg   the log message {@link Supplier}
     */
    public static void handleError(Throwable error,
                                   StreamObserver observer,
                                   Supplier<String> suppMsg)
        {
        handleError(error, observer, suppMsg, true);
        }

    /**
     * Determine how to handle the specified error.
     *
     * @param error     the error to handle
     * @param observer  the {@link StreamObserver} to potentially notify of the error
     * @param suppMsg   the log message {@link Supplier}
     * @param fNotify   {@code true} to notify the caller of the error
     */
    public static void handleError(Throwable error,
                                   StreamObserver observer,
                                   Supplier<String> suppMsg,
                                   boolean          fNotify)
        {
        boolean fCancelled = false;

        if (error instanceof RpcRuntimeException)
            {
            RpcStatus status = ((RpcRuntimeException) error).getStatus();

            if (status.getCode() == RpcStatus.Code.CANCELLED)
                {
                fCancelled = true;
                }
            }
        else if (error instanceof StatusRuntimeException)
            {
            Status status = ((StatusRuntimeException) error).getStatus();

            if (status.getCode() == Status.Code.CANCELLED)
                {
                fCancelled = true;
                }
            }
        else if (error instanceof StatusException)
            {
            Status status = ((StatusException) error).getStatus();

            if (status.getCode() == Status.Code.CANCELLED)
                {
                fCancelled = true;
                }
            }

        if (fCancelled)
            {
            // A CANCELLED status means the caller has probably disconnected so
            // there is nothing we can do, there is no point logging
            // the stack trace
            LOGGER.finest(suppMsg.get() + " - Client disconnected");
            }
        else
            {
            // The cause was something other than client disconnection
            // so log the error and potentially inform the caller
            LOGGER.log(Level.SEVERE, suppMsg.get(), error);
            }

        try
            {
            if (fNotify)
                {
                Throwable grpcError;
                Status grpcStatus;

                if (error instanceof RpcRuntimeException)
                    {
                    // If this is a RpcRuntimeException it potentially contains
                    // a grpc StatusRuntimeException so send that to the caller instead
                    // otherwise convert the RpcRuntimeException to a StatusRuntimeException
                    Throwable cause = error.getCause();

                    if (cause instanceof StatusRuntimeException)
                        {
                        grpcError  = cause;
                        //grpcStatus = ((StatusRuntimeException) cause).getStatus();
                        }
                    else if (cause instanceof StatusException)
                        {
                        grpcError  = cause;
                        //grpcStatus = ((StatusException) cause).getStatus();
                        }
                    else
                        {
                        RpcRuntimeException rpcException = (RpcRuntimeException) error;
                        RpcStatus           status       = rpcException.getStatus();
                        int                 nCode        = status.getCode().value();
                        Status.Code         code         = Arrays.stream(Status.Code.values())
                                                                 .filter(c -> c.value() == nCode)
                                                                 .findFirst()
                                                                 .orElse(Status.Code.UNKNOWN);

                        grpcStatus = Status.fromCode(code)
                                           .withCause(rpcException.getCause())
                                           .withDescription(status.getMessage());

                        grpcError  = grpcStatus.asRuntimeException();
                        }
                    }
                else if (error instanceof SecurityException)
                    {
                    grpcStatus = Status.PERMISSION_DENIED;
                    grpcError  = grpcStatus.withCause(error).asRuntimeException();
                    }
                else
                    {
                    grpcStatus = Status.INTERNAL;
                    grpcError  = grpcStatus.withCause(error).asRuntimeException();
                    }

                observer.onError(grpcError);
                }
            }
        catch (Throwable t)
            {
            // Calling onError might also throw an exception so we need to handle it
            // but there is no point this handler trying to inform the caller again
            handleError(t, observer, () -> "Caught exception sending onError", false);
            }
        }

    // ----- constants ---------------------------------------------------------------

    /**
     * The {@link Logger} to use.
     */
    private static final Logger LOGGER = Logger.getLogger(Errors.class.getName());
    }
