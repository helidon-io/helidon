# io.helidon.media.common.SubscriberInputStream

This document provides additional details about the implementation of `PublisherInputStream`.

## Implementation

The input stream implementation is not thread-safe: concurrent accesses should not be 
allowed, and even invocations of `read()` should be synchronized by out-of-band means for 
any stream state updates to be visible across threads.
 
The following assumptions are made about the operation of the stream:
 
- `Subscription.request` is invoked only after one chunk has been consumed
- the number of chunks requested is always 1
- Publisher fully conforms to the Flow.Publisher in the reactive-streams specification with respect to:
  - total order of `onNext`/`onComplete`/`onError`
  - strictly heeding backpressure (not calling onNext until more chunks were requested)
  - relaxed ordering of calls to request - allows to request even after onComplete/onError
 
 Given the assumptions that the number of chunks requested is at most 1, the requests are totally
 ordered with `onSubscribe`/`onNext` by construction. This affords the following safety guarantees:
 
  1. The only place where this.next is assigned is in onNext, before the next chunk is published
  2. Initially this.next and this.current are identical; one request(1) is called on subscription
  3. All subsequent calls to request(1) happen after the publishing of the chunk is observed by read(...)
 
  4. It follows from 3 and 1 that one and only one assignment to this.next happens before observing the
  chunk by read(...). (provided the Publisher observes backpressure)
 
  5. Such this.next is never lost, because it is copied into this.current before request(1), therefore
     a new assignment of this.next in onNext never loses the reference to a Future with an unobserved chunk
     (provided the Publisher observes backpressure)
 
  6. The publishing of the chunk by onNext synchronizes-with the observation of the chunk by a read(...):
     (1) and (5) ensure this.current observed by read(...) is the same as this.next at the time onNext
     is invoked, so onNext completes the same Future as accessed by read(...). Moreover, the store to
     this.next by onNext and load of this.next by read(...) are in happens-before relationship due to this
     synchronizes-with edge, the program order in onNext, and program order in read(...) (and out-of-bands
     synchronization between multiple reads)
 
  A conforming Publisher establishes total order of onNext, therefore, a total order of assignments to
  this.next and Future.complete:
 
  - onSubscribe: assert: this.current == this.next
    - request(1)
 
  - onNext: assert: this.current == this.next
    - prev = next
    - next = new Future       (A)
    - prev.complete(chunk)    (B): assert: prev == this.current
 
  - read(...)
    - current.get(): (C): (C) synchronizes-with (B): any read is blocked until (B)
  
  - read(...) (same or subsequent read)
    - current.get(): synchronizes-with (B)
    - chunk is seen to be consumed entirely: release the chunk, and request next:
    - current = next:         (D): (A) happens-before (D), no further onNext intervenes
                              invariant: this.current never references a released chunk as seen by close(...),
                                       assuming read(...) and close(...) are totally ordered - either by
                                       program order, or through out-of-bands synchronization
    - request(1): assert: a conforming Publisher does not invoke onNext before this
 
  - onNext: assert: this.current == this.next: a conforming Publisher does not invoke onNext before request(1)
    - prev = next
    - next = new Future       (E)
    - prev.complete(chunk)    (F): assert: prev == this.current
 
  - read(...)
    - current.get(): (G): (G) synchronizes-with (F): any read after (D) is blocked until (F)
  
 
  - onComplete / onError: assert: this.next has not been completed: stream is either empty (no onNext will ever
                                  be called), or an onNext assigned a new uncompleted Future to this.next
    - next.complete(...): (H): assert: conforming Publisher ensures this.next assignments by onNext are visible here
                                  by totally ordering onNext / onComplete / onError
 
  - read(...): assert: eventually this.current == this.next: either initially, or after some read that consumed
                       the chunk in its entirety and requested the new chunk
    - current.get(): (I): (I) synchronizes-with (H)
    - signal EOF
 
  - close(...): assert: this.current never references a released chunk; it either eventually references a chunk
                        that has been produced by onNext and has not been consumed fully by read(...), or a null
                        produced by onComplete / onError
                assert: if this.next != this.current, this.next will never produce a new chunk: this is the case
                        if and only if onNext has occurred, but read(...) has not consumed the chunk in its entirety,
                        hence has not requested any new chunks
    - current.whenComplete(release)
