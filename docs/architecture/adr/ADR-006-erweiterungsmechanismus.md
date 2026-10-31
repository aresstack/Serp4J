# ADR-006 – Erweiterungsmechanismus, Rohdaten und Provider-Optionen

**Status:** Vorgeschlagen · **Quellen:** WP1 D5, §4.10; WP2 §5.2, F3; WP5 §4, F5, F9

## Kontext
Kein Provider-Feld soll verloren gehen, aber der Core darf keine JSON-Bibliothek kennen und neue Provider dürfen den
Core nicht ändern müssen. WP1 schlug `Extensions` mit `ExtensionKey<T>` und `StructuredValue` vor, WP5 ein
`Map<String, JsonValue>`, WP2 typisierte `ProviderRequestOptions` für Requests. WP1 wollte `RawPayload` (byte[],
Default `NONE`), WP5 `RawResponse` (String, Default `ALWAYS`).

## Entscheidung
1. **Response-Extensions:** `Extensions` auf `SearchMetadata`, jedem `SearchElement` und `SearchAnalysis` mit
   typisierten `ExtensionKey<T>` (vom Provider-Modul veröffentlicht, z. B. `BraveExtensions.PROFILE`) und
   `getUnmapped(namespace)` als `StructuredValue` (eigener JSON-artiger Baum im Core, ohne Bibliothek).
2. **Unbekannte SERP-Elemente:** `GenericSerpFeature` mit originalem Typ-Code und Inhalt als `StructuredValue`.
3. **Request-Optionen:** typisierte `ProviderRequestOptions` je Provider, in `SearchRequest` nach `ProviderId` abgelegt.
   Fremde Optionen ignoriert ein Provider, eigene Optionen falschen Typs → `INVALID_REQUEST`.
4. **Rohdaten:** `RawPayload` in `SearchMetadata` (Content-Type, byte[] mit Kopie, Charset, HTTP-Status, gefilterte
   Header ohne Auth/Cookies). `RawPayloadPolicy` im Request: `NONE` (Bibliotheks-Default), `ON_ERROR`, `ALWAYS`.
5. **Hochstufung:** Liefern ≥ 2 Provider ein Extension-Feld, das Konsumenten brauchen, wird es in einer Minor-Version
   Core-Feld (Extension eine Version parallel, deprecated).

## Alternativen (abgelehnt)
- `Map<String, Object>`: nicht typsicher, Inhalt undefiniert.
- Gson-`JsonElement` im Core: koppelt alle Konsumenten an Gson.
- Default `ALWAYS`: unnötige Speicherlast für Bibliotheksnutzer; AskAI setzt `ALWAYS` explizit in der Bridge.

## Konsequenzen
- Ein Contract-Test je Provider: „unbekannter Typ überlebt das Mapping", „keine Secrets in Extensions/RawPayload".
- `BrowserValue` (browser-api) und `StructuredValue` (core) sind bewusst zwei Typen, weil `browser-api` den Core nicht
  kennt; die Umsetzung liegt im Google-Adapter.
