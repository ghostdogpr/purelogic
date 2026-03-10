package purelogic

trait Reader[+R] {
  def ask: R
}

object Reader {
  def ask[R](using r: Reader[R]): R = r.ask

  def inquire[R, B](using r: Reader[R])(f: R => B): B = f(r.ask)

  def apply[A, R](value: R)(body: Reader[R] ?=> A): A = {
    given Reader[R] = new Reader[R] {
      def ask: R = value
    }
    body
  }
}
