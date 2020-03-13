package io.helidon.microprofile.reactive;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cancel the upstream immediately upon subscription and complete a CompletableFuture.
 * @param <T> the element type of the upstream
 */
final class BasicCancelSubscriber<T>
extends AtomicReference<Flow.Subscription>
implements Flow.Subscriber<T> {

    private static final long serialVersionUID = -1718297417587197143L;

    private final CompletableFuture<T> completable = new CompletableFuture<>();

    CompletableFuture<T> completable() {
        return completable;
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        if (SubscriptionHelper.setOnce(this, s)) {
            SubscriptionHelper.cancel(this);
            completable.complete(null);
        }
    }

    @Override
    public void onNext(T t) {
        Objects.requireNonNull(t, "t is null");
    }

    @Override
    public void onError(Throwable t) {
        Objects.requireNonNull(t, "t is null");
        // ignored for now
    }

    @Override
    public void onComplete() {
        // ignored
    }

}
