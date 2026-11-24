package purelogic

import scala.annotation.implicitNotFound
import scala.util.NotGiven

/**
  * Lets `recover` and its variants undo the changes made by a block that fails.
  *
  * You never create or pass a `Rollback` yourself: the library provides one from the capabilities your code has.
  *   - With a [[State]] and a [[Writer]], recovery restores both the state and the writes.
  *   - With only one of them, it restores that one.
  *   - In an event-sourced program, it restores the state and the events together.
  *
  * A function that has only a [[StateWriter]] cannot use `recover`, because the state cannot be read to save it. Ask
  * for a [[State]] instead.
  */
@implicitNotFound(
  "recover needs the Writer and State in scope to save and restore them. Each must be unambiguous, and a StateWriter alone cannot be saved, so require a State instead"
)
sealed trait Rollback extends scala.caps.ExclusiveCapability {

  /**
    * Saves the state and writes, and returns a function that restores them. When the function is given `true`, the
    * writes made since are kept, and the state of an [[EventSourcing]] is kept too, because it was produced by the
    * events that are kept.
    */
  private[purelogic] def checkpoint: Boolean => Unit
}

object Rollback extends RollbackLowPriority {

  private[purelogic] opaque type StateInScope[S] <: State[S] = State[S]

  object StateInScope extends StateInScopeLowPriority {
    given fromState[S](using s: State[S]): StateInScope[S] = s
  }

  private[purelogic] trait StateInScopeLowPriority {

    // Lower priority than `fromState`: with two capabilities that write a state, the `StateWriter` search is ambiguous
    // and `NotGiven` succeeds, but a `State` in scope must still be used.
    given none(using NotGiven[StateWriter[Nothing]]): StateInScope[Unit] = new State[Unit] {
      def get: Unit          = ()
      def set(s: Unit): Unit = ()
    }
  }

  private[purelogic] opaque type WriterInScope[W] <: Writer[W] = Writer[W]

  object WriterInScope extends WriterInScopeLowPriority {
    given fromWriter[W](using w: Writer[W]): WriterInScope[W] = w
  }

  private[purelogic] trait WriterInScopeLowPriority {

    // Lower priority than `fromWriter`, so that a scope with two Writers of different types, where the `Writer` search is
    // ambiguous and `NotGiven` succeeds, does not get the do-nothing writer.
    given none(using NotGiven[Writer[Nothing]]): WriterInScope[Nothing] = new Writer[Nothing] {
      def write(w: Nothing): Unit                       = ()
      def writeAll(elems: IterableOnce[Nothing]^): Unit = ()
      def clear: Unit                                   = ()
      type Snapshot = Unit
      def snapshot: Unit           = ()
      def rollback(to: Unit): Unit = ()
    }
  }

  /**
    * Provides a `Rollback` from the [[EventSourcing]], [[Writer]] and [[State]] in scope, preferred over the one built
    * from [[Writer]] and [[State]] alone.
    */
  given fromEventSourcing[Ev, S, Err, W, S1](
    using eventSourcing: EventSourcing[Ev, S, Err],
    writer: WriterInScope[W],
    state: StateInScope[S1]
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
    * Provides a `Rollback` from the [[Writer]] and [[State]] in scope. When either is missing, an instance that does
    * nothing is used in its place.
    */
  given fromWriterAndState[W, S](using writer: Rollback.WriterInScope[W], state: Rollback.StateInScope[S]): Rollback =
    Rollback(writer, state)
}
