# io.helidon.media.multipart.MultiPartDecoder

This document provides additional details about the implementation of `MultiPartDecoder`.

## Design considerations

Reactive `Processor` should assume it is used concurrently, and yet deliver signals to downstream in
a total order. There are a few other considerations stemming from the reactive specification.

When an error occurs it must be routed downstream, this `Processor` may have more than one downstream over time, thus
a given error may be signaled to many subscribers if needed.

`Subscriber` may cancel their `Subscription`. This should translate into a cancellation of upstream
subscription at the appropriate time: inner `Subscriber` should allow the outer `Subscriber` to make
progress; outer Subscribers should not cancel upstream subscription while the inner `Subscriber` may
need to interact with upstream to make progress.

`Subscriber` may issue bad requests. This should translate into a cancellation of upstream
subscription at the appropriate time: inner `Subscriber` should allow the outer `Subscriber` to make
progress; outer `Subscriber` should not generate errors that can be seen by inner `Subscriber`.

Resources pinned by this `Processor` should be released as soon as they are not needed, and it is
practical to do so: subsequent requests or cancellations may occur at some arbitrary time in the
future.

Whenever this `Processor` is known to have entered a terminal state (including cancellation or bad request),
 it must release any resources.

Since we are essentially dealing with `DataChunk`, need to keep track of who owns the `DataChunk` - that is,
 whose responsibility it is to release it (important for cases when `DataChunk` is backed by Netty buffers).

In this implementation all interactions with upstream, parser, or any of the `Subscriber` is done
in `drainBoth()`, which is guaranteed to be executed single-threadedly, with appropriate memory
fences between any two invocations of `drainBoth()`. This allows much of the state to be implemented as
non-thread-safe data structures. Additionally, the operation of the `Processor` can be understood
by observing `drainBoth()` method alone. The rest then is just a way to cause `drainBoth()` to make further
state transitions.

The state is described by:
- error: for errors that need to be signalled to both inner and outer `Subscriber` (produced by the parser or upstream)
- cancelled: for cancellations signalled by outer `Subscriber`
- parser: a helper object to capture parser state across multiple `DataChunk`
- iterator: parser iterator that holds `ParserEvents` and transition parser state
- partsRequested: for outer `Subscriber` to indicate demand for MIME parts
- demand for DataChunks by inner `Subscriber`

Whenever any of these change, `drain()` is called to enter `drainBoth()` or demand to re-do it again, if
a thread already inside `drainBoth()` is detected.

Additionally, special care is taken when dealing with:
- `upstream`: to interact with upstream
- `downstream`: outer `Subscriber`
- `bodyPartPublisher`: a special `Publisher` that interacts with inner `Subscriber`

At high level, `drainBoth()` operates like a flat map of a stream of `DataChunk` into a stream of
`ParserEvents`: `[DataChunk]` -> `[[ParserEvent]]` -> `[ParserEvent]`, which then is fanned out into a stream
of streams of DataChunk: `[ParserEvent]` -> `[ReadableBodyPart]`, which is essentially
`[ParserEvent]` -> `[[DataChunk]]`. In fact, if not for resource management and the `Processor` interface,
it could have been constructed as a composition of existing reactive components.

The explanation here may appear in reverse order to what `drainBoth()` is doing, but it may be easier to
see it in this order, the goals from high level to low level:

- `DataChunk` are requested from upstream one at a time
  - this way we do not retain too many `DataChunk`, and flattening `[[ParserEvent]]` is trivial
  - this is ensued by inner and outer `Subscriber` detecting when the demand changes from zero
  - additionally, the demand of the outer `Subscriber` can become zero only after the next part is done ; this means
    that the demand of the outer `Subscriber` is essentially unable to issue upstream request until after the inner
    `Subscriber` is done
- `DataChunk` are not requested, nor any errors are signalled, while the parser iterator is able to
  produce more ParserEvents
  - all `onError` events are totally ordered after all possible `onNext` that can be emitted without
    requesting more `DataChunk` from upstream
- parser iterator does not produce more events, unless there is evidence of demand from inner or
  outer `Subscriber`
  - outer `Subscriber` demand is ignored while there is a `bodyPartPublisher` responsible for dealing with
    the demand of an inner `Subscriber`
  - cancellation or error state of inner `Subscriber` appears to `drainBoth()` as a demand for infinite number
    of `DataChunk`; this way we can make progress to the end of the MIME part, and serve the demand of the outer
    `Subscriber` if any
  - inner `Subscriber` demand is witnessed by inner `Subscriber` calling `drain()`, and that observing that
    `bodyPartPublisher` is unable to satisfy the demand
- parser iterator is not asked for more events, while there is a `bodyPartPublisher` and it satisfies
  the demand for `DataChunk` by inner `Subscriber` by the `DataChunk` already given to it

## DataChunkPublisher

