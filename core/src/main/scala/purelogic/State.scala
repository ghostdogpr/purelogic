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
}

object State {
  def apply[S, A](initial: S)(body: State[S] ?=> A): (S, A) = {
    var current: S = initial
    given State[S] = new State[S] {
      def get: S          = current
      def set(s: S): Unit = current = s
    }
    val result     = body
    (current, result)
  }

  given State[Unit] = new State[Unit] {
    def get: Unit          = ()
    def set(s: Unit): Unit = ()
  }

  def get[S](using s: State[S]): S                       = s.get
  def get[S, A](using s: State[S])(f: S => A): A         = s.get(f)
  def set[S](using s: State[S])(v: S): Unit              = s.set(v)
  def update[S](using s: State[S])(f: S => S): Unit      = s.update(f)
  def updateAndGet[S](using s: State[S])(f: S => S): S   = s.updateAndGet(f)
  def modify[S, A](using s: State[S])(f: S => (A, S)): A = s.modify(f)
  def getAndSet[S](using s: State[S])(v: S): S           = s.getAndSet(v)
  def getAndUpdate[S](using s: State[S])(f: S => S): S   = s.getAndUpdate(f)
}
