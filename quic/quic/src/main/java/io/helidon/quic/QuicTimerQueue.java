/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.quic;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.Api;

/**
 * A timer queue that can process events which are due, and possibly
 * reschedule them if needed. An instance of a {@link QuicTimerQueue}
 * is usually associated with an instance of {@link QuicSelector} which
 * provides the timer/wakeup facility.
 */
@Api.Internal
public final class QuicTimerQueue {

    private static final AtomicLong EVENTIDS = new AtomicLong();
    private static final System.Logger LOGGER = System.getLogger(QuicTimerQueue.class.getName());
    // aliases
    private static final Marker FLOOR = Marker.FLOOR;
    private static final Marker CEILING = Marker.CEILING;
    // A queue that contains scheduled events
    private final ConcurrentSkipListSet<QuicTimedEvent> scheduled =
            new ConcurrentSkipListSet<>(QuicTimedEvent.COMPARATOR);
    // A queue that contains events which are due. The queue is
    // filled by processAndReturnNextDeadline()
    private final ConcurrentLinkedQueue<QuicTimedEvent> due =
            new ConcurrentLinkedQueue<>();
    // A queue that contains events that need to be rescheduled.
    // The event may already be in the scheduled queue - in which
    // case it will be removed before being added back.
    private final Set<QuicTimedEvent> rescheduled =
            ConcurrentHashMap.newKeySet();
    // A callback to tell the timer thread to wake up
    private final Runnable notifier;
    private final Supplier<String> logTagSupplier;
    private final ReentrantLock stateLock = new ReentrantLock();
    // A loop to process events which are due, or which need to
    // be rescheduled.
    private final SequentialScheduler processor;

    private volatile boolean closed;
    private volatile Deadline scheduledDeadline = Deadline.MAX;
    private volatile Deadline returnedDeadline = Deadline.MAX;

    {
        processor = SequentialScheduler.lockingScheduler(this::processDue);
    }

    /**
     * Creates a new timer queue with the given notifier.
     * A notifier is used to notify the timer thread that
     * new events have been added to the queue of scheduled
     * event. The notifier should wake up the thread and
     * trigger a call to either {@link
     * #processEventsAndReturnNextDeadline(Deadline, Executor)}
     * or {@link #nextDeadline()}.
     *
     * @param notifier A notifier to wake up the timer thread when
     *                new event have been added and the next
     *                deadline has changed.
     * @param logTagSupplier supplier of the timer-queue log tag
     */
    QuicTimerQueue(Runnable notifier, Supplier<String> logTagSupplier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.logTagSupplier = Objects.requireNonNull(logTagSupplier, "logTagSupplier");
    }

    /**
     * Returns a unique id for a new {@link QuicTimedEvent}.
     *
     * @return a unique id for a new {@link QuicTimedEvent}.
     * Each new instance of {@link QuicTimedEvent} is created with a long
     * ID returned by this method to ensure a total ordering of
     * {@code QuicTimedEvent} instances, even when their deadlines
     * are equal.
     */
    public static long newEventId() {
        return EVENTIDS.getAndIncrement();
    }

