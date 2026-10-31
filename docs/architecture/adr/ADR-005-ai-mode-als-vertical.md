# ADR-005 – Google AI Mode = Engine `google` + Vertical `AI_MODE`

**Status:** Vorgeschlagen · **Quellen:** WP1 Q3, WP2 E3/F5, WP3 §7.1

## Kontext
WP3 ließ offen, ob AI Mode eine eigene Engine (`GOOGLE_AI_MODE`) ist.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Eigene Engine `google-ai-mode` | einfach zu filtern, aber vermischt Index (Engine) mit Oberfläche; Bright Data/DataForSEO liefern AI Mode als Modus derselben Engine. |
| B. Engine `google` + Vertical `AI_MODE` | entspricht News/Images; erlaubt mehreren Providern (Browser, Bright Data, DataForSEO), dasselbe Ziel anzubieten. |

## Entscheidung
Option B. Ob ein Provider AI Mode **dialogorientiert** anbietet, sagt der Port (`ConversationalSearchProvider` +
`ConversationalCapabilities`), nicht das Vertical. Ein browserloser SERP-API-Provider kann `google/AI_MODE` als
zustandslose Suche anbieten.

## Konsequenzen
- `SearchCapabilities.targets` enthält `SearchTarget.of(GOOGLE, AI_MODE)`.
- Die Registry findet AI-Mode-Anbieter über `searchProvidersFor(target)` bzw. `conversationalProvidersFor(target)`.
