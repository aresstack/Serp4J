# Veröffentlichung auf Maven Central

Serp4J wird über das **Central Publisher Portal** mit dem Gradle-Plugin `com.gradleup.nmcp` veröffentlicht
(ADR-015, Entscheidung E-7). Alle Module tragen dieselbe Version (Lockstep); Koordinaten `com.aresstack:serp4j-*`.
Das Root-Projekt sammelt die Publikationen aller Module mit dem Convention-Plugin `serp4j.published` zu **einem**
Bündel (`com.gradleup.nmcp.aggregation`), das als Ganzes validiert und freigegeben wird.

## Voraussetzungen

1. Zugang zum [Central Portal](https://central.sonatype.com/) mit verifiziertem Namespace `com.aresstack`
   (ist durch `playwright4j` und `win-directml-java` bereits eingerichtet).
2. Ein **User Token** des Portals (Account → *Generate User Token*). Es besteht aus Benutzername und Passwort
   und ersetzt die Anmeldedaten des Kontos.
3. Ein **GPG-Schlüssel** für die Signatur. Der private Schlüssel wird ASCII-armored exportiert und der
   öffentliche Schlüssel auf einen Keyserver hochgeladen, den Central kennt (z. B. `keyserver.ubuntu.com`):

   ```bash
   gpg --armor --export-secret-keys <KEY-ID> > serp4j-signing-key.asc
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY-ID>
   ```

## Konfiguration

Der Build liest vier Gradle-Properties. Lokal gehören sie in `~/.gradle/gradle.properties`, **nie** in das
Repository; in GitHub Actions werden sie als `ORG_GRADLE_PROJECT_<name>` aus Secrets gesetzt.

| Gradle-Property | GitHub-Secret | Inhalt |
|---|---|---|
| `mavenCentralUsername` | `MAVEN_CENTRAL_USERNAME` | Benutzername des User Tokens |
| `mavenCentralPassword` | `MAVEN_CENTRAL_PASSWORD` | Passwort des User Tokens |
| `signingInMemoryKey` | `SIGNING_IN_MEMORY_KEY` | privater GPG-Schlüssel, ASCII-armored (Inhalt der `.asc`-Datei) |
| `signingInMemoryKeyPassword` | `SIGNING_IN_MEMORY_KEY_PASSWORD` | Passphrase des Schlüssels (leer, wenn keine) |

Fehlt `signingInMemoryKey`, wird nicht signiert; `publishToMavenLocal` und `zipAggregation` funktionieren dann
trotzdem. Das Portal lehnt unsignierte Releases ab, deshalb prüft der Release-Workflow die Secrets vorab.

## Lokal prüfen

```bash
./gradlew build                      # Tests auf JDK 21 und JDK 8, Javadoc, ArchUnit
./gradlew publishToMavenLocal        # Artefakte unter ~/.m2/repository/com/aresstack/serp4j-*/
./gradlew zipAggregation             # Central-Bündel in build/nmcp/zip/aggregation.zip, ohne Upload
```

Das Bündel enthält je Modul JAR, Sources-JAR, Javadoc-JAR, POM, Gradle-Modulmetadaten, Prüfsummen und – mit
Schlüssel – die `.asc`-Signaturen. Jede POM enthält Name, Beschreibung, URL, Lizenz, Entwickler und SCM, wie das
Portal es verlangt (`build-logic/src/main/kotlin/serp4j.published.gradle.kts`).

## Snapshots

Die Version in `gradle.properties` endet zwischen Releases auf `-SNAPSHOT`. Der CI-Workflow publiziert bei jedem
Push auf `main` Snapshots in das Snapshot-Repository des Portals, sobald die Secrets eingerichtet sind; vorher
überspringt er den Schritt mit einem Hinweis. Von Hand:

```bash
./gradlew publishAllPublicationsToCentralSnapshots
```

Konsumenten binden dafür `https://central.sonatype.com/repository/maven-snapshots/` als Repository ein.

## Release

1. `CHANGELOG.md`: den Abschnitt `## [Unreleased]` in `## [X.Y.Z] - JJJJ-MM-TT` umbenennen (und einen neuen leeren
   `[Unreleased]`-Abschnitt darüber anlegen). Der Release-Workflow bricht ab, wenn der Abschnitt zur Version fehlt.
2. Änderungen auf `main` mergen; der CI-Workflow muss grün sein.
3. Tag setzen und pushen. Die Version kommt **aus dem Tag** (`-Pversion=X.Y.Z`); `gradle.properties` bleibt
   auf `-SNAPSHOT`:

   ```bash
   git tag -a v0.1.0 -m "Serp4J 0.1.0"
   git push origin v0.1.0
   ```

4. Der Workflow `.github/workflows/release.yml` baut und testet (JDK 21 + JDK 8), lädt das signierte Bündel mit
   `publishAggregationToCentralPortal` in das Portal und legt ein GitHub Release mit dem Changelog-Abschnitt an.
5. Im Portal unter *Deployments* das Bündel prüfen und mit **Publish** freigeben (`publishingType = USER_MANAGED`
   im Root-`build.gradle.kts`). Danach ist die Version nach wenigen Minuten auf Maven Central sichtbar, in der
   Suche nach bis zu einigen Stunden.
6. Nach dem Release die Version in `gradle.properties` auf die nächste `-SNAPSHOT` anheben.

## Fehlerbilder

| Meldung des Portals / Builds | Ursache |
|---|---|
| `401 Unauthorized` beim Upload | User Token fehlt oder ist abgelaufen; neues Token erzeugen, Secrets aktualisieren |
| `Missing signature for file` | `signingInMemoryKey` nicht gesetzt oder nicht lesbar (ASCII-armored exportieren, komplette Datei einschließlich Kopf- und Fußzeile) |
| `Invalid signature` / `public key not found` | öffentlicher Schlüssel nicht auf einem Keyserver, den Central abfragt |
| `Missing javadoc/sources jar` | ein Modul ohne `serp4j.java-library` (z. B. ein reines `java-platform` muss nicht, ein Java-Modul schon) |
| `SNAPSHOT versions are not allowed` | Release ohne `-Pversion` gestartet; nur über den Tag-Workflow releasen |
