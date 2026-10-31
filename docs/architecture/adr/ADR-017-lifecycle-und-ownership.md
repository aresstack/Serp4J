# ADR-017 – Lifecycle und Ownership gemeinsam genutzter Infrastruktur

**Status:** Angenommen (Angelo, 2026-10-31, Review-Punkt C) · **Quellen:** Angelos Review; WP2 §8.1 (Ownership in der
Registry); WP3 §6.3; WP5 §2.3 (ein Netty-Eventloop je Provider)

## Kontext
Die Architektur sieht geteilte Infrastruktur vor: ein `HttpTransport` für alle Cloud-Provider, eine `BrowserRuntime` für
`GoogleBrowserSearchProvider` und `GoogleAiModeProvider`, ein Scheduler für Polling. Ohne Regel ist unklar, wer die
Runtime schließt, wenn ein einzelner Provider geschlossen wird. Heute in AskAI erzeugt jeder Provider seinen eigenen
Client und schließt ihn; das vermeidet die Frage, kostet aber einen Eventloop je Provider.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Jeder Provider erzeugt und schließt seine eigene Infrastruktur | einfach, aber verschwenderisch (ein Browser je Provider); Hot-Reload in AskAI würde jedes Mal Browser neu starten |
| B. Referenzzählung in der Infrastruktur (`acquire`/`release`) | implizit, fehleranfällig, schwer zu testen |
| C. **Wer erzeugt, schließt.** Injizierte Infrastruktur gehört dem Aufrufer; Provider schließen nur Eigenes | explizit, testbar, entspricht der Ownership-Regel der Registry |

## Entscheidung
Option C, verbindlich für alle Module:
1. Injizierte Infrastruktur (`HttpTransport`, `BrowserRuntime`, `CredentialProvider`, Scheduler, Executor) gehört dem
   Aufrufer. Ein Provider schließt sie nie.
2. Ein Provider schließt beim `close()` nur, was er selbst erzeugt hat: seine `SearchSession`s, die von ihm geöffneten
   `BrowserSession`s, seine Polling-Aufgaben (abbrechen, nicht den Scheduler beenden) und laufende Futures (`CANCELLED`).
3. Provider beeinträchtigen sich nicht gegenseitig: Das Schließen eines Providers lässt andere Provider auf derselben
   Infrastruktur funktionsfähig.
4. Infrastruktur, die ein Provider selbst erzeugt (z. B. per `ServiceLoader` gefundener Transport ohne explizite
   Übergabe), gehört ihm und wird von ihm geschlossen. Der Builder macht den Unterschied sichtbar.
5. Dienste aus `ProviderEnvironment#getService` gehören dem Ersteller des Environments.
6. Die Registry übernimmt mit `register()` nur die Ownership des Providers, nie der Infrastruktur.
7. Abbaureihenfolge beim Aufrufer: Provider/Registry → Browser-Runtimes → Transports → Executor. Zu früh geschlossene
   Infrastruktur führt zu typisierten Fehlern (`UNAVAILABLE`, `SESSION_LOST`), nie zu Hängern.
8. `close()` ist überall idempotent und threadsicher.

## Konsequenzen
- Contract-Test im Testkit: zwei Provider auf derselben Fake-Infrastruktur; einer wird geschlossen, der andere sucht
  weiter; die Fake-Infrastruktur bleibt offen.
- Gilt für alle Adapter mit injizierter Infrastruktur. Festgeschrieben in Paket A (Port-Javadocs), geprüft ab Paket E
  (Contract-Test); B–D, T, F, G1 und G2 werden dagegen getestet.
- AskAIs Hot-Reload kann Provider austauschen, ohne Browser oder Transports neu zu starten.
