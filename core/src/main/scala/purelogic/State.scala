package purelogic

trait State[S] {
  def get: S
  def set(s: S): Unit

  def getWith[A](f: S => A): A = f(get)
  def modify(f: S => S): Unit  = set(f(get))
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
}
