package purelogic

trait Reader[+R] {
  def ask: R
  def inquire[A](f: R => A): A = f(ask)
}

object Reader {
  def apply[R, A](value: R)(body: Reader[R] ?=> A): A = {
    given Reader[R] = new Reader[R] {
      def ask: R = value
    }
    body
  }
}
