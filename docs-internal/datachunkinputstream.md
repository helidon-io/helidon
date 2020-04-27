# io.helidon.media.common.DataChunkInputStream

This document provides additional details about the implementation of `DataChunkInputStream`.

## Implementation and Proof of Correctness

The input stream implementation is not thread-safe: concurrent accesses should not be 
allowed, and even invocations of `read(...)` should be synchronized by out-of-band means for 
any stream state updates to be visible across threads.
 
The following assumptions are made about the operation of the stream:
 
- `Subscription.request` is invoked only after one chunk has been consumed

- The number of chunks requested is always 1

- Publishers fully conforms to `Flow.Publisher` in the Reactive Streams Specification [I]
with respect to:
  - total order of `onNext`/`onComplete`/`onError`
  - strictly heeding backpressure (not calling `onNext` until more chunks were requested)
  - relaxed ordering of calls to request, allowing class after `onComplete`/`onError`
 
 Given the assumptions that the number of chunks requested is at most 1, the requests are totally
 ordered with `onSubscribe`/`onNext` by construction. This affords the following safety guarantees:
 
  1. The only place where `next` is assigned is in `onNext`, before the next chunk is published

  2. Initially `next` and `current` are identical; first `request(1)` is called on subscription

  3. All subsequent calls to `request(1)` happen after the publishing of the chunk is observed 
  by `read(...)`
 
  4. It follows from (3) and (1) that one and only one assignment to `next` happens before 
  observing the chunk by `read(...)` --provided the Publisher observes backpressure
 
  5. Such `next` is never lost, because it is copied into `current` before `request(1)`, 
  therefore a new assignment of `next` in `onNext` never loses the reference to a future 
  with an unobserved chunk --provided the Publisher observes backpressure
 
  6. The publishing of the chunk by `onNext` synchronizes-with the observation of the 
  chunk by a `read(...)`: (1) and (5) ensure `current` observed by `read(...)` is the same 
  `next` at the time `onNext` is invoked, so `onNext` completes the same future as accessed 
  by `read(...)`. Moreover, the store to `next` by `onNext` and load of `next` by 
  `read(...)` are in happens-before relationship due to this synchronizes-with edge, 
  the program order in `onNext`, and program order in `read(...)` (and out-of-bands
  synchronization between multiple reads)
 
  A conforming Publisher establishes total order of `onNext`, therefore, a total order of 
  assignments to `next` and `Future.complete`:
 
  - `onSubscribe`: assert `current == next`
    - `request(1)`
 
  - `onNext`: assert `current == next`
    - `prev = next`
    - `next = new Future`      (A)
    - `prev.complete(chunk)`   (B): assert `prev == this.current`
 
  - `read(...)`
    - `current.get()`          (C): (C) synchronizes-with (B): any read is blocked until (B)
  
  - `read(...)` (same or subsequent read)
    - `current.get()`: synchronizes-with (B)
    - chunk is seen to be consumed entirely: release the chunk, and request next:
    - `current = next`:        (D): (A) happens-before (D), no further `onNext` intervenes
       invariant: `current` never references a released chunk as seen by `close(...)`,
       assuming `read(...)` and `close(...)` are totally ordered --either by
       program order, or through out-of-bands synchronization
    - `request(1)`: assert a conforming Publisher does not invoke onNext before this
 
  - `onNext`: assert `current == next`: a conforming `Publisher` does not invoke `onNext` before 
  `request(1)`
    - `prev = next`
    - `next = new Future`       (E)
    - `prev.complete(chunk)`    (F): assert `prev == current`
 
  - `read(...)`
    - `current.get()`: (G): (G) synchronizes-with (F): any read after (D) is blocked until (F)
  
 
  - `onComplete` / `onError`: assert: `next` has not been completed: stream is either empty 
  (no `onNext` will ever be called), or an `onNext` assigned a new uncompleted future to `next`
    - `next.complete(...)`: (H): assert conforming `Publisher` ensures `next` assignments 
    by `onNext` are visible here by totally ordering `onNext` / `onComplete` / `onError`
 
  - `read(...)`: assert eventually `current == next`: either initially, or after some read 
  that consumed the chunk in its entirety and requested the new chunk
    - `current.get()`: (I): (I) synchronizes-with (H)
    - signal EOF
 
  -` close(...)`: 
   - assert `current` never references a released chunk; it either eventually references a chunk
    that has been produced by `onNext` and has not been consumed fully by `read(...)`, or a null
    produced by `onComplete` / `onError`
   - assert if `next != current`, `next` will never produce a new chunk: this is the case
    if and only if `onNext` has occurred, but `read(...)` has not consumed the chunk in its 
    entirety, hence has not requested any new chunks
   - `current.whenComplete(release)`
   
   ## References
   
   [I] https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#specification
