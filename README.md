# NadajBackup

Prosty plugin na serwery Paper/Spigot 1.20+, który łączy komendę `/nadajbackup <nick>` z `/ir restore <nick>` z pluginu InventoryRollbackPlus.

## Użycie

```
/nadajbackup <nick>
```

Wykonuje `/ir restore <nick>` jako gracz, który wpisał komendę (komenda otwiera menu, więc nie działa z konsoli).

## Uprawnienia

- `nadajbackup.use` – dostęp do `/nadajbackup` (domyślnie tylko OP)
- osoba używająca komendy musi mieć też uprawnienie do `/ir restore` w InventoryRollbackPlus

## Budowanie

Wymagane: Java 17 i Maven.

```
mvn package
```

Gotowy plik: `target/NadajBackup.jar`. Wrzuć go do folderu `plugins` i zrestartuj serwer.
