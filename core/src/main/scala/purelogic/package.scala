package purelogic

export Abort.*
export Reader.*
export State.*
export Writer.*

/**
  * Type alias for a program that uses all 4 capabilities: `Reader`, `Writer`, `State`, and `Abort`.
  */
type Logic[R, W, S, E, A] = (Reader[R], Writer[W], State[S], Abort[E]) ?=> A
