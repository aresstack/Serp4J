# ADR-002 – Java 8 als Regel, Java 21 als begründete Ausnahme

**Status:** Vorgeschlagen · **Quellen:** WP4 §1.3, §5, §6.1; WP3 §2.1

## Kontext
AskAI ist ein Java-8-Host. Die Playwright-Java-API (1.62.0) ist nachweislich Java-8-Bytecode; nur der JS-Treiber
bestimmt die Java-Version: Node-`driver-bundle` läuft ab Java 8, `playwright4j` (GraalJS 25) verlangt Java 21.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Alles Java 8 | `playwright4j` wäre ausgeschlossen; Node-freier Betrieb unmöglich. |
| B. Browsermodule Java 11/17 | Unnötig: API ist Java 8; schließt Java-8-Hosts mit Node-Treiber aus. |
| C. Java 8 überall, Java 21 nur für reine Laufzeitmodule ohne eigenen Port | Maximale Reichweite, Java 21 nur dort, wo ein Treiber es verlangt. |

## Entscheidung
Option C. Java 8 für alle Module außer `serp4j-browser-playwright-driver-graal` (Java 21, nur Runtime-Abhängigkeiten,
von keinem Java-8-Modul referenziert). Baseline des späteren Camoufox-Moduls ist offene Entscheidung E-6
(Empfehlung Java 8).

Technik: Toolchain JDK 21, `options.release = 8` (prüft gegen die Java-8-API-Signaturen), zusätzlicher Testlauf auf JDK 8.
Test-Abhängigkeiten müssen auf JDK 8 laufen (JUnit 5.11.x, AssertJ 3.x, kein Mockito 5, kein WireMock 3).

## Konsequenzen
- Keine `record`, `var`, `sealed`, `HexFormat`, `Files.readString`, `java.net.http` in Java-8-Modulen.
- Fällt Java 8 bei Playwright weg, werden nur die Browsermodule angehoben; der Core bleibt unberührt.
