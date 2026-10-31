# ADR-014 – Migration: charakterisieren, portieren, Parität, umschalten

**Status:** Vorgeschlagen · **Quellen:** WP5 §5–7; WP4 §4.4

## Kontext
AskAI darf zu keinem Zeitpunkt Funktion verlieren. Serp4J soll keine ausgelagerte AskAI-Suche sein.

## Entscheidung
1. Schritt 0 in AskAI (rein additiv): Fixtures echter, anonymisierter Antworten und Fehler; Golden-Tests der
   Request-Mapper. Die Fixtures wandern 1:1 nach `serp4j-testkit`.
2. Übernehmen (Ü): Request-Mapper, Ziel-URL-Fabrik, Enums (ohne Gson-Annotationen), Validatoren, `UrlQueryBuilder`,
   `SecretArrays`, DataForSEO-Statusprüfung. Refaktorieren (R): Provider-Klassen, Options, Auth-Strategien, Registry,
   Composite → Orchestrator, Bright-Data-Polling. Neu (N): Ergebnismodell, Response-Mapper, Exceptions, Transport-Port,
   Credential-Port. In AskAI (A): Persistenz, Krypto, Settings-Draft, Playground, `WebSearchProvidersModule`.
3. Paritätstest in AskAI vor dem Umschalten; nur dokumentierte Fehlerbehebungen dürfen abweichen.
4. Umschalten über eine einzige Klasse `Serp4jSearchProviderBridge` in `:research-agent-runtime`.
5. AskAI nutzt in der Entwicklung einen Composite Build, danach Release-Artefakte.

## Konsequenzen
- Kein Big-Bang; jeder Schritt ist einzeln rückrollbar.
- `AgentPluginLoadIntegrationTest` in AskAI ist Gate des Umschaltens.
- Die Umstellung in AskAI ist ein eigenes Paket (M) nach H und wird erst mit Angelos Freigabe begonnen.
