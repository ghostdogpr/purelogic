package purelogic

// Reader
inline def read[R](using r: Reader[R]): R                   = r.read
inline def readWith[R, A](using r: Reader[R])(f: R => A): A = r.readWith(f)

// State
inline def get[S](using s: State[S]): S                   = s.get
inline def set[S](using s: State[S])(v: S): Unit          = s.set(v)
inline def modify[S](using s: State[S])(f: S => S): Unit  = s.modify(f)
inline def getWith[S, A](using s: State[S])(f: S => A): A = s.getWith(f)

// Writer
inline def write[W](using wr: Writer[W])(w: W): Unit = wr.write(w)

// Abort
inline def fail[E](using r: Abort[E])(e: E): Nothing                                                          = r.fail(e)
inline def ensure[E](using r: Abort[E])(condition: Boolean, error: => E): Unit                                = r.ensure(condition, error)
inline def ensureWith[E, A](using r: Abort[E])(option: Option[A], error: => E): A                             = r.ensureWith(option, error)
inline def recover[E, S, W, A](using s: State[S], w: Writer[W])(f: Abort[E] ?=> A)(handler: E => A): A        = Abort.recover(resetLog = true)(f)(handler)
inline def recoverKeepLog[E, S, W, A](using s: State[S], w: Writer[W])(f: Abort[E] ?=> A)(handler: E => A): A =
  Abort.recover(resetLog = false)(f)(handler)
