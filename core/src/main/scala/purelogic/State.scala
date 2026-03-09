package purelogic

sealed trait State[S] {
  def get: S
  def set(s: S): Unit
  def modify(f: S => S): Unit
}

object State {
  def get[S](using s: State[S]): S                  = s.get
  def set[S](using s: State[S])(v: S): Unit         = s.set(v)
  def modify[S](using s: State[S])(f: S => S): Unit = s.modify(f)

  def inspect[S, B](using s: State[S])(f: S => B): B = f(s.get)

  private[purelogic] def makeState[S](initial: S): (State[S], () => S) = {
    var current: S = initial
    val state      = new State[S] {
      def get: S                  = current
      def set(s: S): Unit         = current = s
      def modify(f: S => S): Unit = current = f(current)
    }
    (state, () => current)
  }
}