Inner `Subscriber` is dealt with using `DataChunkPublisher`. Essentially, it is a flat map
`[[DataChunk]]` -> `[DataChunk]` (given iterators of `BufferEntry`, one at a time, emits `DataChunk` one at a
time). In fact, if not for resource management, it could have been constructed using existing reactive
components.

The design is very simple:
- keep track of change of demand and cancellations of inner `Subscriber`
- expose methods to allow total order of signals emitted by `drainBoth()`
- when cancelled, or a bad request is received, appear as unlimited unsatisfied demand, and merely discard
  all `DataChunk` that are received
- relies on `drainBoth()` not attempting to deliver `onError` before the previous iterator of `BufferEntry` is
  emptied ; this simplifies resource management

## Initialization

Both `MultiPartDecoder` and `DataChunkPublisher` share a similar approach: they have an atomic counter that:
- is initialized to a value indicating the uninitialized state that can never occur naturally throughout the
`Publisher` lifetime
- can be transitioned into "subscribed" state once and only once in its lifetime
- is finally transitioned into initialized state only after `onSubscribe` has returned.

This allows to ensure that no more than one `Subscriber` is associated with the `Publisher` (concurrent cases are
commonly omitted), and enforce the rule that all on* signals get delivered only after `onSubscribe` and none
during `onSubscribe`.

`DataChunkPublisher` is pretty much done at that stage. `MultiPartDecoder` needs a bit more explanation, as it
has two ends that need initializing:
- upstream signalling `onSubscribe`, potentially immediately followed by `onError` or `onComplete` for
  empty upstream
- downstream outer `Subscriber` being attached by `subscribe()`

The use of contenders atomic counter allows to synchronize all these.

The partial order of possible events is:

```
                                                      uninitialized
                                                       |         |
                       .-------------------------------'         `----------------------.
                       |                                                                |
                       V                                                                V
subscribe(...) --> halfInit(UPSTREAM_INIT) --> deferredInit()          deferredInit() <-- halfInit(DOWNSTREAM_INIT) <-- onSubscribe(...)
                       |                     |                             |            |
                       V                     |   onError / onComplete      |            V
subscribe(...) --> !halfInit(UPSTREAM_INIT)  |           |                 |       !halfInit(DOWNSTREAM_INIT) <-- onSubscribe(...)
                       |                     |           |  request        |            |
                       V                     |           |     |           |            V
subscribe(...) --> !halfInit(UPSTREAM_INIT)  `--.        |     |        .--'       !halfInit(DOWNSTREAM_INIT) <-- onSubscribe(...)
                       |                        |        |     |        |               |
                       V                        V        V     V        V               V
                      ...                      atomic update of contenders             ...
                                                           |
                                                           V
                                                     contenders >= 0
                                                           |
                                                           V
                                                       initialized
```

`halfInit()` ensures that one and only one of `UPSTREAM_INIT` / `DOWNSTREAM_INIT` returns true, and any subsequent
 future invocations with the same argument get false.

Of all atomic updates of contenders counter only the updates by `deferredInit()` are able to turn the value
into a non-negative. All other updates observe they are "locked out" from entering `drainBoth()`. The second
`deferredInit()` can witness if any of `onError`/`onComplete`/request happened, and enter `drainBoth()` on their
behalf.

Uninitialized state is represented by `Integer.MIN_VALUE` (`0b1000`). Each end attempts to transition to
half-initialized for their end, unless it is already initialized. It is safe to enter `drainBoth()`
only after both ends have initialized, so the number of ends that have been initialized is tracked as
the fourth bit: each end tries to add `SUBSCRIPTION_LOCK` (`0b0001`). Before the second of them,
the counter necessarily appears as `0b1111`, and adding `SUBSCRIPTION_LOCK` clears all the high
bits, leaving only zero, unless there were already attempts to enter `drainBoth()` by outer Subscriber
requesting parts as part of `onSubscribe`, or by upstream delivering `onError` or `onComplete`, which are
allowed to occur without requests from downstream.

## Normal flow