    /**
     * Schedule the given event by adding it to the timer queue.
     *
     * @param event an event to be scheduled
     */
    public void offer(QuicTimedEvent event) {
        if (event instanceof Marker marker) {
            throw new IllegalArgumentException(marker.name());
        }
        Deadline deadline = event.deadline();
        scheduled.add(event);
        scheduled(deadline);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "QuicTimerQueue: event %s offered", event);
        }
        if (notify(deadline)) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "QuicTimerQueue: event %s will be rescheduled", event);
            }
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                var now = debugNow();
                log(System.Logger.Level.TRACE,
                    "QuicTimerQueue: event %s will be scheduled at %s (returned deadline: %s, nextDeadline: %s)",
                    event, d(now, deadline), d(now, returnedDeadline), d(now, nextDeadline()));
            }
            notifier.run();
        } else if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            var now = debugNow();
            log(System.Logger.Level.TRACE,
                "QuicTimerQueue: event %s will not be scheduled at %s (returned deadline: %s, nextDeadline: %s)",
                event, d(now, deadline), d(now, returnedDeadline), d(now, nextDeadline()));
        }
    }

    /**
     * Cancels the given event if it is waiting in this timer queue.
     *
     * <p>An event which has already been claimed by the due-event processor may still have its {@link QuicTimedEvent#handle()
     * handle} method invoked. Event implementations which race cancellation against handling must therefore coordinate that
     * race in their own state.
     *
     * @param event event to cancel
     * @return whether the event was removed from a queue
     */
    public boolean cancel(QuicTimedEvent event) {
        if (event instanceof Marker marker) {
            throw new IllegalArgumentException(marker.name());
        }
        boolean removed = rescheduled.remove(event);
        if (scheduled.remove(event)) {
            return true;
        }
        return removed || due.remove(event);
    }

    /**
     * The next deadline for this timer queue. This is only weakly
     * consistent. If the queue is empty, {@link Deadline#MAX} is
     * returned.
     *
     * @return The next deadline, or {@code Deadline.MAX}.
     */
    public Deadline nextDeadline() {
        var event = scheduled.ceiling(FLOOR);
        return event == null ? Deadline.MAX : event.deadline();
    }

    /**
     * Returns the earliest deadline pending through a reschedule request.
     *
     * @return the earliest deadline pending through a reschedule request.
     */
    public Deadline pendingScheduledDeadline() {
        return scheduledDeadline;
    }

    /**
     * Process all events that were due before {@code now}, and
     * returns the next deadline. The events are processed within
     * an executor's thread, so this method may return before all
     * events have been processed. The events are processed in
     * order, with respect to their deadline. Processing an event
     * involves invoking its {@link QuicTimedEvent#handle() handle}
     * method. If that method returns a new deadline different from
     * {@link Deadline#MAX} the processed event is rescheduled
     * immediately. Otherwise, it will not be rescheduled.
     *
     * @param now      The point in time before which events are
     *                considered to be due. Usually, that's now.
     * @param executor An executor to process events which are due.
     * @return the next unexpired deadline, or {@link Deadline#MAX}
     *        if the queue is empty.
     */
    public Deadline processEventsAndReturnNextDeadline(Deadline now, Executor executor) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(executor, "executor");
        QuicTimedEvent event;
        int drained = 0;
        int dues;
        stateLock.lock();
        try {
            scheduledDeadline = Deadline.MAX;
        } finally {
            stateLock.unlock();
        }
        // moved scheduled / rescheduled tasks to due, until
        // nothing else is due. Then process dues.
        do {
            dues = processRescheduled(now);
            dues = dues + processScheduled(now);
            drained += dues;
        } while (dues > 0);
        event = scheduled.ceiling(FLOOR);
        Deadline newDeadline = event == null ? Deadline.MAX : event.deadline();
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            log(System.Logger.Level.TRACE, "QuicTimerQueue: newDeadline: %s%s",
                      d(now, newDeadline),
                      event == null ? " no event scheduled" : " for " + event);
        }
        Deadline next;
        stateLock.lock();
        try {
            var scheduled = scheduledDeadline;
            scheduledDeadline = Deadline.MAX;
            // if some task is being rescheduled with a deadline
            // that is before any scheduled deadline, use that deadline.
            returnedDeadline = min(newDeadline, scheduled);
            next = returnedDeadline;
        } finally {
            stateLock.unlock();
        }
        if (next.equals(Deadline.MAX) && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            log(System.Logger.Level.TRACE,
                "TimerQueue: no deadline (scheduled: %s, rescheduled: %s, dues %s)",
                this.scheduled.size(), this.rescheduled.size(), this.due.size());
        }
        if (drained > 0) {
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                log(System.Logger.Level.TRACE, "TimerQueue: %s events to handle (%s in dues)", drained, this.due.size());
            }
            processor.runOrSchedule(executor);
        }
        return next;
    }

    /**
     * Reschedule the given {@code QuicTimedEvent}.
     *
     * @param event an event to reschedule
     * <p>Note: This method is used if the prospective future deadline at which the event
     *        should be scheduled is not known by the caller.
     *        This may cause an idle wakeup in the selector thread owning this
     *        {@code QuicTimerQueue}. Use {@link #reschedule(QuicTimedEvent, Deadline)}
     *        to minimize idle wakeup.
     */
    public void reschedule(QuicTimedEvent event) {
        if (event instanceof Marker marker) {
            throw new IllegalArgumentException(marker.name());
        }
        rescheduled.add(event);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "QuicTimerQueue: event %s will be rescheduled", event);
        }
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            var now = debugNow();
            log(System.Logger.Level.TRACE,
                "QuicTimerQueue: event %s will be rescheduled (returned deadline: %s, nextDeadline: %s)",
                event, d(now, returnedDeadline), d(now, nextDeadline()));
        }
        notifier.run();
    }

    /**
     * Reschedule the given {@code QuicTimedEvent}.
     *
     * @param event    an event to reschedule
     * @param deadline the prospective future deadline at which the event should
     *                be rescheduled
     * <p>Note: This method should be used in preference of {@link #reschedule(QuicTimedEvent)}
     *        if the prospective future deadline at which the event should be scheduled is
     *        already known by the caller. Using this method will minimize idle wakeup
     *        of the selector thread, in comparison of {@link #reschedule(QuicTimedEvent)}.
     */
    public void reschedule(QuicTimedEvent event, Deadline deadline) {
        if (event instanceof Marker marker) {
            throw new IllegalArgumentException(marker.name());
        }
        rescheduled.add(event);
        scheduled(deadline);
        // no need to wake up the selector thread if the next deadline
        // is already before the new deadline

        if (notify(deadline)) {
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                var now = debugNow();
                log(System.Logger.Level.TRACE,
                    "QuicTimerQueue: event %s will be rescheduled at %s (returned deadline: %s, nextDeadline: %s)",
                    event, d(now, deadline), d(now, returnedDeadline), d(now, nextDeadline()));
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "QuicTimerQueue: event %s will be rescheduled", event);
            }
            notifier.run();
        } else if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            var now = debugNow();
            log(System.Logger.Level.TRACE,
                "QuicTimerQueue: event %s will not be rescheduled at %s (returned deadline: %s, nextDeadline: %s)",
                event, d(now, deadline), d(now, returnedDeadline), d(now, nextDeadline()));
        }
    }

    /**
     * Called to clean up the timer queue when it is no longer needed.
     * Makes sure that all pending tasks are cleared from the various lists.
     */
    public void stop() {
        closed = true;
        do {
            processor.stop();
            due.clear();
            rescheduled.clear();
            scheduled.clear();
        } while (!due.isEmpty() || !rescheduled.isEmpty() || !scheduled.isEmpty());
    }

    private static boolean isDue(Deadline deadline, Deadline now) {
        return deadline.compareTo(now) <= 0;
    }

    // For debug purposes only
    private String d(Deadline deadline) {
        return Utils.debugDeadline(debugNow(), deadline);
    }

    // For debug purposes only
    private String d(Deadline now, Deadline deadline) {
        return Utils.debugDeadline(now, deadline);
    }

    // For debug purposes only
    private Deadline debugNow() {
        return TimeSource.now();
    }

    private void log(System.Logger.Level level, String format, Object... arguments) {
        LOGGER.log(level,
                   () -> "[" + logTagSupplier.get() + "] "
                           + (arguments.length == 0 ? format : format.formatted(arguments)));
    }

    private void log(System.Logger.Level level, String message, Throwable throwable) {
        LOGGER.log(level, "[" + logTagSupplier.get() + "] " + message, throwable);
    }

    // return the deadline which is before the other
    private Deadline min(Deadline one, Deadline two) {
        return one.isBefore(two) ? one : two;
    }

    // walk through the rescheduled tasks and moves any
    // that are due to `due`. Otherwise, move them to
    // `scheduled`
    private int processRescheduled(Deadline now) {
        int drained = 0;
        for (var it = rescheduled.iterator(); it.hasNext();) {
            QuicTimedEvent event = it.next();
            it.remove(); // remove before processing to avoid race
            scheduled.remove(event);
            Deadline deadline = event.refreshDeadline();
            if (deadline.equals(Deadline.MAX)) {
                continue;
            }
            if (deadline.isAfter(now)) {
                scheduled.add(event);
            } else {
                due.add(event);
                drained++;
            }
        }
        if (drained > 0) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "QuicTimerQueue: %s rescheduled tasks are due", drained);
            }
        }
        return drained;
    }

    // walk through the scheduled tasks and moves any
    // that are due to `due`.
    private int processScheduled(Deadline now) {
        QuicTimedEvent event;
        int drained = 0;
        while ((event = scheduled.ceiling(FLOOR)) != null) {
            Deadline deadline = event.deadline();
            if (!isDue(deadline, now)) {
                break;
            }
            if (!scheduled.remove(event)) {
                continue;
            }
            drained++;
            due.add(event);
        }
        if (drained > 0 && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "QuicTimerQueue: %s scheduled tasks are due", drained);
        }
        return drained;
    }

    // process all due events in order
    private void processDue() {
        try {
            QuicTimedEvent event;
            if (closed) {
                return;
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "QuicTimerQueue: processDue");
            }
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                log(System.Logger.Level.TRACE, "TimerQueue: process %s events", due.size());
            }
            Deadline minDeadLine = Deadline.MAX;
            while ((event = due.poll()) != null) {
                if (closed) {
                    return;
                }
                Deadline nextDeadline = event.handle();
                if (Deadline.MAX.equals(nextDeadline)) {
                    continue;
                }
                rescheduled.add(event);
                if (nextDeadline.isBefore(minDeadLine)) {
                    minDeadLine = nextDeadline;
                }
            }

            // record the minimal deadline that was rescheduled
            scheduled(minDeadLine);

            // wake up the selector thread if necessary
            if (notify(minDeadLine)) {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    log(System.Logger.Level.TRACE, "TimerQueue: notify: minDeadline: %s", d(minDeadLine));
                }
                notifier.run();
            } else if (!minDeadLine.equals(Deadline.MAX) && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                log(System.Logger.Level.TRACE, "TimerQueue: no need to notify: minDeadline: %s", d(minDeadLine));
            }

        } catch (Throwable t) {
            if (!closed) {
                log(System.Logger.Level.ERROR, "Unexpected exception while processing due events", t);
                throw t;
            } else {
                log(System.Logger.Level.ERROR, "Ignoring exception while closing", t);
            }
        }
    }

    // We do not need to notify the selector thread if the next scheduled
    // deadline is before the given deadline, or if it is after
    // the last returned deadline.
    private boolean notify(Deadline deadline) {
        stateLock.lock();
        try {
            return deadline.isBefore(nextDeadline())
                    || deadline.isBefore(returnedDeadline);
        } finally {
            stateLock.unlock();
        }
    }

    // Record a prospective attempt to reschedule an event at
    // the given deadline
    private Deadline scheduled(Deadline deadline) {
        stateLock.lock();
        try {
            var scheduled = scheduledDeadline;
            if (deadline.isBefore(scheduled)) {
                scheduledDeadline = deadline;
                return deadline;
            }
            return scheduled;
        } finally {
            stateLock.unlock();
        }
    }

    // This class is used to work around the lack of a peek() method
    // in ConcurrentSkipListSet. ConcurrentSkipListSet has a method
    // called first(), but it throws NoSuchElementException if the
    // set isEmpty() - whereas peek() would return {@code null}.
    // The next best thing is to use ConcurrentSkipListSet::ceiling,
    // but for that we need to define a minimum event which is lower
    // than any other event: we do this by defining Marker.FLOOR
    // which has deadline=Deadline.MIN and eventId=Long.MIN_VALUE;
    // Note: it would be easier to use a record, but an enum ensures that we
    // can only have the two instances FLOOR and CEILING.
    enum Marker implements QuicTimedEvent {
        /**
         * A {@code Marker} event to pass to {@link ConcurrentSkipListSet#ceiling(Object)
         * ConcurrentSkipListSet::ceiling} in order to get the first event in the list,
         * or {@code null}.
         *
         * <p>Note: The intended usage is: <pre>{@code
         *              var head = scheduled.ceiling(FLOOR);
         *        }</pre>
         *
         */
        FLOOR(Deadline.MIN, Long.MIN_VALUE),
        /**
         * A {@code Marker} event to pass to {@link ConcurrentSkipListSet#floor(Object)
         * ConcurrentSkipListSet::floor} in order to get the last event in the list,
         * or {@code null}.
         *
         * <p>Note: The intended usage is: <pre>{@code
         *              var head = scheduled.floor(CEILING);
         *        }</pre>
         *
         */
        CEILING(Deadline.MAX, Long.MAX_VALUE);
        private final Deadline deadline;
        private final long eventId;

        Marker(Deadline deadline, long eventId) {
            this.deadline = deadline;
            this.eventId = eventId;
        }

        @Override
        public Deadline deadline() {
            return deadline;
        }

        @Override
        public Deadline refreshDeadline() {
            return Deadline.MAX;
        }

        @Override
        public Deadline handle() {
            return Deadline.MAX;
        }

        @Override
        public long eventId() {
            return eventId;
        }
    }

}
