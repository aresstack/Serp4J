# ADR-012 – HTTP-Transport- und Credential-Port; Konfiguration bleibt beim Host

**Status:** Vorgeschlagen · **Quellen:** WP4 §3.2, §4.5; WP5 §2.2, F3, F4; WP2 §8.2

## Kontext
Heute erzeugt jeder Provider einen eigenen AsyncHttpClient; Config-DTOs tragen verschlüsselte Secrets und kennen
AskAI-Pfade. WP5 wollte die Ports im Core, WP4 ein eigenes `serp4j-http-api`. WP2 hatte `httpTransport()` direkt in
`ProviderEnvironment` des Cores.

## Entscheidung
1. `serp4j-http-api` (0 Deps): `HttpTransport`, `HttpRequest`, `HttpResponse`, `RequestAuthentication`
   (Header, Bearer, Basic), `HttpTransportException`. Provider erhalten den Transport per Injektion.
2. Adapter: zuerst `serp4j-http-ahc` (Parität), später `serp4j-http-jdk` ohne Abhängigkeit. Kein Provider bringt einen
   Transport mit; optional `ServiceLoader`-Erkennung, wenn genau ein Transport vorhanden ist.
3. `CredentialProvider` + `SecretValue` (char[]) + `CredentialKey` im Core (`core.port.credential`), weil auch die
   Fabrik-SPI sie braucht. Mitgeliefert: `StaticCredentialProvider`, `EnvironmentCredentialProvider`.
4. `ProviderEnvironment#getService(Class<T>)` statt fester Methoden für Transport/Browser.
5. Datei-Konfiguration, AES-GCM, Schlüsseldatei, Settings-Draft und Playground bleiben in AskAI
   (`:research-search-config`). Kein `serp4j-config-json`, bis ein zweiter Konsument es braucht.

## Alternativen (abgelehnt)
- Ports im Core: Browser-only- und Testkonsumenten erhielten HTTP-Typen.
- Krypto in Serp4J: Speicherpolitik ist Host-Sache.

## Konsequenzen
- Tests ohne lokalen Server über `FakeHttpTransport`.
- AskAI implementiert `CredentialProvider` mit seiner verschlüsselten Ablage; Konfigurationsdateien bleiben kompatibel.
