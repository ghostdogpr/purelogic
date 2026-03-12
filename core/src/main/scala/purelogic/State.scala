package purelogic

trait State[S] {
  def get: S
  def set(s: S): Unit

  def get[A](f: S => A): A         = f(get)
  def update(f: S => S): Unit      = set(f(get))
  def updateAndGet(f: S => S): S   = { update(f); get }
  def modify[A](f: S => (A, S)): A = { val (a, newState) = f(get); set(newState); a }
  def getAndSet(v: S): S           = { val old = get; set(v); old }
  def getAndUpdate(f: S => S): S   = { val old = get; update(f); old }

  def local[A](f: S => S)(body: State[S] ?=> A): A = {
    val state    = this
    val previous = get
    set(f(previous))
    try body(using state)
    finally set(previous)
  }

  def focus[A, B](getFocus: S => A)(setFocus: (S, A) => S)(body: State[A] ?=> B): B = {
    val outer = this
    val state = new State[A] {
      def get: A          = getFocus(outer.get)
      def set(a: A): Unit = outer.set(setFocus(outer.get, a))
    }
    body(using state)
  }
}

object State {
  def apply[S, A](initial: S)(body: State[S] ?=> A): (S, A) = {
    var current: S = initial
    val state      = new State[S] {
      def get: S          = current
      def set(s: S): Unit = current = s
    }
    val result     = body(using state)
    (current, result)
  }

  given State[Unit] = new State[Unit] {
    def get: Unit          = ()
    def set(s: Unit): Unit = ()
  }

  inline def get[S](using s: State[S]): S                                                                             = s.get
  inline def get[S, A](using s: State[S])(f: S => A): A                                                               = s.get(f)
  inline def set[S](using s: State[S])(v: S): Unit                                                                    = s.set(v)
  inline def update[S](using s: State[S])(f: S => S): Unit                                                            = s.update(f)
  inline def updateAndGet[S](using s: State[S])(f: S => S): S                                                         = s.updateAndGet(f)
  inline def modify[S, A](using s: State[S])(f: S => (A, S)): A                                                       = s.modify(f)
  inline def getAndSet[S](using s: State[S])(v: S): S                                                                 = s.getAndSet(v)
  inline def getAndUpdate[S](using s: State[S])(f: S => S): S                                                         = s.getAndUpdate(f)
  inline def localState[S, A](using s: State[S])(f: S => S)(body: State[S] ?=> A): A                                  = s.local(f)(body)
  inline def focusState[S, A, B](using s: State[S])(getFocus: S => A)(setFocus: (S, A) => S)(body: State[A] ?=> B): B =
    s.focus(getFocus)(setFocus)(body)
}
