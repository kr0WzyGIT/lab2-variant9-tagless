package domain.algebra

/** Tagless-алгебра консольного ввода-вывода. */
trait ConsoleAlg[F[_]]:
  def printLine(text: String): F[Unit]
  def readLine: F[String]
