package purelogic

trait State[S] {
  def get: S
  def set(s: S): Unit
}

object State {
  def apply[S, A](initial: S)(body: State[S] ?=> A): (S, A) = {
    var current: S = initial
    given State[S] = new State[S] {
      def get: S          = current
      def set(s: S): Unit = current = s
    }
    val a          = body
    (current, a)
  }

  def get[S](using s: State[S]): S                       = s.get
  def set[S](using s: State[S])(v: S): Unit              = s.set(v)
  def update[S](using s: State[S])(f: S => S): Unit      = set(f(get))
  def modify[S, A](using s: State[S])(f: S => (A, S)): A = { val (a, newState) = f(get); set(newState); a }
  def getWith[S, A](using s: State[S])(f: S => A): A     = f(get)
  def updateAndGet[S](using s: State[S])(f: S => S): S   = { update(f); get }
  def getAndSet[S](using s: State[S])(v: S): S           = { val old = get; set(v); old }
  def getAndUpdate[S](using s: State[S])(f: S => S): S   = { val old = get; update(f); old }
}
