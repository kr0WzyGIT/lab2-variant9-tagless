package infrastructure

import cats.effect.Sync
import cats.effect.std.Console
import domain.algebra.ConsoleAlg

/** Интерпретация консоли через cats-effect Console. */
final class ConsoleInterpreter[F[_]: Sync](console: Console[F]) extends ConsoleAlg[F]:
  override def printLine(text: String): F[Unit] =
    console.println(text)

  override def readLine: F[String] =
    console.readLine

object ConsoleInterpreter:
  def apply[F[_]: Sync](using console: Console[F]): ConsoleInterpreter[F] =
    new ConsoleInterpreter(console)
