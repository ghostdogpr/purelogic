package purelogic

/**
  * Saves the state and writes of a scope and restores them. `recover` and its variants use it to undo the effects of a
  * block that failed.
  *
  * Programs never create or pass a `Rollback`: the library provides one from the capabilities in scope. It always
  * covers the [[Writer]] and [[State]] in scope, or their default instances when they are not in scope. In an
  * event-sourced program, it also covers the [[EventSourcing]] in scope and restores its state and events together, so
  * the state always matches the events.
  */
sealed trait Rollback extends scala.caps.ExclusiveCapability {

  /**
    * Saves the state and writes, and returns a function that restores them. When the function is given `true`, the
    * writes made since are kept, and the state of an [[EventSourcing]] is kept too, because it was produced by the
    * events that are kept.
    */
  private[purelogic] def checkpoint: Boolean => Unit
}

object Rollback extends RollbackLowPriority {

  /**
    * Provides a `Rollback` from the [[EventSourcing]], [[Writer]] and [[State]] in scope, preferred over the one built
    * from [[Writer]] and [[State]] alone.
    */
  given fromEventSourcing[Ev, S, Err, W, S1](
    using eventSourcing: EventSourcing[Ev, S, Err],
    writer: Writer[W],
    state: State[S1]
  ): Rollback =
    eventSourcing.rollback(writer, state)

  private[purelogic] def apply[W, S](writer: Writer[W], state: State[S]): Rollback =
    new Rollback {
      def checkpoint: Boolean => Unit = {
        val saved = state.get
        val log   = writer.snapshot
        keepLog => {
          state.set(saved)
          if (!keepLog) writer.rollback(log)
        }
      }
    }

  private[purelogic] def eventSourced[Ev, S, W, S1](
    events: Writer[Ev],
    eventState: State[S],
    writer: Writer[W],
    state: State[S1]
  ): Rollback = {
    val inScope = Rollback(writer, state)
    new Rollback {
      def checkpoint: Boolean => Unit = {
        val saved          = eventState.get
        val log            = events.snapshot
        val restoreInScope = inScope.checkpoint
        keepLog => {
          // The State in scope can be or write through to the EventSourcing's state, so the restore below can change it.
          val produced = eventState.get
          restoreInScope(keepLog)
          if (keepLog) eventState.set(produced)
          else {
            eventState.set(saved)
            events.rollback(log)
          }
        }
      }
    }
  }
}

private[purelogic] trait RollbackLowPriority {

  /**
    * Provides a `Rollback` from the [[Writer]] and [[State]] in scope. When either is missing, its default instance,
    * which does nothing, is used.
    */
  given fromWriterAndState[W, S](using writer: Writer[W], state: State[S]): Rollback =
    Rollback(writer, state)
}
