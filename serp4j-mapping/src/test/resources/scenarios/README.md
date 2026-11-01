# Synthetische Szenarien (keine Provider-Fixtures)

Die Antworten in diesem Verzeichnis sind **handgeschriebene, synthetische Testdaten**. Sie prüfen die Semantik der
Mapping-Engine an drei Strukturen aus dem früheren Prototyp (`spikes/mapping-prototype`, ADR-019) und stammen von
keinem echten Provider. Nach E-1 dürfen sie nie als Grundlage einer Provider-Mapping-Definition dienen; die
Definitionen für Brave, Bright Data und DataForSEO entstehen in den Paketen B–D gegen echte, aufgezeichnete Fixtures.

| Szenario | Struktur | Ebene |
|---|---|---|
| `a` | Angelos Beispiel 1: `organic_results[]` mit `title`/`link`/`snippet`/`position` | rein deklarativ |
| `b` | Angelos Beispiel 2: `results[]` mit `name`/`url`/`description`/`rank` (Rang als String, Element ohne URL) | rein deklarativ |
| `c` | flache, polymorphe `items[]` mit `type`-Diskriminator, `rank_group`/`rank_absolute` | deklarativ + drei Custom Mapper |

Je Szenario: `definition.json` (Mapping-Definition), `response.json` (Antwort), `expected.json`
(Golden-Snapshot von `MappingSnapshot.render`).
