package purelogic

// Reader
inline def ask[R](using r: Reader[R]): R                   = r.ask
inline def inquire[R, A](using r: Reader[R])(f: R => A): A = Reader.inquire(f)

// State
inline def get[S](using s: State[S]): S                   = s.get
inline def set[S](v: S)(using s: State[S]): Unit          = s.set(v)
inline def modify[S](using s: State[S])(f: S => S): Unit  = s.modify(f)
inline def inspect[S, B](using s: State[S])(f: S => B): B = State.inspect(f)

// Writer
inline def tell[W](w: W)(using wr: Writer[W]): Unit = wr.tell(w)

// Raise
inline def raise[E](e: E)(using r: Raise[E]): Nothing                          = r.raise(e)
inline def ensure[E](using Raise[E])(condition: Boolean, error: => E): Unit    = Raise.ensure(condition, error)
inline def ensureWith[E, A](using Raise[E])(option: Option[A], error: => E): A = Raise.ensureWith(option, error)
