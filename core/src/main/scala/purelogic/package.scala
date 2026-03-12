package purelogic

export Abort.*
export Reader.*
export State.*
export Writer.*

type Logic[R, W, S, E, A] = (Reader[R], Writer[W], State[S], Abort[E]) ?=> A
