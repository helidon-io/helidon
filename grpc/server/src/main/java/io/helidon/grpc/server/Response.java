package io.helidon.grpc.server;

import java.io.Serializable;

import java.util.concurrent.CompletableFuture;

/**
 * An interface that is used to send the response back to the client.
 *
 * @author Aleksandar Seovic  2017.09.19
 */
public interface Response<T>
    {
    /**
     * Write a single value into the response.
     * <p>
     * This method can be called multiple times, but it cannot be called after
     * either {@link #onCompleted} or {@link #onError} are called.
     *
     * @param value the value to write
     */
    void onNext(T value);

    /**
     * Exceptionally complete the response.
     * <p>
     * This method can only be called once, to terminate request with the
     * exception.
     *
     * @param t an exception that caused the request to fail
     */
    void onError(Throwable t);

    /**
     * Complete the request.
     * <p>
     * This method can only be called once, to terminate request successfully.
     */
    void onCompleted();

    /**
     * Write a single value into the response and terminate the request.
     * <p>
     * This method will write the result of specified {@code value} into the
     * response by calling {@link #onNext} and then {@link #onCompleted}.
     *
     * @param value the value to write into the response
     */
    default void send(T value)
        {
        send(CompletableFuture.completedFuture(value));
        }

    /**
     * Write a single value into the response and terminate the request.
     * <p>
     * This method will write the result of specified {@code future} into the
     * response by calling {@link #onNext} and then {@link #onCompleted} if the
     * specified {@code future} completes successfully. Otherwise, it will
     * terminate the request by calling {@link #onError}.
     *
     * @param future the future whose result to write into the response on
     *               completion
     */
    default void send(CompletableFuture<T> future)
        {
        future.whenCompleteAsync((result, error) ->
                            {
                            if (error != null)
                                {
                                onError(error);
                                }
                            else
                                {
                                onNext(result);
                                onCompleted();
                                }
                            });
        }

    /**
     * Complete the streaming response.
     * <p>
     * This method doesn't actually write any values into the response
     * directly,
     * but simply terminates the request either by calling {@link #onCompleted}
     * or {@link #onError}, based on whether the {@code future} completed
     * successfully or exceptionally.
     * <p>
     * The responsibility for actually writing the stream of values into the
     * response is on the caller of this method, using whatever mechanism it
     * has to do so.
     * <p>
     * For example, {@code GetAll} request in Cache API is implemented as:
     * <p>
     * <pre>
     * response.stream(
     *          getCache().async()
     *                    .getAll(keys, (key, value) -> response.onNext(new Entry<>(key, value))));
     * </pre>
     *
     * @param future the future whose result to write into the response on
     *               completion
     */
    default void stream(CompletableFuture<Void> future)
        {
        future.whenComplete((result, error) ->
                            {
                            if (error != null)
                                {
                                onError(error);
                                }
                            else
                                {
                                onCompleted();
                                }
                            });
        }

    /**
     * Represents a single value that can be written into the response.
     */
    class Result<T>
            implements Serializable
        {
        public Result()
            {
            }

        Result(T value)
            {
            this.value = value;
            }

        public T getValue()
            {
            return value;
            }

        private T value;

        /**
         * A singleton NULL instance.
         */
        public static final Result NULL = new Result();
        }
    }
