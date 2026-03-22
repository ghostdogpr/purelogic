package purelogic

export Abort.*
export EventSourcing.*
export Reader.*
export State.*
export Writer.*

/**
  * Type alias for a program that uses all 4 capabilities: `Reader`, `Writer`, `State`, and `Abort`.
  */
type Logic[R, W, S, E, A] = (Reader[R], Writer[W], State[S], Abort[E]) ?=> A

/**
  * Type alias for an event-sourced program. Provides `EventSourcing` for writing events through transitions, `Reader`
  * for the environment, `StateReader` for read-only access to the state, and `Abort` for error handling.
  */
type EventSourcingLogic[R, Ev, S, Err, A] = (EventSourcing[Ev, S, Err], Reader[R], StateReader[S], Abort[Err]) ?=> A
