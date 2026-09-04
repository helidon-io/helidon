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

import java.util.Comparator;

/**
 * Models an event which is triggered upon reaching a
 * deadline. {@code QuicTimedEvent} instances are designed to be
 * registered with a single {@link QuicTimerQueue}.
 *
 * @implSpec Implementations should make sure that each instance of
 *        {@code QuicTimedEvent} is only present once in a single
 *        {@link QuicTimerQueue} at any given time. It is however
 *        allowed to register the event again with the same {@link QuicTimerQueue}
 *        after it has been handled, or if it is no longer registered in any
 *        queue.
 */
sealed interface QuicTimedEvent
        permits PacketSpaceManager.PacketTransmissionTask,
                QuicTimerQueue.Marker,
                QuicEndpoint.ClosedConnection,
                IdleTimeoutManager.TimedEvent,
                QuicConnectionImpl.PathValidationTimer,
                QuicConnectionImpl.MaxInitialTimer,
                QuicServerHandshakeAdmission.Permit {

    /**
     * A comparator that compares {@code QuicTimedEvent} instances by their deadline, in the same
     * manner as {@link #compare(QuicTimedEvent, QuicTimedEvent) QuicTimedEvent::compare}.
     */
    // public static final (are redundant)
    Comparator<QuicTimedEvent> COMPARATOR = QuicTimedEvent::compare;

    /**
     * Compares two instance of {@link QuicTimedEvent}.
     * First compared their {@link #deadline()}, then their {@link #eventId()}.
     * It is expected that two elements with same deadline and same event id
     * must the same {@link QuicTimedEvent} instance.
     *
     * @param one a first QuicTimedEvent instance
     * @param two a second QuicTimedEvent instance
     * @return whether the first element is less, same, or greater than the
     *        second.
     */
    static int compare(QuicTimedEvent one, QuicTimedEvent two) {
        if (one == two) {
            return 0;
        }
        int cmp = one.compareDeadlines(two);
        cmp = cmp == 0 ? Long.compare(one.eventId(), two.eventId()) : cmp;
        // ensure total ordering;
        return cmp;
    }

    /**
     * Returns the deadline at which the event should be triggered,
     * or {@link Deadline#MAX} if the event does not need
     * to be scheduled.
     *
     * @return the deadline at which the event should be triggered, or
     *         {@link Deadline#MAX} if the event does not need to be scheduled
     *
     * @implSpec Care should be taken to not change the deadline while the
     *        event is registered with a {@link QuicTimerQueue timer queue}.
     *        The only safe time when the deadline can be changed is:
     *        <ul>
     *            <li>when {@link #refreshDeadline()} method, since the event
     *                is not in any queue at that point,</li>
     *            <li>when the deadline is {@link Deadline#MAX}, since the
     *                event should not be in any queue if it has no
     *                deadline</li>
     *        </ul>
     *
     */
    Deadline deadline();

    /**
     * Handles the triggered event.
     * Returns a new deadline, if the event needs to be
     * rescheduled, or {@code Deadline.MAX} otherwise.
     *
     * @return a new deadline  if the event should be
     *        rescheduled right away, {@code Deadline.MAX}
     *        otherwise.
     * @implSpec The {@link #deadline() deadline} should not be
     *        changed before {@link #refreshDeadline()} is called.
     */
    Deadline handle();

    /**
     * An event id, obtained at construction time from
     * {@link QuicTimerQueue#newEventId()}. This is used
     * to implement a total order among subclasses.
     *
     * @return this event's id.
     */
    long eventId();

    /**
     * Returns whether this event's deadline is before the other's event deadline.
     *
     * @param other the other event
     * @return true if this event's deadline is before the other's event deadline
     * @implSpec The default implementation of this method is to return {@code
     *        deadline().isBefore(other.deadline())}.
     */
    default boolean isBefore(QuicTimedEvent other) {
        return deadline().isBefore(other.deadline());
    }

    /**
     * Compares this event's deadline with the other event's deadline.
     *
     * @param other the other event
     * @return {@code -1}, {@code 0}, or {@code 1} depending on whether this
     *        event's deadline is before, equals to, or after, the other event's
     *        deadline.
     * @implSpec The default implementation of this method compares deadlines in the same manner as
     *        {@link Deadline#compareTo(Deadline) this.deadline().compareTo(other.deadline())} would.
     */
    default int compareDeadlines(QuicTimedEvent other) {
        return deadline().compareTo(other.deadline());
    }

    /**
     * Called to cause an event to refresh its deadline.
     * This method is called by the {@link QuicTimerQueue}
     * when rescheduling an event.
     *
     * <p>Note: The value returned by {@link #deadline()} can only be safely
     *        updated when this method is called.
     */
    Deadline refreshDeadline();

}
