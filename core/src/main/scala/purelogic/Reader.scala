package purelogic

trait Reader[+R] {
  def read: R

  def readWith[A](f: R => A): A = f(read)
}

object Reader {
  def apply[R, A](value: R)(body: Reader[R] ?=> A): A = {
    given Reader[R] = new Reader[R] {
      def read: R = value
    }
    body
  }
}
