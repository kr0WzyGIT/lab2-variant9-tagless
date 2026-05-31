package domain.algebra

/** Tagless-алгебра логирования бизнес-событий. */
trait WarehouseLoggerAlg[F[_]]:
  def info(message: String): F[Unit]
