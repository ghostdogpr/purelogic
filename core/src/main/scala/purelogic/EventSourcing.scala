package purelogic

import EventSourcing.Transition

/**
  * Combines [[State]] and [[Writer]] to enforce the event sourcing pattern: every state change must go through a
  * [[Transition]] that is triggered by an event.
  *
  * @tparam Ev
  *   the base event type
  * @tparam S
  *   the type of the state
  * @tparam Err
  *   the type of errors that transitions can raise
  */
trait EventSourcing[Ev, S, Err] {
  protected given state: State[S]
  protected given writer: Writer[Ev]

  /**
    * Applies the transition for the given event to update the state, then records the event. If the transition fails via
    * [[Abort]], the event is not recorded.
    */
  def writeEvent[Ev1 <: Ev](event: Ev1)(using transition: Transition[Ev1, S, Err], abort: Abort[Err]): Unit = {
    transition.run(event)
    write(event)
  }

  /**
    * Replays a sequence of events by applying their transitions to rebuild the state. Events are not recorded in the
    * writer, making this suitable for rehydrating state from a persisted event log.
    */
  def replayEvents(events: Iterable[Ev])(using transition: Transition[Ev, S, Err], abort: Abort[Err]): Unit =
    events.foreach(transition.run)
}

object EventSourcing {

  /**
    * Defines how an event of type `Ev` modifies the state `S`, potentially failing with `Err`.
    *
    * @tparam Ev
    *   the event type
    * @tparam S
    *   the type of the state
    * @tparam Err
    *   the type of errors that the transition can raise
    */
  trait Transition[Ev, S, Err] {

    /**
      * Applies the event to the current state.
      */
    def run(ev: Ev): (State[S], Abort[Err]) ?=> Unit
  }

  /**
    * Provides an [[EventSourcing]] instance and runs the body, threading through [[State]], [[Writer]], and [[Abort]]
    * from the enclosing scope.
    */
  def apply[Ev, S, Err, A](body: EventSourcing[Ev, S, Err] ?=> A)(using s: State[S], w: Writer[Ev]): A = {
    val eventSourcing = new EventSourcing[Ev, S, Err] {
      protected given state: State[S]    = s
      protected given writer: Writer[Ev] = w
    }
    body(using eventSourcing)
  }

  /**
    * Applies the transition for the given event to update the state, then records the event. If the transition fails via
    * [[Abort]], the event is not recorded.
    */
  inline def writeEvent[Ev1 <: Ev, Ev, S, Err](event: Ev1)(using transition: Transition[Ev1, S, Err])(
    using eventSourcing: EventSourcing[Ev, S, Err],
    abort: Abort[Err]
  ): Unit =
    eventSourcing.writeEvent(event)

  /**
    * Replays a sequence of events by applying their transitions to rebuild the state. Events are not recorded in the
    * writer, making this suitable for rehydrating state from a persisted event log.
    */
  inline def replayEvents[Ev, S, Err](events: Iterable[Ev])(using transition: Transition[Ev, S, Err])(
    using eventSourcing: EventSourcing[Ev, S, Err],
    abort: Abort[Err]
  ): Unit =
    eventSourcing.replayEvents(events)
}
