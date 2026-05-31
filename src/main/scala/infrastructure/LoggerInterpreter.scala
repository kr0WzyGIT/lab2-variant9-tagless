package infrastructure

import cats.effect.{Ref, Sync}
import domain.algebra.WarehouseLoggerAlg

/** Интерпретация Writer-алгебры через Ref[F, Vector[String]]. */
final class LoggerInterpreter[F[_]: Sync](logRef: Ref[F, Vector[String]]) extends WarehouseLoggerAlg[F]:
  override def info(message: String): F[Unit] =
    logRef.update(_ :+ message)

object LoggerInterpreter:
  def apply[F[_]: Sync](logRef: Ref[F, Vector[String]]): LoggerInterpreter[F] =
    new LoggerInterpreter(logRef)
