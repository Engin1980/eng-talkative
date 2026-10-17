# Co je potřeba připravit na počítači (úkoly pro uživatele)

Dokument popisuje, co musí být hotové na tomto počítači, abych mohl aplikaci naprogramovat, sestavit a vyzkoušet. Jednotlivé body odškrtávej.

Stav zjištěný na počítači (Windows 10): `git` je nainstalovaný. **Chybí** Java (JDK), Android SDK, `adb`, emulátor i Android Studio. Proměnné `JAVA_HOME` a `ANDROID_HOME` nejsou nastavené. RAM je dostatek (cca 30 GB), virtualizace (hypervisor) je zapnutá.

Od kroku 0 v `plan.md` potřebuji body 1 až 5. Body 6 až 9 jsou potřeba až později (kroky 3, 4, 9 a 11).

---

## Nutné hned (krok 0)

### 1. JDK 17
- Nainstalovat JDK 17 (např. Eclipse Temurin 17) a nastavit `JAVA_HOME` na jeho adresář.
- Ověření: v novém terminálu `java -version` vypíše verzi 17 a `echo $env:JAVA_HOME` vrací cestu.
- Poznámka: Android Studio obsahuje vlastní JDK, takže pokud nainstaluješ Android Studio (bod 2), stačí `JAVA_HOME` nasměrovat na jeho podsložku `jbr`. Samostatný JDK je ale pro příkazový řádek jednodušší.

### 2. Android Studio a Android SDK
- Nainstalovat aktuální stabilní Android Studio. Při prvním spuštění nechat nainstalovat standardní SDK.
- V SDK Manageru nainstalovat:
  - **Android SDK Platform** pro aktuální cílové API (např. API 35) a **API 26** (minimální verze, pro testy na staré verzi),
  - **Android SDK Build-Tools**,
  - **Android SDK Platform-Tools** (obsahuje `adb`),
  - **Android SDK Command-line Tools (latest)**,
  - **Android Emulator**.
- Nastavit proměnnou `ANDROID_HOME` (typicky `C:\Users\<uživatel>\AppData\Local\Android\Sdk`) a přidat do `PATH` složky `platform-tools`, `emulator` a `cmdline-tools\latest\bin`.
- Odsouhlasit licence: `sdkmanager --licenses`.
- Ověření: `adb version`, `sdkmanager --list` a `emulator -version` fungují z nového terminálu.

### 3. Přístup k internetu pro Gradle
- Počítač musí mít přístup na `dl.google.com`, `maven.google.com`, `repo.maven.apache.org` a `plugins.gradle.org` (stahování knihoven a Gradle).
- Pokud je za firemním proxy nebo firewallem, napiš mi nastavení proxy (do `gradle.properties`), případně povol tyto domény.

### 4. Oprávnění pro Claude Code
- Povolit mi spouštět v terminálu příkazy `./gradlew` (resp. `gradlew.bat`), `adb`, `emulator` a `sdkmanager`, abych mohl projekt sestavit a testovat bez neustálého potvrzování.

### 5. Verzování
- Složka `D:\repos\eng-talkative` zatím není git repozitář. Doporučuji `git init` a první commit se současnou dokumentací, abychom měli historii před začátkem kódování. (Commit udělám jen na tvůj pokyn.)

---

## Pro ladění a ověřování na zařízení (od kroku 1)

### 6. Emulátor tabletu (AVD)
- Vytvořit v Android Studiu (Device Manager) alespoň dva virtuální tablety:
  - moderní (např. Pixel Tablet, aktuální API, systémový obraz s **Google Play**, protože potřebuji přihlášení Google účtem a Google TTS),
  - minimální (API 26 / Android 8.0) pro kontrolu kompatibility.
- Použít x86_64 obrazy s hardwarovou akcelerací. Na tomto počítači je zapnutý hypervisor, takže by mělo stačit mít zapnutou Windows funkci „Platforma hypervizoru Windows“ (WHPX) nebo nainstalovaný Android Emulator Hypervisor Driver.
- Ověření: emulátor se spustí do několika desítek sekund a `adb devices` ho vypíše.

### 7. Český hlas pro TTS na emulátoru
- V emulátoru nainstalovat/povolit **Hlasový výstup Google (Google Speech Services)** a stáhnout **český** hlas (Nastavení → Systém → Jazyky → Převod textu na řeč). Bez toho nejde ověřit krok 3 (přehrání věty) ani krok 5.
- Totéž na reálném tabletu, pokud ho budeš používat.

### 8. Reálný tablet (doporučeno, ne nutné)
- Zapnout v tabletu Možnosti pro vývojáře a **Ladění přes USB** (nebo bezdrátové ladění), připojit k počítači a potvrdit ladicí klíč.
- Ověření: `adb devices` zobrazí tablet jako `device`.
- Napiš mi model a verzi Androidu tabletu, aby se mohl ověřit minSdk a velikost mřížky (v zadání je zatím „obecný tablet“).

### 9. Testovací soubory
- Několik obrázků (JPG/PNG, různé velikosti, alespoň jeden velký > 4000 px) a několik MP3 souborů (krátký, dlouhý nad limit, poškozený) nahrát do emulátoru (např. `adb push`) pro kroky 4 a 5.
- Pokud je nemáš, vygeneruji vhodné soubory sám, stačí mi potvrdit.

---

## Pro synchronizaci s Google Drive (krok 9)

### 10. Google Cloud projekt a OAuth
Bez tohoto nelze synchronizaci vyzkoušet. Potřebuji, abys na [Google Cloud Console](https://console.cloud.google.com/) udělal(a):
- Vytvořit projekt (např. „Talkative“).
- Zapnout **Google Drive API**.
- Nastavit **OAuth consent screen** (typ External, stav Testing) a přidat testovací uživatele (Google účty, kterými budeme testovat). Scope `drive.appdata` je bez ověřování aplikace v testovacím režimu použitelný.
- Vytvořit **OAuth client ID typu Android** s package name `cz.talkative.app` a **SHA-1 otiskem ladicího klíče** (debug keystore). SHA-1 získáš příkazem `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android` (debug keystore vznikne po prvním sestavení v Android Studiu).
- Pro release APK později přidat druhý OAuth klient s SHA-1 release klíče (bod 11).
- Pošli mi: název projektu a ID Android klienta (ID klienta není tajné, tajné údaje po mně nechci).

### 11. Google účty pro testování
- Alespoň jeden **testovací Google účet** (ne tvůj hlavní), přidaný v consent screen jako testovací uživatel, přihlášený na emulátoru/tabletu.
- Pro test synchronizace mezi zařízeními ideálně dvě zařízení (dva emulátory nebo emulátor + reálný tablet) se stejným účtem.
- Přihlašovací údaje mi nesdílej, přihlášení v emulátoru provedeš ty.

---

## Pro předání (krok 11)

### 12. Podpisový klíč pro release
- Vytvořit release keystore (`keytool -genkeypair ...`) a uložit ho **mimo repozitář** na bezpečné místo včetně zálohy. Ztráta klíče znamená, že aplikaci nepůjde aktualizovat.
- Údaje klíče (heslo) do repozitáře nepatří. Ukáži ti, jak je předat Gradlu přes lokální soubor nebo proměnné prostředí.

---

## Co od tebe potřebuji ještě vědět

- Zda smím instalovat/stahovat věci sám (např. přes `sdkmanager`), nebo to chceš udělat ručně.
- Zda mám na tomto počítači spouštět emulátor (zabírá hodně paměti), nebo budeš testovat na reálném tabletu.
- Model a verzi Androidu cílového tabletu, až bude známa.
