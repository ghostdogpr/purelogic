package purelogic

trait State[S] {
  def get: S
  def set(s: S): Unit
  def modify(f: S => S): Unit
}

object State {
  def get[S](using s: State[S]): S                  = s.get
  def set[S](using s: State[S])(v: S): Unit         = s.set(v)
  def modify[S](using s: State[S])(f: S => S): Unit = s.modify(f)

  def inspect[S, B](using s: State[S])(f: S => B): B = f(s.get)

  def apply[A, S](initial: S)(body: State[S] ?=> A): (S, A) = {
    var current: S = initial
    given State[S] = new State[S] {
      def get: S                  = current
      def set(s: S): Unit         = current = s
      def modify(f: S => S): Unit = current = f(current)
    }
    val a          = body
    (current, a)
  }
}
