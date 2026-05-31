# ЛР2 — Tagless Final (Вариант 9: Склад заказов)

Переработка ЛР1: та же предметная область, но эффекты описаны через **tagless-алгебры** `F[_]` (Cats Effect).

## Отличие от ЛР1

| ЛР1 | ЛР2 |
|-----|-----|
| Свои `Reader` / `Writer` / `State` / `IO` | Алгебры `trait XxxAlg[F[_]]` |
| Логика в `State.run` / `Writer(...)` | `WarehouseService[F]` — только `F` через алгебры |
| `WarehouseApp` вызывает `processOrder` напрямую | `program[F[_]: Sync](using ...)` + интерпретаторы |

## Структура

- `domain/algebra` — tagless-интерфейсы (`WarehouseReaderAlg`, `WarehouseLoggerAlg`, `WarehouseStateAlg`, `ConsoleAlg`);
- `domain/WarehouseService.scala` — бизнес-логика **в `F[_]`**;
- `infrastructure` — интерпретации (конфиг, `Ref` для состояния и лога, `Console`);
- `app/WarehouseApp.scala` — сценарий с `program[F[_]: Sync](using ...)`;
- `src/test` — 4 обязательных кейса.

## Запуск

```bash
sbt run
sbt test
```

## Ключевой паттерн (как на лекции)

```scala
def program[F[_]: Sync](using
    reader: WarehouseReaderAlg[F],
    logger: WarehouseLoggerAlg[F],
    state: WarehouseStateAlg[F],
    console: ConsoleAlg[F]
): F[Unit] = ...

given WarehouseReaderAlg[IO] = ReaderInterpreter[IO](config)
// ...
program[IO].unsafeRunSync()
```