```
upstream             outer Subscriber           bodyPartPublisher          inner Subscriber

         initialized     request
             |              |
             V              V
           upstream.request(1)
                   |
    .--------------'
    |
    V
 onNext --> parserIterator = parser.parseIterator
                    |
                   ...
                    |
                    V
            parserIterator.next() == END_HEADERS
                    |
                    V
            bodyPartPublisher = new DataChunkPublisher
                    |
                    V
                 onNext ----------------------> onSubscribe ---------------> request
                                                     |                          |
                    .--------------------------------+--------------------------'
                    |
                    V
         parserIterator.next() == BODY
                    |
                    V
        enter bodyPartPublisher.drain() ----------> onNext
                                                     |
                                                     V
                                                   onNext
                                                     |
                                                    ...
                                                     |
                    .--------------------------------'
                    |
                    V
        return !bodyPartPublisher.drain() // effectively wait for request          request
                                                                                      |
                    .-----------------------------------------------------------------'
                    |
                    V
        enter bodyPartPublisher.drain() ----------> onNext
                                                     |
                    .--------------------------------'
                    |
                    V
        return bodyPartPublisher.drain()
                    |
                    V
          parserIterator.next() == BODY
                    |
                    V
        enter bodyPartPublisher.drain() ----------> onNext
                                                     |
                    .--------------------------------'
                    |
                   ...
                    |
                    V
        return bodyPartPublisher.drain()
                    |
                    V
            !parserIterator.hasNext
                    |
                    V
            upstream.request(1)
                    |
    .---------------'
    |
    V
 onNext --> parserIterator = parser.parseIterator
                    |
                   ...
                    |
                    V
         parserIterator.next() == END_PART
                    |
                    V
            bodyPartPublisher.complete() --------> onComplete
                                                     |
                    .--------------------------------'
                    |
                    V
             partRequested == 0 // essentially wait for request for more parts

                 request
                    |
                    V
            partsRequested > 0
                    |
                    V
            parserIterator.hasNext
                    |
                   ...
                    |
                    V
          !parserIterator.hasNext
                    |
                    V
            partsRequested > 0
                    |
                    V
            upstream.request(1)
                    |
    .---------------'
    |
    V
 onNext --> parserIterator = parser.parseIterator
    |               |
   ...             ...
    |               |
    V               |
 onComplete --------+
                    |
                   ...
                    |
                    V
           !parserIterator.hasNext
                    |
                    V
               onComplete
```

## Errors and cancellations

Inner `Subscriber` makes a bad request:
(This state is not visible to anyone but `bodyPartPublisher` - the inner `Subscription`
appears as the one with the forever unsatisfied demand)

```
bodyPartPublisher      inner Subscriber

     ...              request(0 or negative)
      |                     |
      +---------------------'
      |
     ...
      |
      V
 return bodyPartPublisher.drain() // always returns true without interacting with inner Subscriber
      |
     ...
      |
      V
 parserIterator.next() == END_PART
      |
      V
 bodyPartPublisher.complete --> onError
      |
     ...
```

Inner `Subscriber` cancels:
(This state is not visible to anyone but `bodyPartPublisher` - the inner `Subscription`
appears as the one with the forever unsatisfied demand)

```
bodyPartPublisher      inner Subscriber

     ...                  cancel
      |                     |
      +---------------------'
      |
     ...
      |
      V
 return bodyPartPublisher.drain() // always returns true without interacting with inner Subscriber
      |
     ...
      |
      V
 parserIterator.next() == END_PART
      |
      V
 bodyPartPublisher.complete
      |
     ...
```

Outer `Subscriber` cancels:
(it is difficult to depict the absence of events signalled to downstream exactly)

```
upstream       outer Subscriber

                cancel
                  |
                 ...
                  |
                  V
          bodyPartPublisher == null
                  |
                  V
            upstream.cancel
                  |
                  V
               cleanup
                  |
                  V
  ...         cancelled
   |              |
   V              V
 onNext ----> parserIterator = parser.parseIterator // may throw
   |              |
   V              |
 onError /        V
onComplete --> upstream.cancel
                  |
                  V
               cleanup
```

Outer `Subscriber` makes a bad request:
(As soon as the current part is done, report `onError`, and appear to upstream
as a cancelled `Subscription` after that)

```
upstream       outer Subscriber

           request(0 or negative)
                  |
                 ...
                  |
                  V
          bodyPartPublisher == null
                  |
                  V
            upstream.cancel
                  |
                  V
               onError
                  |
                  V
               cleanup
                  |
                  V
  ...         cancelled
   |              |
   V              V
 onNext ----> parserIterator = parser.parseIterator // may throw
   |              |
   V              |
 onError /        V
onComplete --> upstream.cancel
                  |
                  V
               cleanup
```

Upstream reports `onError`:

```
upstream       outer Subscriber              bodyPartPublisher          inner Subscriber

   ...          ...
    |            |
    V            |
 onError --------+
                 |
                ...
                 |
                 V
          !parserIterator.hasNext
                |       |
                |       V
                |    bodyPartPublisher != null ---> onError
                |
                V
             onError
                |
                V
          upstream.cancel
                |
                V
            cancelled
```

Parser throws:
(As soon as the next parser event is requested. Report to inner and outer `Subscriber`,
appear to upstream as a cancelled `Subscription`)

```
upstream       outer Subscriber              bodyPartPublisher

                    ...
                     |
                     V
           parser or parserIterator throws
                     |
                     V
           parserIterator = EMPTY_ITER
                     |
                     V
            !parserIterator.hasNext
                |       |
                |       V
                |    bodyPartPublisher != null ---> onError
                |
                V
          upstream.cancel
                |
                V
             onError
                |
                V
             cleanup
                |
                V
  ...       cancelled
   |            |
   V            V
 onNext ----> parserIterator = parser.parseIterator // may throw
   |              |
   V              |
 onError /        V
onComplete --> upstream.cancel
                  |
                  V
               cleanup
```