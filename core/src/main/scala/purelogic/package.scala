package purelogic

import scala.util.Try

// Reader
inline def read[R](using Reader[R]): R               = Reader.read
inline def read[R, A](using Reader[R])(f: R => A): A = Reader.read(f)

// State
inline def get[S](using State[S]): S                       = State.get
inline def get[S, A](using State[S])(f: S => A): A         = State.get(f)
inline def set[S](using State[S])(v: S): Unit              = State.set(v)
inline def update[S](using State[S])(f: S => S): Unit      = State.update(f)
inline def modify[S, A](using State[S])(f: S => (A, S)): A = State.modify(f)
inline def updateAndGet[S](using State[S])(f: S => S): S   = State.updateAndGet(f)
inline def getAndSet[S](using State[S])(v: S): S           = State.getAndSet(v)
inline def getAndUpdate[S](using State[S])(f: S => S): S   = State.getAndUpdate(f)

// Writer
inline def write[W](using Writer[W])(w: W): Unit                      = Writer.write(w)
inline def writeAll[W](using Writer[W])(elems: IterableOnce[W]): Unit = Writer.writeAll(elems)
inline def clear[W](using Writer[W]): Unit                            = Writer.clear

// Abort
inline def fail[E](using Abort[E])(e: E): Nothing                                                       = Abort.fail(e)
inline def ensure[E](using Abort[E])(condition: Boolean, error: => E): Unit                             = Abort.ensure(condition, error)
inline def ensureNot[E](using Abort[E])(condition: Boolean, error: => E): Unit                          = Abort.ensureNot(condition, error)
inline def extractOption[E, A](using Abort[E])(option: Option[A], error: => E): A                       = Abort.extractOption(option, error)
inline def extractEither[E, A](using Abort[E])(either: Either[E, A]): A                                 = Abort.extractEither(either)
inline def extractTry[A](t: Try[A])(using Abort[Throwable]): A                                          = Abort.extractTry(t)
inline def attempt[A](f: => A)(using Abort[Throwable]): A                                               = Abort.attempt(f)
inline def recover[W, S, E, A](using Writer[W], State[S])(f: Abort[E] ?=> A)(handler: E => A): A        = Abort.recover(resetLog = true)(f)(handler)
inline def recoverKeepLog[W, S, E, A](using Writer[W], State[S])(f: Abort[E] ?=> A)(handler: E => A): A = Abort.recover(resetLog = false)(f)(handler)
