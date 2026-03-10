package purelogic

// Reader
inline def read[R](using Reader[R]): R                   = Reader.read
inline def readWith[R, A](using Reader[R])(f: R => A): A = Reader.readWith(f)

// State
inline def get[S](using State[S]): S                   = State.get
inline def getWith[S, A](using State[S])(f: S => A): A = State.getWith(f)
inline def set[S](using State[S])(v: S): Unit          = State.set(v)
inline def modify[S](using State[S])(f: S => S): Unit  = State.modify(f)

// Writer
inline def write[W](using Writer[W])(w: W): Unit = Writer.write(w)
inline def clear[W](using Writer[W]): Unit       = Writer.clear

// Abort
inline def fail[E](using Abort[E])(e: E): Nothing                                                       = Abort.fail(e)
inline def ensure[E](using Abort[E])(condition: Boolean, error: => E): Unit                             = Abort.ensure(condition, error)
inline def extractOption[E, A](using Abort[E])(option: Option[A], error: => E): A                       = Abort.extractOption(option, error)
inline def recover[E, S, W, A](using State[S], Writer[W])(f: Abort[E] ?=> A)(handler: E => A): A        = Abort.recover(resetLog = true)(f)(handler)
inline def recoverKeepLog[E, S, W, A](using State[S], Writer[W])(f: Abort[E] ?=> A)(handler: E => A): A = Abort.recover(resetLog = false)(f)(handler)
inline def attempt[A](using Abort[Throwable])(f: => A): A                                               = Abort.attempt(f)
