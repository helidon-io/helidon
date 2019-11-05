package io.helidon.microprofile.reactive;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Consumer;

public class ConsumableSubscriber<T> implements Subscriber<T> {

    private Consumer<T> onNext;

    public ConsumableSubscriber(Consumer<T> onNext) {
        this.onNext = onNext;
    }

    @Override
    public void onSubscribe(Subscription s) {
        System.out.println(s);

    }

    @Override
    public void onNext(T o) {
        onNext.accept(o);
    }

    @Override
    public void onError(Throwable t) {

    }

    @Override
    public void onComplete() {

    }
}
