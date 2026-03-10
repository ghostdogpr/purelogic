package purelogic

trait Reader[+R] {
  def ask: R
}

object Reader {
  def ask[R](using r: Reader[R]): R = r.ask

  def inquire[R, A](using r: Reader[R])(f: R => A): A = f(r.ask)

  def apply[R, A](value: R)(body: Reader[R] ?=> A): A = {
    given Reader[R] = new Reader[R] {
      def ask: R = value
    }
    body
  }
}
