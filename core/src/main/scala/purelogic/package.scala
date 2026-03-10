package purelogic

// Reader
inline def ask[R](using r: Reader[R]): R                   = r.ask
inline def inquire[R, A](using r: Reader[R])(f: R => A): A = r.inquire(f)

// State
inline def get[S](using s: State[S]): S                   = s.get
inline def set[S](using s: State[S])(v: S): Unit          = s.set(v)
inline def modify[S](using s: State[S])(f: S => S): Unit  = s.modify(f)
inline def inspect[S, A](using s: State[S])(f: S => A): A = s.inspect(f)

// Writer
inline def tell[W](using wr: Writer[W])(w: W): Unit = wr.tell(w)

// Raise
inline def raise[E](using r: Raise[E])(e: E): Nothing                                                         = r.raise(e)
inline def ensure[E](using r: Raise[E])(condition: Boolean, error: => E): Unit                                = r.ensure(condition, error)
inline def ensureWith[E, A](using r: Raise[E])(option: Option[A], error: => E): A                             = r.ensureWith(option, error)
inline def recover[E, S, W, A](using s: State[S], w: Writer[W])(f: Raise[E] ?=> A)(handler: E => A): A        = Raise.recover(resetLog = true)(f)(handler)
inline def recoverKeepLog[E, S, W, A](using s: State[S], w: Writer[W])(f: Raise[E] ?=> A)(handler: E => A): A =
  Raise.recover(resetLog = false)(f)(handler)
