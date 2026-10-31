# ADR-004 – Offene Wertetypen statt Enums, API-Konventionen

**Status:** Angenommen (Angelo, 2026-10-31) · **Quellen:** WP1 D2, D7, §4.1; WP2 E4, §3.2

## Kontext
AskAI nutzt geschlossene Enums (`SearchProviderId` mit `COMPOSITE`, `SearchEngine` mit `MULTI`). Eine Bibliothek muss
Fremd-Provider und neue Engines ohne Core-Änderung zulassen. WP1 und WP2 waren sich einig, unterschieden sich aber bei
Namen (`SearchEngineId` vs. `SearchEngine`), bei `SearchVertical` (Wertetyp vs. Enum) und beim Getter-Stil.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Enums | nicht erweiterbar. Abgelehnt. |
| B. Strings | keine Typsicherheit, Tippfehler. Abgelehnt. |
| C. `final class` mit normalisiertem String-Code und Konstanten | erweiterbar, typsicher, `equals` über Code. |

## Entscheidung
Option C für `ProviderId`, `SearchEngineId`, `SearchVertical`, `ResultType`, `SerpFeatureType`, `IntentCategory`.
Code-Format `[a-z0-9][a-z0-9._-]*`, kleingeschrieben. Keine zentrale ID-Registry; Konvention „Namespace =
Artefaktname" für Fremdwerte. Pseudo-IDs `COMPOSITE`/`MULTI` entfallen.
`SearchTarget = SearchEngineId + SearchVertical` beschreibt, was ein Aufrufer haben will.

Echte Enums nur für geschlossene technische Mengen: `SerpArea`, `DeviceType`, `SafeSearch`, `RawPayloadPolicy`,
`SearchErrorCategory`, `SessionState`, `ProviderKind`, `SearchFeature`, `ResponseFeature`.

API-Konventionen für alle Module: `public final`, unveränderlich, Builder mit Pflichtfeldern in `builder(…)`,
`toBuilder()`, **JavaBean-Getter** (`getX()`), optionale Werte nur als `Optional`-Rückgabe, Collections nie `null`,
`equals/hashCode/toString` mit Wertesemantik, `Instant`/`Duration`, Geld als `BigDecimal` + ISO-4217, URLs als `String`.

## Konsequenzen
- Mehrere Instanzen desselben Anbieters erhalten eigene `ProviderId`s; der Typ steht in `ProviderDescriptor.providerType`.
- `switch` über Engines ist nicht möglich; Adapter vergleichen mit Konstanten.
