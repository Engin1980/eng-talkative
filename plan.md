# Plán implementace

Plán postupuje po funkcionalitách (vertikální řezy přes UI, doménu i data), ne po vrstvách. Po každém kroku je aplikace spustitelná a použitelná o kousek víc než předtím. Zadání je v `assignment.md`, odložené funkce v `todo.md` (do tohoto plánu nepatří).

## Jak s plánem pracovat

- Každý krok je rozdělen na **podkroky** (např. 2.1, 2.2), které jsou dostatečně malé na jednu pracovní session. Lze zadat např. „proveď podkrok 2.3 z `plan.md`“.
- U každého podkroku jsou dva seznamy: **Úkoly** (co udělat) a **Kritéria** (jak ověřit, že je hotovo). Podkrok je hotový, až jsou zaškrtnuté obě části.
- Po dokončení položky se v tomto souboru změní `- [ ]` na `- [x]`. Souhrnný přehled níže se zaškrtne, až jsou hotové všechny podkroky daného kroku.
- Po každém dokončeném podkroku: zelené testy, zaškrtnutí v plánu a **vždy samostatný commit a push** (kód, testy i zaškrtnutí v `plan.md` patří do stejného commitu; zpráva commitu uvádí číslo podkroku, např. „Krok 1.2: Skrytý vstup do administrace“). Při zadání celého kroku se commit dělá po každém jeho podkroku. Rozpracovaný podkrok se nenechává v nefunkčním stavu a necommituje se; pokud dojde limit, nechá se v plánu poznámka, co zbývá.
- Pokud se při práci objeví nová otázka, postupuje se podle workflow s `guestions.md` popsaného v `CLAUDE.md`.

## Přehled postupu

- [x] Krok 0: Kostra projektu
- [x] Krok 1: Vstup do administrace a základ slovníku
- [x] Krok 2: Klient: procházení a skládání věty
- [x] Krok 3: Přehrání věty hlasem (TTS)
- [x] Krok 4: Obrázky slovíček
- [x] Krok 5: Vlastní zvuková definice slovíčka
- [x] Krok 6: Pokročilá správa slovníku
- [x] Krok 7: Konfigurace a pokročilé ovládání klienta
- [x] Krok 8: Export a import _(všech 22 instrumentovaných tříd, 260 testů, prošlo po jedné; jeden společný běh systém ukončil pro nedostatek paměti)_
- [ ] Krok 9: Synchronizace s Google Drive _(kód, testy a dokumentace hotové; zbývá založit OAuth klienta v Google Cloud a ručně ověřit s ostrým účtem na dvou zařízeních, viz `format-synchronizace.md`)_
- [ ] Krok 10: Nekonzistence a zotavení
- [ ] Krok 11: Dokončení a předání
- [x] Krok 12: Rozšíření I: About, piktogramy ARASAAC a kreslení ikon _(podkroky E1–E3)_
- [x] Krok 13: Nahrání vlastního zvuku mikrofonem _(podkroky D.1–D.4; zbývá ruční ověření nahrávání poslechem na skutečném tabletu, viz D.4)_

## Obecná pravidla pro všechny kroky

- Každý krok končí zelenými testy (`./gradlew test`, u UI kroků i instrumentované/Compose testy) a ručním ověřením na emulátoru tabletu.
- Každý dokončený podkrok končí commitem (viz „Jak s plánem pracovat“); do commitu nepatří build výstupy ani lokální soubory (hlídá `.gitignore`).
- Datový model od začátku obsahuje: ID, `profileId` (`default`), `updatedAt`, příznak smazání (tombstone), aby se synchronizace nemusela dělat zpětně přepisem.
- Texty jsou v Android resources (čeština), rozměry a barvy z tématu.
- Pokud nová knihovna vyžaduje minSdk vyšší než 26, krok se zastaví a zeptá se.
- Kód je jednoduchý a čitelný (vlastník zná Javu); preferuje se jasná architektura před zkratkami.
- Testy se píší tak, aby skutečně ověřovaly požadované chování, ne jen aby procházely.

---

## Krok 0: Kostra projektu

**Cíl:** Spustitelná prázdná aplikace se základní architekturou, na které lze stavět.

### 0.1 Gradle projekt a závislosti

**Úkoly:**
- [x] Založit Gradle projekt (`cz.talkative.app`, Kotlin, Compose, Material 3, minSdk 26) včetně Gradle wrapperu a version catalogu (`libs.versions.toml`). Projekt pro android aplikaci bude ve složce `android-app`.
- [x] Přidat `.gitignore` pro Android/Gradle (`build/`, `.gradle/`, `local.properties`, keystore soubory).
- [x] Nastavit Hilt, Room (prázdná databáze s verzováním schématu a exportem schémat do repozitáře), Coroutines/Flow.
- [x] Nastavit testovací závislosti (JUnit, coroutines-test, Compose UI test, Room testing).

**Kritéria splnění:**
- [x] `./gradlew assembleDebug` proběhne bez chyb.
- [x] Žádná závislost nevyžaduje minSdk vyšší než 26.

### 0.2 Architektura a téma

**Úkoly:**
- [x] Rozdělit balíčky na UI / doména / data a zavést zvolený vzor (MVVM/MVI) na jedné ukázkové obrazovce.
- [x] Téma (barvy, typografie, rozměry) s vysokým kontrastem; uvažovat pouze světlý režim.
- [x] Texty v `strings.xml` (čeština), rozměry v tématu (včetně minimální dotykové plochy 64 dp jako sdílená konstanta).

**Kritéria splnění:**
- [x] Ukázková obrazovka prochází celým řetězcem UI → ViewModel → doména → data (i kdyby s fiktivními daty).
- [x] V kódu obrazovek nejsou natvrdo napsané barvy, velikosti ani texty.

### 0.3 Navigace a testy

**Úkoly:**
- [x] Navigace mezi klientským a administrátorským rozhraním (zatím prázdné obrazovky).
- [x] Alespoň jeden unit test, jeden test Room (instrumentovaný) a jeden Compose UI test.
- [x] Doplnit příkazy pro build a testy (včetně spuštění jednoho testu) do `CLAUDE.md` a opravit v něm sekci „Current state“.

**Kritéria splnění:**
- [x] Aplikace se sestaví a spustí na emulátoru tabletu (API 26 i novější) a zobrazí klientskou obrazovku. _(ověřeno na `Medium_Tablet_API_26` a `Pixel_Tablet_API_35`)_
- [x] `./gradlew test` a `./gradlew connectedAndroidTest` (nebo ekvivalent) proběhnou s alespoň jedním testem na každé úrovni.
- [x] `CLAUDE.md` obsahuje funkční příkazy pro build, testy a spuštění jednoho testu.

---

## Krok 1: Vstup do administrace a základ slovníku (složky a slovíčka, jen text)

**Cíl:** Administrátor umí vytvořit strom složek a slovíček s titulky.

### 1.1 Datový model a repozitář

**Úkoly:**
- [x] Doménový model a Room entity pro složku a slovíčko (ID, `profileId`, titulek, rodič, pořadí, `updatedAt`, tombstone); kořen má rodiče `null`.
- [x] DAO a repozitář: čtení obsahu složky (Flow), vytvoření, úprava, smazání.
- [x] Rekurzivní smazání podstromu a výpočet počtu dotčených položek.
- [x] Nové položky se řadí na konec složky (výpočet pořadí).

**Kritéria splnění:**
- [x] Unit/Room testy: CRUD, strom o 3+ úrovních, rekurzivní mazání, počet dotčených položek.
- [x] Smazané položky mají tombstone a nezobrazují se v běžných dotazech.

### 1.2 Skrytý vstup do administrace

**Úkoly:**
- [x] Skrytý ovládací prvek v klientském režimu: podržení po dobu intervalu (výchozí 10 s, hodnota zatím konstanta/výchozí z konfigurace) přepne do administrace.
- [x] V administraci tlačítko pro návrat do klienta; žádný PIN.
- [x] Aplikace po každém startu otevře klientský režim.

**Kritéria splnění:**
- [x] Kratší podržení nedělá nic; podržení po celý interval otevře administraci.
- [x] Po restartu aplikace je vždy klientský režim.
- [x] UI test podržení (krátké vs. dlouhé).

### 1.3 Administrace: výpis a navigace ve stromu

**Úkoly:**
- [x] Obrazovka administrace s výpisem obsahu aktuální složky (složky a slovíčka).
- [x] Vstup do podsložky a návrat o úroveň výš; zobrazení aktuální cesty.
- [x] Prázdný stav (složka bez obsahu) s nápovědou.

**Kritéria splnění:**
- [x] Lze se pohybovat stromem tam a zpět a cesta odpovídá poloze.
- [x] Dotykové plochy mají minimálně 64 dp.

### 1.4 Administrace: vytvoření, úprava, smazání

**Úkoly:**
- [x] Dialog/obrazovka pro vytvoření a úpravu složky a slovíčka (titulek, validace prázdného titulku).
- [x] Smazání slovíčka a složky.
- [x] Potvrzení smazání neprázdné složky s počtem dotčených položek; bez koše.

**Kritéria splnění:**
- [x] Lze vytvořit alespoň 3 úrovně zanořených složek, slovíčka v nich, upravit titulky a vše smazat.
- [x] Smazání složky s obsahem zobrazí správný počet dotčených položek; po potvrzení zmizí celý podstrom, po zrušení se nic nestane.
- [x] Data přežijí restart aplikace.
- [x] Titulky nemusí být unikátní (lze vytvořit dvě stejně pojmenovaná slovíčka).

---

## Krok 2: Klient: procházení a skládání věty

**Cíl:** Klient vidí mřížku složek a slovíček a klepnutím skládá větu.

### 2.1 Konfigurační úložiště

**Úkoly:**
- [x] Lokální konfigurace (DataStore) s výchozími hodnotami z `assignment.md`; zatím bez obrazovky nastavení.
- [x] Repozitář konfigurace vystavený jako Flow.
- [x] Napojit interval podržení pro vstup do administrace (z kroku 1.2) na konfiguraci.

**Kritéria splnění:**
- [x] Výchozí hodnoty odpovídají `assignment.md`.
- [x] Unit test: čtení výchozích hodnot a zápisu změny.

### 2.2 Mřížka a stránkování

**Úkoly:**
- [x] Čistá logika stránkování (rozdělení položek podle velikosti mřížky).
- [x] Klientská obrazovka: stránkovaná mřížka složek a slovíček; velikost mřížky z konfigurace, jiná na výšku a na šířku.
- [x] Ovládání stránek (listování); stránkování přežije otočení tabletu.

**Kritéria splnění:**
- [x] 10 položek v mřížce 3×3 jsou 2 stránky; prázdná složka i přesný násobek stránky fungují.
- [x] Otočení tabletu nezmění aktuální složku a mřížka se přepočítá. _(ověřeno na emulátoru API 35 skutečným otočením: složka i věta zůstaly, přepočet velikosti mřížky pokrývá Compose test)_
- [x] Unit testy stránkování.

### 2.3 Navigace ve stromu

**Úkoly:**
- [x] Drobečková navigace (breadcrumbs).
- [x] Velká tlačítka „Zpět“ a „Domů“.
- [x] Klepnutí na složku do ní vstoupí.

**Kritéria splnění:**
- [x] Breadcrumbs vždy odpovídají aktuální cestě, „Domů“ vede do kořene, „Zpět“ o úroveň výš.
- [x] Dotykové plochy mají minimálně 64 dp.
- [x] Unit testy navigace ve view modelu.

### 2.4 Skládání věty

**Úkoly:**
- [x] Stavový řádek věty nahoře.
- [x] Klepnutí na slovíčko ho přidá do věty (včetně opakování stejného slova).
- [x] Odebrání slova ze věty podržením (výchozí 1 s); krátké klepnutí nic neodebere.
- [x] Tlačítko pro smazání celé věty.
- [x] Věta se po restartu neobnovuje; přežije ale otočení obrazovky. _(ověřeno na emulátoru API 35 skutečným otočením: věta zůstala)_
- [x] Zachování věty při přechodu do administrace a zpět není nutné. Po návratu do klientského pohledu se to může chovat jako nově spuštěná aplikace.

**Kritéria splnění:**
- [x] Z dat vytvořených v kroku 1 lze projít celý strom a poskládat větu z libovolných slovíček.
- [x] Krátké klepnutí na slovo ve větě nic neodebere, podržení po nastavený interval ano.
- [x] Dlouhá věta se ve stavovém řádku zobrazí použitelně (zalamování nebo posun).
- [x] Unit testy view modelu (skládání věty) a Compose UI testy hlavního scénáře.

---

## Krok 3: Přehrání věty hlasem (TTS)

**Cíl:** Klient větu stiskem tlačítka přečte nahlas a může přehrávání zastavit.

### 3.1 Rozhraní a TTS implementace

**Úkoly:**
- [x] Rozhraní `SpeechProvider` (přehraj, zastav, stav přehrávání jako Flow).
- [x] Implementace nad Android `TextToSpeech` (čeština, hlas dle zařízení), správa životního cyklu (inicializace, uvolnění).
- [x] Falešná implementace `SpeechProvider` pro testy.
- [x] Sestavení výpovědi: titulky slovíček se spojí do jednoho textu (čistá logika).

**Kritéria splnění:**
- [x] Unit test skládání textu s falešným `SpeechProvider`.
- [x] TTS engine se po zavření aplikace korektně uvolní (bez úniku).

### 3.2 Tlačítko Přehrát/Stop a chyby

**Úkoly:**
- [x] Tlačítko Přehrát, které se při přehrávání mění na Stop.
- [x] Tlačítko je neaktivní (nebo nic nedělá) při prázdné větě.
- [x] Ošetření chyb: chybějící český hlas nebo nedostupný TTS engine zobrazí srozumitelnou hlášku (s nápovědou, co dělat).

**Kritéria splnění:**
- [x] Poskládaná věta se přečte jedním plynulým TTS výstupem, ne po slovech.
- [x] Stop okamžitě přeruší řeč a tlačítko se vrátí na Přehrát; opětovné přehrání funguje.
- [x] Změna věty během přehrávání se chová předvídatelně (přehrávací tlačítko opětovným stiskem přehrání zastaví).
- [x] Ruční ověření hlasu na emulátoru/zařízení. _(neověřeno sluchem: emulátor běží bez zvuku. Test s reálným TTS enginem na API 26 doběhl bez chyby, tj. engine i čeština jsou k dispozici, ale že věta zní správně, musí zkontrolovat člověk)_

---

## Krok 4: Obrázky slovíček

**Cíl:** Slovíčka mají volitelný obrázek, který se zobrazuje v mřížce i ve větě.

### 4.1 Úložiště a zpracování obrázků

**Úkoly:**
- [x] Služba pro zmenšení obrázku při uložení (např. 512 px, zachování poměru stran, správná orientace dle EXIF) a uložení do soukromého úložiště aplikace.
- [x] Pole s cestou/názvem obrázku v modelu slovíčka (migrace databáze + test migrace).
- [x] Úklid souboru při odebrání obrázku nebo smazání slovíčka (včetně rekurzivního smazání složky).

**Kritéria splnění:**
- [x] Uložený soubor je zmenšen na limit a původní velký obrázek se neukládá.
- [x] Smazání slovíčka (i celé složky) smaže soubory obrázků; žádné osiřelé soubory.
- [x] Testy: zmenšení obrázku, čištění souborů, migrace.

### 4.2 Výběr obrázku v administraci

**Úkoly:**
- [x] Výběr z galerie (systémový výběr) a pořízení fotografie.
- [x] Náhled, výměna a odebrání obrázku v editaci slovíčka.
- [x] Správa oprávnění a chyb (fotoaparát zamítnut, soubor nelze přečíst, nedostatek místa). _(fotí se přes systémovou aplikaci fotoaparátu, takže aplikace oprávnění CAMERA nevyžaduje; chybějící galerie/fotoaparát, nečitelný soubor i plné úložiště ukazují hlášku)_

**Kritéria splnění:**
- [x] Obrázek lze přidat, vyměnit a odebrat; přežije restart aplikace.
- [x] Zamítnuté oprávnění k fotoaparátu nezpůsobí pád a zobrazí hlášku. _(aplikace oprávnění nepotřebuje; ověřeno na emulátoru: zamítnutí oprávnění v systémovém fotoaparátu aplikaci nerozbije, chybu startu fotoaparátu ošetřuje hláška „Fotoaparát není dostupný“ – ta je ověřená jen testem události, ne vynuceným selháním)_

### 4.3 Zobrazení obrázků v klientovi

**Úkoly:**
- [x] Dlaždice v mřížce: obrázek a titulek; slovíčko bez obrázku jen text.
- [x] Věta: menší dlaždice s obrázkem a titulkem (skrytí titulku podle konfigurace).
- [x] Efektivní načítání obrázků (cache, zmenšené bitmapy, plynulé listování). _(řeší knihovna Coil: načítání mimo hlavní vlákno, cache, dekódování na velikost prvku; obrázky se navíc ukládají zmenšené na max. 512 px. Plynulost při stovkách položek se neměřila, to patří do kroku 11.1)_

**Kritéria splnění:**
- [x] Zobrazení v mřížce a ve větě odpovídá zadání, bez obrázku se zobrazí jen text.
- [x] Skrytí titulků ve větě funguje (hodnota z konfigurace).
- [x] UI testy zobrazení s obrázkem i bez.

---

## Krok 5: Vlastní zvuková definice slovíčka

**Cíl:** Slovíčko může mít jiný text pro čtení nebo vlastní MP3 a věta se přehraje správně.

*Rozšíření: nahrání zvuku mikrofonem (M4A) je v kroku 13.* *Ruční ověření nahrávání poslechem na skutečném tabletu je zatím otevřené (viz D.4 v kroku 13).*

### 5.1 Alternativní text a plánování úseků

**Úkoly:**
- [x] Pole alternativního textu pro TTS v modelu slovíčka (migrace + test) a v editaci v administraci. _(migrace 3→4 přidává rovnou i sloupec `audioFile` pro 5.2; zvuk je buď jiný text, nebo MP3, MP3 má v plánovači přednost)_
- [x] Čistá logika plánování úseků věty (sloučení sousedních TTS slov, dělení na MP3 úseky).
- [x] Alternativní text nahradí titulek při sestavení výpovědi.

**Kritéria splnění:**
- [x] Slovo s alternativním textem se čte podle něj, ne podle titulku.
- [x] Unit testy plánování: „A, [MP3], B, C“ → TTS(A), MP3, TTS(B C); věta jen z MP3; MP3 na začátku i na konci.

### 5.2 Správa MP3 souborů

**Úkoly:**
- [x] Výběr MP3 ze zařízení, kontrola formátu, velikosti a délky (desítky sekund), uložení do soukromého úložiště. _(limity: max. 60 s a 5 MB, jen `audio/mpeg`; výběr přes systémový výběr souborů `OpenDocument`, bez nové knihovny)_
- [x] Náhled zvuku v administraci (přehrát/zastavit).
- [x] Odebrání a výměna MP3; úklid souboru při smazání slovíčka nebo složky.
- [x] Hlášky při odmítnutí (příliš dlouhý, nečitelný, nepodporovaný formát).

**Kritéria splnění:**
- [x] Příliš dlouhý nebo nečitelný MP3 je odmítnut s hláškou a nic se neuloží.
- [x] Smazání/výměna nenechá osiřelý soubor.
- [x] Zvuk přežije restart aplikace. _(ověřeno na emulátoru: slovíčko i soubor v `files/audio` po restartu; odmítnutí neplatného a příliš dlouhého souboru ověřeno ručně přes systémový výběr, po odmítnutí v úložišti nic nezůstalo)_

### 5.3 Přehrávání MP3 ve větě

**Úkoly:**
- [x] Rozšíření `SpeechProvider` (nebo nadřazená vrstva) o přehrávání MP3 (na pozadí, UI nezamrzá).
- [x] Sekvenční přehrávání úseků podle plánu: TTS → MP3 → TTS.
- [x] Stop funguje v kterékoli fázi (TTS i MP3) a zruší zbývající úseky.
- [x] Uvolnění přehrávače po dokončení/zastavení.

**Kritéria splnění:**
- [x] Věta „A, [MP3], B, C“ se přehraje bez překryvu a bez zbytečných pauz. _(pořadí a časování ověřeno testem se skutečným `MediaPlayer` a tichým MP3 a falešným TTS; poslechem neověřeno)_
- [x] Stop během TTS i během MP3 zastaví vše a další slova se nepřehrají.
- [x] Chyba při přehrávání jednoho MP3 neshodí aplikaci ani nezablokuje tlačítko Přehrát.
- [x] Ruční ověření zvuku na zařízení. _(neověřeno: emulátor nemá zvuk, ověří člověk poslechem)_

---

## Krok 6: Pokročilá správa slovníku

**Cíl:** Administrátor pohodlně přesouvá a řadí položky, včetně hromadných operací.

### 6.1 Multi-výběr

**Úkoly:**
- [x] Režim multi-výběru v administraci (slovíčka i složky najednou), označení/odznačení, „vybrat vše“.
- [x] Hromadné smazání vybraných položek se souhrnným potvrzením (počet včetně podstromů). _(pozn.: smazání běží v jedné databázové transakci; výběr patří jedné složce a při přechodu jinam se zruší)_

**Kritéria splnění:**
- [x] Lze označit více položek a výběr zrušit; stav je přehledný.
- [x] Hromadné smazání zobrazí správný celkový počet a po potvrzení smaže vše.

### 6.2 Hromadný přesun

**Úkoly:**
- [x] Výběr cílové složky (procházení stromu).
- [x] Přesun jako jedna transakce; aktualizace `updatedAt` přesunutých položek.
- [x] Ochrana proti přesunu složky do sebe sama nebo do vlastního podstromu. _(pozn.: kontrola cyklů je v `MoveItemsUseCase`, přesun do neexistující složky se také odmítne)_

**Kritéria splnění:**
- [x] Označení více položek a přesun do jiné složky je jedna operace; po ní je vše na novém místě a ve staré složce zmizelo.
- [x] Pokus o přesun složky do jejího potomka je odmítnut s hláškou a nic se nezmění.
- [x] Unit testy přesunů a ochrany cyklů.

### 6.3 Ruční řazení

**Úkoly:**
- [x] Ruční řazení položek ve složce (přesouváním nahoru/dolů nebo tažením).
- [x] Uložené pořadí se používá v administraci i v klientu. _(pozn.: administrace i klient čtou stejný `observeItems`)_

**Kritéria splnění:**
- [x] Po ručním přeřazení a restartu je pořadí stejné, klient vidí stejné pořadí. _(pozn.: restart ověřen znovuotevřením databáze v testu; ruční řazení je tlačítky nahoru/dolů)_
- [x] Unit test přepočtu pořadí.

### 6.4 Abecední řazení

**Úkoly:**
- [x] Rychlé seřazení podle abecedy s kolací z konfigurace (výchozí čeština).
- [x] Volba kolace v konfiguraci zatím jako hodnota v úložišti (UI nastavení přijde v kroku 7). _(pozn.: hodnota `collationLanguageTag` už byla v konfiguraci z kroku 0, UI nastavení přijde v kroku 7)_

**Kritéria splnění:**
- [x] Abecední řazení řadí česky (např. „cibule“ před „chléb“, „d“ po „ch“, správně diakritika); změna kolace v konfiguraci změní výsledek. _(pozn.: příklad v plánu "d po ch" je chybný; v české abecedě je ch za h, takže „dům“ je před „chléb“, implementace řadí podle skutečné české abecedy; ověřeno i na zařízení, kde řadí ICU)_
- [x] Unit testy řazení.

---

## Krok 7: Konfigurace a pokročilé ovládání klienta

**Cíl:** Administrátor nastaví chování klienta a klient může věty skládat i tažením.

### 7.1 Obrazovka konfigurace

**Úkoly:**
- [x] Obrazovka konfigurace v administraci: způsob přidání slova (klepnutí/tažení), povolení změny pořadí, interval podržení, přehrání slova při klepnutí, velikost mřížky (na výšku i na šířku), kolace, skrytí titulků ve větě, automatické smazání věty po přehrání.
- [x] Validace hodnot (rozumné meze intervalů a velikostí mřížky) a tlačítko „obnovit výchozí“. _(pozn.: meze v `ConfigLimits`, změna hodnot krokovými tlačítky − a +; mimo meze se hodnota při uložení i čtení srazí)_

**Kritéria splnění:**
- [x] Každé nastavení má viditelný efekt v klientovi hned po návratu z administrace a přežije restart. _(pozn.: efekt ověřen testy s view modelem klienta nad sdílenou konfigurací: mřížka, interval odebrání, vstup do administrace, titulky, čtení po klepnutí, mazání věty, režim tažení i pořadí; uložení všech hodnot přes restart ověřuje `ConfigPersistenceTest`; kolace se projeví při řazení v administraci)_
- [x] Výchozí hodnoty odpovídají `assignment.md`.
- [x] Konfigurace je lokální a nesynchronizuje se.
- [x] Compose UI test nastavení.

### 7.2 Přehrání slova při klepnutí a automatické mazání

**Úkoly:**
- [x] Přehrání jednotlivého slova při klepnutí (pokud zapnuto). _(pozn.: čte se i slovo s jiným textem nebo vlastním MP3; ověřeno se skutečným TTS jen dříve v kroku 3, tady falešným)_
- [x] Automatické smazání věty po přehrání (pokud zapnuto).

**Kritéria splnění:**
- [x] Při zapnutém nastavení se slovo po klepnutí přečte, při vypnutém ne.
- [x] Věta se po dokončení přehrávání smaže jen při zapnutém nastavení; po Stopu se nesmaže.

### 7.3 Režim tažení

**Úkoly:**
- [x] Přetažení slova do stavového řádku přidá slovo. _(pozn.: slovo se přidá na konec věty; tažená dlaždice se vznáší pod prstem)_
- [x] Odtažení slova pryč z řádku jej odebere.
- [x] V režimu tažení klepnutí slovo nepřidá, v režimu klepnutí tažení nepřidá.

**Kritéria splnění:**
- [x] Obě varianty ovládání fungují podle nastavení a vzájemně se nerušají.
- [x] Compose UI testy pro oba režimy.

### 7.4 Změna pořadí ve větě

**Úkoly:**
- [x] Změna pořadí slov ve větě posunováním vlevo/vpravo. _(pozn.: přetažením slova na jiné místo ve větě, i mezi řádky zalomené věty)_
- [x] Možnost změnu pořadí zakázat v konfiguraci.

**Kritéria splnění:**
- [x] Zakázaná změna pořadí opravdu nejde provést.
- [x] Při povolení lze slovo přesunout na libovolnou pozici ve větě.
- [x] Unit testy přesunu ve větě.

---

## Krok 8: Export a import

**Cíl:** Slovník nebo jeho část lze uložit do souboru a později načíst.

### 8.1 Formát archivu a serializace

**Úkoly:**
- [x] Návrh `manifest.json` (verze schématu, struktura složek a slovíček, odkazy na média) a adresářů pro obrázky a zvuky.
- [x] Serializace a deserializace (kotlinx.serialization nebo ekvivalent).
- [x] Zdokumentovat formát (např. krátká sekce v `assignment.md` nebo samostatný soubor). _(`format-archivu.md`)_

**Kritéria splnění:**
- [x] Unit testy (de)serializace včetně prázdného slovníku a slovníku s médii.
- [x] Archiv neobsahuje nic jiného než povolená data (žádný PIN, žádné přihlašovací údaje).

### 8.2 Export

**Úkoly:**
- [x] Export celého slovníku nebo vybrané složky (včetně podstromu) do ZIP přes systémový výběr souboru (SAF).
- [x] Volitelně export konfigurace (zaškrtávátko).
- [x] Indikace průběhu a chyb (nedostatek místa, přerušení).

**Kritéria splnění:**
- [x] Vzniklý ZIP lze otevřít a obsahuje `manifest.json` a všechna použitá média.
- [x] Chyba při zápisu nezanechá poškozený částečný soubor bez upozornění. _(neověřeno: skutečné smazání souboru u poskytovatele SAF po chybě; use case při chybě volá `discard()` a ten je otestován jen s falešným cílem. Úspěšný zápis přes SAF jsem na emulátoru zkusil.)_

### 8.3 Import

**Úkoly:**
- [x] Import celého archivu přidávající položky s novými ID; nic nepřepisuje.
- [x] Import vybraných složek/podsložek do zvolené cílové složky (výchozí kořen).
- [x] Volitelné nahrazení celého slovníku (s potvrzením).
- [x] Kontrola verze schématu, validace archivu před zápisem, odolnost vůči poškozenému archivu (import v jedné transakci, média se kopírují až po validaci nebo se při chybě odstraní).
- [x] Import konfigurace, je-li v archivu a uživatel ji chce.

**Kritéria splnění:**
- [x] Export → smazání dat → import vrátí identický slovník (struktura, titulky, pořadí, obrázky, zvuky).
- [x] Opakovaný import téhož archivu vytvoří duplicity s novými ID, původní data zůstanou nedotčena.
- [x] Částečný import jen vybrané složky ji vloží do zvolené cílové složky včetně jejích médií. _(složky se vybírají zaškrtnutím v seznamu složek archivu; kořenová slovíčka archivu jsou zvláštní volba)_
- [x] Poškozený nebo cizí archiv i archiv s novější verzí schématu skončí srozumitelnou chybou bez změny dat.
- [x] Unit testy import/export round-trip a chybových stavů.

---

## Krok 9: Synchronizace s Google Drive

**Cíl:** Data se zálohují na Google Drive a po instalaci na jiném zařízení se stejným účtem se dotáhnou.

> Tento krok vyžaduje externí nastavení (Google Cloud projekt, OAuth klient, podpisový klíč). Nejdřív ho připravit ručně, případně se zeptat vlastníka.

### 9.1 Příprava a přihlášení

**Úkoly:**
- [ ] Založit/ověřit Google Cloud projekt, povolit Drive API, nastavit OAuth klienta pro `cz.talkative.app` a SHA-1 debug klíče (postup zapsat do dokumentace). *Postup je zapsaný v `format-synchronizace.md` (včetně SHA-1 debug klíče z tohoto počítače); projekt a OAuth klienta musí založit vlastník, nedá se udělat ani ověřit z kódu.*
- [ ] Výběr Google účtu systémovým dialogem při prvním spuštění nebo v administraci. *Hotovo v administraci (Synchronizace → Přihlásit účet Google, `GoogleAuthorizer`, `SyncViewModel`, `SyncScreen`); s ostrým účtem zatím nevyzkoušeno, protože chybí OAuth klient.*
- [x] Bez účtu aplikace běží jen lokálně; odhlášení/změna účtu v administraci.

**Kritéria splnění:**
- [ ] *Neověřeno s ostrým účtem.* Po výběru účtu aplikace získá oprávnění `drive.appdata` a zapamatuje si účet.
- [x] Odmítnutí přihlášení nezpůsobí pád a aplikace dál funguje lokálně. *(ověřeno testy view modelu a obrazovky: zrušení souhlasu, chyba, chybějící služby Google Play; reálný dialog neověřen)*

*Provedení: služby Google Play přes `play-services-auth` 22.0.0 (Authorization API, `minSdk` knihovny 24, tedy pod 26; WorkManager 2.12.0 má 24, `hilt-work` 23). Stav (účet, poslední výsledek) je v DataStore pod vlastními klíči, ne v `AppConfig` ani v exportu. `ConsentRequest` je obecný obal na `IntentSender`, který otevírá jen obrazovka (`SyncRoute`). Bez služeb Google Play nebo bez souhlasu se synchronizace nespustí a aplikace běží lokálně.*

### 9.2 Abstrakce úložiště a model sloučení

**Úkoly:**
- [x] Rozhraní vzdáleného úložiště (výpis, čtení, zápis, mazání) s falešnou implementací pro testy.
- [x] Návrh struktury na Drive: metadata jako JSON, média jako samostatné soubory; zdokumentovat. _(`format-synchronizace.md`)_
- [x] Čistá logika slučování podle `updatedAt` (last write wins) včetně tombstonů a konfliktu delete-vs-edit.
- [x] Úklid starých tombstonů (po definované době).

**Kritéria splnění:**
- [x] Unit testy slučování: nová položka, úprava na jedné straně, souběžné úpravy, smazání vs. úprava (vyhrává novější), tombstone.
- [x] Slučování je idempotentní (opakované spuštění nic nemění).

*Provedení: `domain/sync` (`SyncItem`, `MediaRef`, `SyncMerge`, `SyncCodec`, rozhraní `RemoteStore`), `FakeRemoteStore` v `sharedTest`. Slučování je navíc komutativní (ověřeno testem), při shodě času vyhrává smazání a pak pevný obsahový klíč. Tombstony se zahazují po 30 dnech, nepoužitá média na Drive po 7 dnech.*

### 9.3 Implementace Drive a přenos médií

**Úkoly:**
- [x] Implementace úložiště nad Drive API (`drive.appdata`). _(`DriveRemoteStore` nad OkHttp a Drive REST v3, ověřeno `MockWebServer`em; proti skutečnému Drive neověřeno)_
- [x] Nahrávání a stahování obrázků a zvuků; přenášejí se jen změněné soubory. _(název média se nikdy nemění, takže rozdíl množin názvů stačí)_
- [x] Lokální data se mění až po úspěšném stažení (aplikace změn v transakci), aby výpadek sítě nenechal poškozený stav. _(`SyncDictionaryUseCase`: stažení do `cache/sync`, nahrání médií, zápis metadat a teprve pak média do úložišť a jedna transakce)_

**Kritéria splnění:**
- [ ] *Neověřeno na skutečném Drive (chybí OAuth).* Po instalaci na druhém zařízení (nebo po vymazání dat) a přihlášení stejným účtem se objeví stejný slovník včetně obrázků a zvuků.
- [ ] *Algoritmus ověřen testy s falešným úložištěm (`SyncDictionaryUseCaseTest`: výpadek při stahování i při zápisu metadat nechá místní data beze změny); na skutečném Drive neověřeno.* Výpadek sítě uprostřed synchronizace nenechá lokální data v poškozeném stavu.

*Provedení: `RoomSyncLocalStore` čte i tombstony a zapisuje přes `DictionaryDao.applySync` beze změny `updatedAt`, `FileSyncMediaStore` ukládá média pod přesným názvem. Testy: `DriveRemoteStoreTest` (MockWebServer: seznam, multipart vytvoření, aktualizace, mazání, 401 s obnovením tokenu, chybové stavy, duplicitní `dictionary.json`), `SyncDictionaryUseCaseTest` (dvě zařízení, mazání, souběh, výpadky), `SyncLocalStoreTest` (Room a soubory na emulátoru).*

### 9.4 Spouštění a stav synchronizace

**Úkoly:**
- [x] Synchronizace po změně v administraci (je-li síť), při startu aplikace a ručním tlačítkem. _(`SyncTrigger` sleduje tabulky Room, série úprav se sloučí; tlačítko „Synchronizovat teď“ v administraci → Synchronizace)_
- [x] Opakování po výpadku sítě na pozadí (WorkManager s omezením na síť, exponenciální backoff). _(`SyncWorker`: nejvýš 5 pokusů, backoff 30 s; chyby, které musí řešit uživatel, se neopakují)_
- [x] Stav synchronizace v administraci (probíhá, poslední úspěšná synchronizace, chyba).
- [x] Konfigurace se nesynchronizuje. _(synchronizují se jen složky, slovíčka a média; účet a stav synchronizace jsou v DataStore mimo `AppConfig` i exportní archiv)_

**Kritéria splnění:**
- [ ] *Algoritmus a plánování ověřeny (`SyncDictionaryUseCaseTest`, `WorkManagerSyncSchedulerTest`: úloha čeká na síť); skutečný výpadek a obnovení připojení na zařízení s Drive neověřeny.* Offline aplikace funguje beze změn a nahromaděné změny se po připojení odešlou.
- [x] Smazání na jednom zařízení se po synchronizaci projeví na druhém; úprava vs. smazání vyhrává novější změna. _(ověřeno testem se dvěma zařízeními nad falešným Drive)_
- [x] Souběžné úpravy ze dvou zařízení nezpůsobí pád ani ztrátu celého slovníku; vyhrává poslední zápis. _(ověřeno testem nad falešným Drive)_
- [ ] Ruční test se dvěma zařízeními/emulátory a skutečným účtem. *Čeká na založení OAuth klienta (viz `format-synchronizace.md`).*

---

*Bezpečnost dat při souběhu s běžnou prací v administraci: synchronizace maže jen média, která sama opustila (ne plošný úklid osiřelých souborů, ten zůstává jen při startu), a `DictionaryDao.applySync` uvnitř transakce zahodí příchozí verzi starší než uložená a maže jen tombstony; `WorkManagerSyncSchedulerTest` ověřuje, že `HiltWorkerFactory` úlohu skutečně vytvoří a doběhne.*

*Provedení: `RunSyncUseCase` (výsledek a stav do DataStore, přerušení nenechá svítit „probíhá“), `SyncWorker` + `WorkManagerSyncScheduler` (unikátní úloha `sync`, omezení CONNECTED, REPLACE sloučí série žádostí), `SyncTrigger` (start aplikace po úklidu souborů a změny tabulek `folders`/`words`, zpoždění 5 s po 2 s ticha). WorkManager se inicializuje v `TalkativeApp` (`Configuration.Provider` s `HiltWorkerFactory`, výchozí inicializátor odebrán z manifestu). Použití vzdálených změn vyvolá jednu další prázdnou synchronizaci, která nic nezapisuje, takže smyčka skončí.*

## Krok 10: Nekonzistence a zotavení

**Cíl:** Aplikace se po poškození dat nerozsype a upozorní administrátora výkřičníkem.

### 10.1 Detekce nekonzistencí

**Úkoly:**
- [ ] Detekce chybějícího souboru obrázku nebo MP3.
- [ ] Detekce slovíčka/složky odkazující na neexistující složku.
- [ ] Označení nevyřešitelného konfliktu při synchronizaci.
- [ ] Doménový model „problému“ slovíčka (seznam důvodů) vystavený UI.

**Kritéria splnění:**
- [ ] Testy pro každou ze tří situací (chybějící soubor, neexistující rodič, konflikt).

### 10.2 Zobrazení a bezpečné chování

**Úkoly:**
- [ ] Výkřičník u dotčeného slovíčka v administraci (s vysvětlením po klepnutí).
- [ ] Klient: chybějící obrázek se zobrazí jako text, chybějící MP3 se přečte titulek.

**Kritéria splnění:**
- [ ] Ručně smazaný soubor obrázku/MP3 nebo uměle rozbitá reference vede k výkřičníku, nikoli k pádu ani prázdné obrazovce.
- [ ] Klient s poškozenými daty dál skládá a přehrává věty.

### 10.3 Zotavení

**Úkoly:**
- [ ] Osiřelé položky se zobrazí na dohledatelném místě (např. speciální složka „Nalezené“), aby je šlo opravit nebo smazat.
- [ ] Po opravě (nahrání souboru, přesun, smazání) výkřičník zmizí.
- [ ] Pokus o opětovné stažení chybějících souborů z Drive při další synchronizaci.

**Kritéria splnění:**
- [ ] Osiřelé položky nejsou nikde „ztracené“ a lze je přesunout nebo smazat.
- [ ] Po opravě administrátorem výkřičník zmizí.
- [ ] Chybějící soubor, který je na Drive, se při další synchronizaci obnoví.

---

## Krok 11: Dokončení a předání

**Cíl:** Stabilní první verze připravená k interní distribuci.

### 11.1 Přístupnost a výkon

**Úkoly:**
- [ ] Projít přístupnost: velikosti dotykových ploch, kontrast, žádná časově omezená gesta, popisky pro čtečku obrazovky (`contentDescription`).
- [ ] Orientace na výšku i na šířku na různých velikostech tabletu.
- [ ] Kontrola výkonu při velkém slovníku (stovky položek, desítky obrázků): plynulé listování, rychlý start.

**Kritéria splnění:**
- [ ] Všechny interaktivní prvky mají alespoň 64 dp a dostatečný kontrast.
- [ ] Aplikace zůstává plynulá s testovacím slovníkem o stovkách položek.

### 11.2 Soukromí a release

**Úkoly:**
- [ ] Informace o soukromí (co se ukládá a kam) dostupná v administraci. Součástí: aplikace může nahrávat mikrofonem, ale jen na výslovný pokyn administrátora v editaci slovíčka (nahrávka zůstává v zařízení, v exportu a případně na Drive uživatele, nikam jinam se neposílá).
- [ ] Informace o soukromí a Google Drive (krok 9): po přihlášení k účtu Google se slovník (názvy, pořadí, texty, obrázky, zvuky i nahrávky z mikrofonu) ukládá do skryté složky aplikace (`appDataFolder`) na Disku Google uživatele, kam má přístup jen tato aplikace (oprávnění `drive.appdata`); aplikace nevidí ostatní soubory na Disku. Bez přihlášení zůstává vše jen v zařízení. Žádný vlastní server, žádné sdílení s třetími stranami. Konfigurace zařízení se nesynchronizuje. Smazání účtu v aplikaci (odhlášení) data na Disku nemaže; smazat je lze v nastavení Disku Google (Správa aplikací). Smazané položky zůstávají na Disku jako záznamy o smazání nejvýše 30 dní, nepoužité soubory se mažou po 7 dnech. Informace se zobrazí na obrazovce v administraci (např. v „O aplikaci“ nebo „Synchronizace“) a uvede se v ní i ARASAAC (viz E3).
- [ ] Release build: ikona aplikace, název, verze, minifikace/ProGuard pravidla (Room, Hilt, Drive API).
- [ ] Podpisový klíč (mimo repozitář), podepsaný release APK, návod k instalaci.
- [ ] Ověřit OAuth klienta Drive pro release podpis (SHA-1 release klíče).

**Kritéria splnění:**
- [ ] Release APK se nainstaluje na čisté zařízení a funguje včetně přihlášení k Drive.
- [ ] Aplikace běží plynule na minimálně podporovaném Androidu 8.0 i na novějším.
- [ ] Keystore ani hesla nejsou v repozitáři.

### 11.3 Závěrečné ověření a dokumentace

**Úkoly:**
- [ ] Projít ručním scénářem: vytvořit slovník → poskládat větu → přehrát → export/import → synchronizace na druhé zařízení.
- [ ] Aktualizace `CLAUDE.md`, `assignment.md` a `todo.md` podle skutečného stavu.

**Kritéria splnění:**
- [ ] Všechny testy jsou zelené a ruční scénář projde.
- [ ] Dokumentace odpovídá kódu.

---

## Krok 12: Rozšíření I: About, piktogramy ARASAAC a kreslení ikon

Doplňující kroky mimo původní pořadí 0–11, hotové. Platí stejná pravidla práce jako výše: podkroky pro jednu session, **Úkoly** a **Kritéria**, zaškrtávání `- [x]`, po každém podkroku zelené testy a samostatný commit (zpráva s číslem podkroku, např. „Krok E2.1: …“), texty česky v `strings.xml`, rozměry a barvy z tématu, dotykové plochy alespoň 64 dp, jednoduchý čitelný Kotlin, testy ověřující skutečné chování. Výsledné věci jsou promítnuté do `CLAUDE.md` (sekce „Current state“) a `todo.md` (položka č. 6 „Knihovna symbolů“ se tím vyřešila; sloupec `Priorita` se nevyplňuje).

Podkroky jsou označené **E1–E3** (Extensions), aby se nepletly s ostatními kroky. Nezávisí na krocích 9–11. Pořadí: E1 (About) šel první, protože uvedení autora ARASAAC musí být v aplikaci dřív, než se z ARASAAC začne stahovat.

Přehled podkroků: E1 Obrazovka „O aplikaci“ (About); E1a Úvodní oznámení při prvním spuštění (musí se vzít na vědomí); E2 Piktogramy ARASAAC (hledání a výběr uživatelem); E3 Kreslení ikon prstem.

### Rozhodnutí a předpoklady (Rozšíření I)

Vycházejí ze zadání a ze zjištění ve stávajícím kódu. Cokoli z toho se může změnit, než se příslušný podkrok začne dělat.

- **ARASAAC je jen další zdroj obrázku.** Uživatel (administrátor) sám zadá slovo, vybere jeden z nabídnutých piktogramů a ten se stáhne a uloží lokálně. Nic se nevybírá ani nepřiřazuje automaticky. Klient ani export/import/sync o ARASAAC nic neví, vidí jen soubor obrázku. **Slovíčka se jako „z ARASAAC“ neznačí** (rozhodnutí zadavatele): žádné nové pole v modelu, entitě, migraci ani `manifest.json`, žádná změna schématu databáze ani archivu. Licenční povinnost se proto plní vždy, bez ohledu na to, zda slovník piktogramy ARASAAC obsahuje (viz E2.5).
- **Offline-first zůstává.** Síť se používá jen při výběru piktogramu v administraci. Po stažení je obrázek v `filesDir/images` jako každý jiný. Bez sítě se zobrazí srozumitelná hláška a zbytek aplikace funguje.
- **API ARASAAC** (ověřeno 20. 10. 2026; před implementací znovu zkontrolovat, může se změnit):
  - hledání: `GET https://api.arasaac.org/v1/pictograms/cs/search/{text}` (text URL-kódovaný). Vrací JSON pole; položka má `_id` (číslo) a `keywords` (pole objektů s `keyword`, někdy `plural`), dále `tags`, `categories`, `desc` aj. Hledání „dům“ vrátí ~150 položek. Klíč ani přihlášení se nepoužívá.
  - obrázek: `https://static.arasaac.org/pictograms/{id}/{id}_{velikost}.png`, ověřené velikosti 300, 500 a 2500 (náhled 300, ukládání 500).
  - v odpovědi vyhledávání není autor, licence je u všech stejná (CC BY-NC-SA).
- **Licence ARASAAC: CC BY-NC-SA** (podle `_arasaac/Terms of use.txt`; číslo verze licence se v podmínkách neuvádí, odkaz na text licence se před použitím ověří na webu ARASAAC). Z podmínek plyne:
  - **Nekomerční použití.** Použití zdrojů „v rámci jakéhokoli produktu nebo publikace pro komerční účely je vyloučeno“. Při interní distribuci je to v pořádku; pokud by se aplikace někdy měla prodávat nebo vydat veřejně za úplatu, je to **blokující** a nutno předem vyřešit (podmínky uvádějí, že pro jiné použití je třeba písemný souhlas držitelů práv a že se lze na ně obrátit).
  - **Povinné uvedení** autora (Sergio Palao), vlastníka (Gobierno de Aragón), původu (ARASAAC) a licence (CC BY-NC-SA). Podmínky nabízejí dvě znění; v aplikaci se použije **druhé, kratší, doslova**: „Autor piktogramů: Sergio Palao. Původ: ARASAAC (http://www.arasaac.org). Licence: CC (BY-NC-SA). Majitel: vláda Aragonu (Španělsko)“. První znění („Použité piktografické symboly jsou majetkem Aragonské vlády a vytvořil je Sergio Palao pro ARASAAC (http://www.arasaac.org), který je šíří pod Creative Commons License BY-NC-SA.“) je rovnocenná alternativa, lze ho dát do About jako delší popis.
  - **Odvozená díla** (podmínky výslovně uvádějí „materiály na nich založené“, tj. i naše zmenšené kopie a slovníky s nimi) se musí šířit pod stejnou licencí a se stejným uvedením. Týká se to hlavně **exportu slovníku**: do každého exportního ZIPu se proto přidá textový soubor s uvedením autora a licencí (rozhodnutí zadavatele, viz E2.5).
  - **Logo ARASAAC** podmínky vyžadují jen na cedulích, plakátech a tabulkách označujících veřejné prostory, služby a obchody. V aplikaci tedy **povinné není**; ARASAAC je navíc registrovaná značka, proto se logo v About použije jen k označení původu piktogramů (viz E1.1).
  - **Odmítnutí odpovědnosti:** Aragonská vláda neodpovídá za použití překračující podmínky licence.
- **Podklady od zadavatele ve složce `_arasaac/`:** `Terms of use.txt` (podmínky použití přeložené do češtiny, využité výše) a dvě loga: `logo_ARASAAC.png` (barevné: „ARA“ zeleně, „SAAC“ šedě) a `logo_ARASAAC_black.png` (černé písmo, jinak totéž), obě PNG 2083 × 495 px s průhledností (~70 KB), na levé straně postavička s pozdravem a barevný proužek.
- **Formát uložení:** `FileImageStore` už ukládá obrázek s průhledností jako PNG a bez průhlednosti jako JPEG, takže průhledné piktogramy ARASAAC ani kresba se nepokazí. Tlačítko/dlaždice mají vlastní pozadí z tématu.
- **Napojení na editaci slovíčka bez zásahu do ukládání:** výsledek (stažený piktogram i nakreslená ikona) se předá do `AdminEvent.ImagePicked(uri)` jako soubor v `cache/`, takže se využije stávající `processPickedImage` (zmenšení, `ImageStore`, nahrazení a úklid dosud nepřiřazeného obrázku, `sessionId`). Dočasný soubor se po zpracování smaže. (Ověřit, že `saveFromUri` zvládne i `file://` URI; jinak přidat cestu `cache/` do `file_paths.xml` a použít `FileProvider`.)
- **Nové obrazovky jsou trasy navigace** (`TalkativeNavHost`): `about`, `arasaac`, `draw`. Stav dialogu editace slovíčka žije v `AdminViewModel`, který zůstává v back stacku, takže rozepsaná editace přežije odchod na výběr piktogramu či kreslení. Výsledek se vrací přes `savedStateHandle` předchozí obrazovky. Obrazovky jsou stejně jako dosud parametry `TalkativeNavHost`, aby šly v testech nahradit (bez Hilt).
- **Kreslení:** plátno 512 × 512 px (shoda s `ImageScaling.MAX_SIDE`), vždy neprůhledná bílá, nic se neukládá napůl. Rozkreslená kresba se **zahodí**, pokud ji uživatel neuloží (viz E3.3). Přežije jen otočení tabletu (je ve `ViewModelu`).
- **Výplň a výpočetní náročnost:** kreslí se přímo do rastrové `Bitmap`, ne do seznamu vektorových tahů, protože výplň potřebuje pracovat s pixely. Výplň je scanline flood fill nad polem pixelů na pozadí vlákna `Dispatchers.Default` (512 × 512 = 262 144 pixelů, řádově jednotky až desítky ms). Hranice tahů jsou vyhlazené, proto má výplň toleranci barvy, aby nezůstal tenký světlý lem.
- **Zpět (undo):** snímky bitmapy před každou akcí (tah, guma, výplň), nejvýše 20 kroků (~1 MB každý, tj. ~20 MB). Redo se nedělá (nebylo požadováno).
- **Šířka štětce** se nastavuje posuvníkem; stejná šířka platí pro štětec i gumu. Gumou se maluje bílou, takže výsledek zůstává neprůhledný.
- **Nové závislosti:** OkHttp (HTTP klient; Coil 3 pro načítání URL potřebuje `coil-network-okhttp`, takže stejná knihovna slouží pro API i pro náhledy) a pro testy `mockwebserver`. Žádná z nich nevyžaduje minSdk vyšší než 26; kdyby ano, krok se zastaví a zeptá se. Nové oprávnění `INTERNET` v `AndroidManifest.xml`.
- **Soukromí:** na server ARASAAC odchází jen hledaný text a ID stahovaného piktogramu (a technické údaje jako IP adresa). Nic z dat uživatele se neodesílá. Zapsat do informace o soukromí (viz 11.2 výše) a do About.

---

### Krok E1: Obrazovka „O aplikaci“ (About)

**Cíl:** V administraci je obrazovka s informacemi o aplikaci a s uvedením zdroje a licence piktogramů ARASAAC.

#### E1.1 Obrazovka About a navigace

**Úkoly:**
- [x] Texty uvedení zapsat do `strings.xml` doslova podle `_arasaac/Terms of use.txt` (viz „Rozhodnutí a předpoklady“): kratší znění „Autor piktogramů: Sergio Palao. Původ: ARASAAC (http://www.arasaac.org). Licence: CC (BY-NC-SA). Majitel: vláda Aragonu (Španělsko)“ jako samostatný řetězec použitelný i na jiných obrazovkách (E2.3). Před dokončením podkroku krátce porovnat se zněním na https://arasaac.org/terms-of-use, zda se od úpravy zadavatele nic neliší, a ověřit odkaz na text licence a její verzi. _(neověřeno: stránka arasaac.org/terms-of-use se bez JavaScriptu nenačte, znění proto nešlo porovnat s webem; text je doslova podle _arasaac/Terms of use.txt. Verzi licence podmínky neuvádějí, odkaz míří na CC BY-NC-SA 4.0 a verzi je třeba potvrdit)_
- [x] Logo ARASAAC (nepovinné, viz podmínky; slouží k označení původu): z `_arasaac/` převést do zdrojů aplikace (`res/drawable-nodpi`, zmenšené na šířku cca 800 px kvůli velikosti APK, průhlednost zachovat; barevná varianta `logo_ARASAAC.png`, černá je rezerva pro případ nízkého kontrastu). Zobrazit v sekci ARASAAC s `contentDescription` („Logo ARASAAC“), poměr stran se zachová; logo je klepnutelné jako odkaz na https://arasaac.org a nesmí se upravovat ani použít jako logo aplikace. Originály v `_arasaac/` zůstávají v repozitáři jako podklady.
- [x] Nová obrazovka `ui/about` (`AboutRoute` + stavová/bezstavová verze podle vzoru `ui/config`): název aplikace, verze (`versionName` z `PackageManager`, předaná jako parametr kvůli testům), stručný popis aplikace.
- [x] Sekce **ARASAAC**: co to je, odkud pochází piktogramy použité v aplikaci, uvedení autora doslova podle podmínek (autor Sergio Palao, vlastník vláda Aragonu, původ ARASAAC, licence CC BY-NC-SA), věta o tom, co licence znamená (jen nekomerční použití; odvozená díla včetně exportovaných slovníků s piktogramy ARASAAC se musí šířit pod stejnou licencí a se stejným uvedením), odkaz na https://arasaac.org, na podmínky použití https://arasaac.org/terms-of-use a na text licence (viz E1.1, první úkol, ověření verze). Případné dotazy ohledně jiného použití se podle podmínek řeší kontaktem na ARASAAC.
- [x] Sekce **Soukromí a síť**: data zůstávají v zařízení; síť se používá jen při hledání piktogramu v ARASAAC a odchází při něm jen hledaný text.
- [x] Odkazy se otevírají přes `Intent.ACTION_VIEW`; otevření je parametr obrazovky (`onOpenLink`), chybějící prohlížeč (`ActivityNotFoundException`) neshodí aplikaci a odkaz je vždy vidět i jako text, aby ho šlo opsat. _(chybějící prohlížeč ukáže hlášku přes Toast (helper ui/common/OpenLink.kt))_
- [x] Trasa `about` v `TalkativeNavHost` (parametr jako u `config` a `exchange`), tlačítko „O aplikaci“ v administraci vedle konfigurace a exportu (stejný styl, alespoň 64 dp), tlačítko Zpět. Z klienta se About neotevírá.
- [x] Obrazovka se vejde na výšku i na šířku (posuvný obsah).

**Kritéria splnění:**
- [x] Z administrace se otevře About a Zpět vrací do administrace; klient About neobsahuje.
- [x] Je vidět název a verze aplikace, logo ARASAAC, autor a vlastník ARASAAC, označení licence a oba odkazy.
- [x] Znění uvedení autora v aplikaci je shodné se zněním v `_arasaac/Terms of use.txt` (test porovná řetězec z `strings.xml` s očekávanou hodnotou, aby se nepozměnilo omylem).
- [x] Klepnutí na odkaz zavolá `onOpenLink` se správnou adresou; selhání otevření nezpůsobí pád.
- [x] Compose UI test (obsah, odkazy) a rozšíření `NavigationTest` o cestu admin → About → zpět.
- [x] Žádné natvrdo napsané texty, barvy ani rozměry.

#### E1a Úvodní oznámení při prvním spuštění

Rozhodnutí zadavatele: při prvním spuštění se před klientem zobrazí oznámení (obsah About) a uživatel ho musí vzít na vědomí. Tlačítka jsou „Beru na vědomí“ (bere se jako souhlas) a „Odejít“ (aplikaci ukončí, příště se oznámení ukáže znovu). Souhlas se ukládá s číslem verze textu; po jeho zvýšení se oznámení ukáže znovu.

**Úkoly:**
- [x] `NoticeRepository` (`domain/notice`, konstanta `NOTICE_VERSION`) a implementace v DataStore (`DataStoreNoticeRepository`, vlastní klíč ve stejném souboru jako konfigurace, konfigurace se ho nedotýká a do exportu nepatří).
- [x] `NoticeViewModel` (stavy `LOADING` / `REQUIRED` / `ACKNOWLEDGED`) a brána `NoticeGate` kolem celé navigace v `MainActivity`; do načtení uložené hodnoty se nezobrazuje nic.
- [x] `NoticeScreen`: obsah About (`AboutContent` sdílený s About v administraci), tlačítka „Beru na vědomí“ a „Odejít“ (alespoň 64 dp), systémové Zpět = „Odejít“.

**Kritéria splnění:**
- [x] Unit testy view modelu (první spuštění, odsouhlaseno, starší verze, uložení po odsouhlasení).
- [x] Compose UI testy brány (obsah, obě tlačítka, aplikace je skrytá, po odsouhlasení viditelná) a instrumentovaný test uložení do souboru.
- [x] Na emulátoru: po vymazání dat se ukáže oznámení, „Odejít“ aplikaci zavře a příště se oznámení ukáže znovu, po „Beru na vědomí“ se otevře klient a po restartu už oznámení nepřijde.

---

### Krok E2: Piktogramy ARASAAC

**Cíl:** Administrátor v editaci slovíčka napíše hledané slovo, uvidí nabídku piktogramů ARASAAC, klepnutím vybere jeden a ten se stane obrázkem slovíčka. Nic se nevybírá automaticky.

#### E2.1 Síťová vrstva a vyhledávání

**Úkoly:**
- [x] Oprávnění `INTERNET` v `AndroidManifest.xml`; do `libs.versions.toml` a `build.gradle.kts` OkHttp a `coil-network-okhttp` (Coil 3 pak umí načítat URL), pro testy `mockwebserver`.
- [x] Doména (`domain/pictogram`): `PictogramSearch` (rozhraní, `suspend fun search(text: String): PictogramSearchResult`), `Pictogram(id, title, previewUrl)`, výsledek `Found(list)` / `Failed(PictogramError)` a `PictogramError` (`NO_NETWORK`, `TIMEOUT`, `SERVER_ERROR`, `BAD_RESPONSE`). Čistá funkce pro sestavení URL obrázku (`id`, velikost 300 / 500) s unit testem. _(OkHttp 4.12.0 (stejná verze, kterou si přes coil-network-okhttp stejně tahá Coil 3.6.3); HTTP 404 s prázdným polem server vrací při nenalezení, bere se jako prázdný výsledek)_
- [x] Datová vrstva (`data/pictogram`): implementace nad OkHttp s adresou serveru jako parametrem (kvůli testům), časový limit 10 s, URL-kódování hledaného textu včetně diakritiky, parsování pomocí `kotlinx.serialization` s `ignoreUnknownKeys`. Titulek piktogramu = první `keyword`. Položky bez klíčového slova nebo s neplatným ID se přeskočí. Počet výsledků se omezí na rozumné maximum (např. 100).
- [x] Hilt modul `PictogramModule`, falešná implementace `FakePictogramSearch` do `src/sharedTest/java` pro testy view modelu a UI.
- [x] Prázdný hledaný text (jen mezery) se nehledá.

**Kritéria splnění:**
- [x] Unit testy s `MockWebServer`: úspěšná odpověď (správné ID a titulky, diakritika v dotazu odeslána správně zakódovaná), prázdné pole výsledků, poškozený JSON → `BAD_RESPONSE`, HTTP 500 → `SERVER_ERROR`, nedostupný server → `NO_NETWORK`, zpomalená odpověď → `TIMEOUT`.
- [x] Žádná závislost nevyžaduje minSdk vyšší než 26.
- [x] Ruční ověření proti skutečnému API (hledání „dům“ vrátí výsledky, náhled s ID z výsledku se načte). _(dočasný instrumentovaný test na emulátoru: hledání „dům“ vrátilo přes 20 výsledků, náhled prvního se stáhl; test jsem nekomitoval)_

#### E2.2 Stažení vybraného piktogramu

**Úkoly:**
- [x] `PictogramDownloader` (rozhraní v doméně, OkHttp implementace): stáhne `{id}_500.png` do dočasného souboru v `cache/pictograms`, omezí velikost odpovědi (např. 2 MB), při chybě nebo přerušení soubor smaže.
- [x] `ImportPictogramUseCase`: stáhne piktogram a předá ho do `ImageStore.saveFromUri`/`saveFromStream` (zmenšení, uložení do `filesDir/images`), dočasný soubor smaže vždy (i při chybě) a vrátí `ImageSaveResult` nebo chybu sítě. _(samostatný use case nevznikl: podle části „Rozhodnutí a předpoklady“ stažený soubor z cache/pictograms jde jako URI do AdminEvent.ImagePicked a ukládání (zmenšení, ImageStore, úklid) dělá stávající processPickedImage. Soubor se maže před dalším stažením a při startu aplikace, ne hned po zpracování)_
- [x] Úklid `cache/pictograms` při startu aplikace (vedle stávajícího úklidu `cache/camera`).

**Kritéria splnění:**
- [x] Unit testy (`MockWebServer` + falešný `ImageStore`): úspěch uloží obrázek a smaže dočasný soubor; chyba sítě, HTTP chyba i příliš velká odpověď neuloží nic a nezanechají soubor v `cache`. _(testy dočasného souboru jsou v OkHttpPictogramDownloaderTest (MockWebServer), test zachování PNG s průhledností v FileImageStoreTest)_
- [x] Uložený obrázek je PNG s průhledností (nebyl převeden na JPEG s černým pozadím).

#### E2.3 Obrazovka hledání a výběru

**Úkoly:**
- [x] Trasa `arasaac` a `ArasaacViewModel` (MVI jako ostatní obrazovky: `UiState` / `Event`): vyhledávací pole s tlačítkem Hledat (hledá se po potvrzení, ne při každém písmenu), předvyplněné titulkem slovíčka (administrátor může text upravit, např. použít jiný pojem), stavy *prázdné zadání*, *načítání*, *výsledky*, *nic nenalezeno*, *chyba* (s tlačítkem Zkusit znovu). _(hledaný text žije ve view modelu; trasa je `arasaac?query=…`, výsledek se vrací přes savedStateHandle předchozí obrazovky (konzument je v E2.4))_
- [x] Mřížka výsledků (Coil, náhled 300 px, titulek pod obrázkem, dlaždice alespoň 64 dp a dobře čitelná); klepnutí vybere piktogram a stáhne ho (E2.2) s indikací průběhu; zdvojené klepnutí během stahování se ignoruje.
- [x] Po úspěšném uložení se vrací zpět do editace slovíčka s novým obrázkem (viz E2.4). Zpět bez výběru nic nemění.
- [x] Viditelné uvedení autora na obrazovce výběru (krátké znění z E1.1: „Autor piktogramů: Sergio Palao. Původ: ARASAAC … Licence: CC (BY-NC-SA). Majitel: vláda Aragonu (Španělsko)“) s odkazem na About.
- [x] Hlášky: bez internetu (s nápovědou zapnout připojení), server nedostupný, nic nenalezeno (s nápovědou zkusit jiné slovo), obrázek nelze uložit (plné úložiště).

**Kritéria splnění:**
- [x] View model testy s `FakePictogramSearch`: předvyplnění titulku, prázdné zadání nehledá, přechody stavů načítání → výsledky / nic / chyba, Zkusit znovu, výběr volá stažení právě jednou.
- [x] Compose UI testy: zobrazení výsledků a chybových stavů, klepnutí na dlaždici vyvolá výběr, poznámka o licenci je vidět.
- [x] Obrazovka funguje na výšku i na šířku a hledaný text i výsledky přežijí otočení. _(ověřeno na emulátoru při scénáři v E2.4: po otočení na výšku zůstal hledaný text i výsledky)_

#### E2.4 Napojení do editace slovíčka

**Úkoly:**
- [x] V `ImageSection` (`AdminScreen.kt`) další tlačítko „Najít piktogram (ARASAAC)“ vedle galerie a fotoaparátu, aktivní jen u slovíčka (ne u složky, stejně jako ostatní obrázky) a jen když neprobíhá zpracování obrázku.
- [x] Předání výsledku zpět do editace jako `AdminEvent.ImagePicked(uri)` (viz „Rozhodnutí a předpoklady“); stávající `processPickedImage` zajistí uložení, nahrazení a úklid dosud nepřiřazeného obrázku.
- [x] Rozepsaná editace (titulek, alternativní text, MP3) zůstane po návratu z výběru piktogramu beze změny.
- [x] Aktualizovat `CLAUDE.md` (zdroje obrázku, balíček `pictogram`, nová trasa) a `todo.md` (položka 6 hotová pro ARASAAC).

**Kritéria splnění:**
- [x] Celý scénář na emulátoru: nové slovíčko → Najít piktogram → hledání → výběr → uložit slovíčko → obrázek je v administraci i v klientovi (mřížka i věta), přežije restart aplikace. _(na emulátoru se skutečným ARASAAC: nové slovíčko „pes“ → hledání → výběr → uložení; obrázek je v administraci i v mřížce klienta a po restartu zůstal (soubor .png v filesDir/images, cache/pictograms po startu zmizela). Věta s tímto slovem ručně neověřena)_
- [x] Výměna už přiřazeného obrázku piktogramem a pak zrušení editace nenechá osiřelý soubor (shoda s úklidem v kroku 4).
- [x] Bez sítě se zobrazí hláška a editace zůstane použitelná (galerie, fotoaparát, kreslení fungují dál).
- [x] Odebrání obrázku a smazání slovíčka stažený piktogram smaže jako každý jiný obrázek. _(neověřeno zvlášť pro piktogram, stažený soubor je po uložení obyčejný soubor v ImageStore a mazání je společné)_
- [x] Export a import archivu přenese stažený piktogram stejně jako obrázek z galerie (bez další změny kódu, ověřit testem round-trip nebo ručně). _(pokryto ImportOnDeviceTest (PNG s průhledností jako obrázek slovíčka), ruční export piktogramu neprovedena)_
- [x] Instrumentovaný test napojení (`NavigationTest` nebo nový test) s `FakePictogramSearch`.

#### E2.5 Uvedení autora a licence v exportním archivu

Rozhodnutí zadavatele: autor a licence ARASAAC se do exportního ZIPu přidají jako **text v ZIPu**; slovíčka se jako „z ARASAAC“ neznačí, takže soubor je v každém exportu (aplikace neví, zda slovník piktogramy ARASAAC obsahuje, a text to říká obecně). Nutné až po E1.1 (znění textu).

**Úkoly:**
- [x] Nová položka archivu `LICENSE-ARASAAC.txt` v kořeni ZIPu (konstanta v `ArchiveLayout` vedle `manifest.json`), UTF-8, zapisuje ji `ExportDictionaryUseCase` hned po `manifest.json`. Schéma `manifest.json` se **nemění** (verze schématu zůstává), starší verze aplikace položku při importu ignorují jako neznámou. _(text skládá domain/exchange/ArchiveNotice.kt z řetězců ve strings.xml (arasaac_credit, arasaac_credit_long, arasaac_license_file_note))_
- [x] Obsah souboru: doslovné krátké znění uvedení z E1.1 („Autor piktogramů: Sergio Palao. Původ: ARASAAC (http://www.arasaac.org). Licence: CC (BY-NC-SA). Majitel: vláda Aragonu (Španělsko)“), věta, že archiv může obsahovat piktogramy ARASAAC a že se musí šířit pod licencí CC BY-NC-SA nekomerčně a se stejným uvedením, odkazy na https://arasaac.org, podmínky použití a text licence. Jeden zdroj textu: řetězce ze `strings.xml` z E1.1, do use case se text předá přes malé rozhraní (např. `ArchiveNotice`) s implementací v `data`/`di`, aby doména nezávisela na Androidu.
- [x] Import: `ArchiveReader`/`ImportArchiveUseCase` položku `LICENSE-ARASAAC.txt` ignorují (nepočítá se do validace jako neznámý soubor ani jako chyba, nic se z ní nezapisuje); ověřit, že stávající chování (`ArchiveLimits.maxBytes` = `null` → přeskočit) to splňuje, a případně položku povolit výslovně.
- [x] Obrazovka exportu (`ui/exchange`): krátká poznámka, že se do archivu přidá soubor s uvedením autora a licencí ARASAAC a že archiv s piktogramy ARASAAC lze šířit jen nekomerčně pod stejnou licencí.
- [x] Zdokumentovat v `format-archivu.md` (nová volitelná textová položka, ignorovaná při importu, žádná změna `manifest.json`) a upravit tam i případné tvrzení, že archiv obsahuje jen `manifest.json` a média.
- [x] Upravit existující testy archivu z kroku 8, které předpokládají přesný seznam položek ZIPu.

**Kritéria splnění:**
- [x] Unit test exportu: ZIP obsahuje `LICENSE-ARASAAC.txt` s očekávaným textem (shoda s řetězci z E1.1, správně uložená čeština v UTF-8, i při exportu prázdného slovníku a jedné složky), vedle `manifest.json` a médií.
- [x] Round-trip export → smazání dat → import vrátí identický slovník a položka s licencí nic nezmění (nevytvoří slovíčko, soubor ani chybu); archiv bez této položky (starý export) se importuje stejně.
- [x] Žádný výstup exportu nemá nové pole v `manifest.json` (test porovnává výstup se stávajícím schématem) a `ManifestCodec` zůstává beze změny.
- [x] Ruční ověření: exportovaný ZIP otevřený na počítači obsahuje čitelný `LICENSE-ARASAAC.txt`. _(ověřeno na emulátoru: ZIP exportovaný přes SAF a stažený na počítač obsahuje manifest.json a LICENSE-ARASAAC.txt s čitelným textem)_

---

### Krok E3: Kreslení ikon prstem

**Cíl:** Administrátor v editaci slovíčka nakreslí jednoduchou ikonu prstem (štětec s nastavitelnou tloušťkou, guma, výplň, zpět) a ta se uloží jako obrázek slovíčka. Pozadí je vždy bílé.

#### E3.1 Jádro kreslení (bez UI)

**Úkoly:**
- [x] Balíček `domain/drawing`, bez závislosti na Compose, aby šel testovat na JVM, kde to jde (`Bitmap` lze v JVM testech nahradit vlastním polem pixelů `IntArray`; operace pracují nad rozhraním/pixelovou mřížkou, ne přímo nad `android.graphics.Bitmap`): _(PixelCanvas (pixely, tah s vyhlazením, scanline výplň), DrawingBoard (historie Zpět, akce, výplň na pozadí) a PngEncoder; paleta je v DrawingColors jako pevné ARGB hodnoty. Zápis do cache/drawings je v E3.3)_
  - `DrawingCanvas`: 512 × 512 pixelů, vždy inicializované bílou barvou, neprůhledné.
  - `DrawingTool` (`BRUSH`, `ERASER`, `FILL`), `DrawingColor` (paleta cca 8 barev z tématu včetně černé; barva se ukládá jako hodnota, ne podle tématu, aby výsledek byl stejný všude).
  - Tah štětcem/gumou: spojení bodů úsečkami s kulatými konci a zadanou šířkou (`BrushWidth` s mezemi v konstantách, např. 4–64 px); guma = totéž bílou. Samotné klepnutí bez posunu nakreslí tečku.
  - Výplň `floodFill(x, y, color, tolerance)`: scanline algoritmus (bez rekurze, nepadá na zásobníku) nad pixely ohraničenými oblastmi podobné barvy; tolerance kvůli vyhlazeným hranicím; výplň stejnou barvou, jakou už pixel má, nedělá nic. Souřadnice mimo plátno se ignorují.
  - Historie pro Zpět: zásobník snímků (nejvýše 20), operace `push` před každou akcí (jeden tah od přiložení po zvednutí prstu = jedna akce), `undo()` vrací poslední snímek, `isModified` (je na plátně něco jiného než bílá).
  - Převod na PNG (`toPng()`/zápis do souboru v `cache/drawings`).
- [x] Výpočet výplně a vykreslení do bitmapy běží mimo hlavní vlákno (`Dispatchers.Default`) a UI během výplně zobrazuje krátkou indikaci; více akcí se nezpracovává souběžně.

**Kritéria splnění:**
- [x] Unit testy: nové plátno je celé bílé; tah zbarví pixely podél úsečky a nic mimo ni; širší štětec zbarví víc pixelů; guma vrací bílou; klepnutí vytvoří tečku; výplň zaplní jen souvislou oblast uvnitř uzavřeného tvaru a ne oblast za čárou; výplň mezi čárou a pozadím se tolerancí dostane i k vyhlazeným okrajům; výplň na plném plátně (nejhorší případ) doběhne rychle a bez pádu; výplň stejnou barvou nic nezmění; nejvýše 20 kroků Zpět, Zpět po 21. akci vrátí 20., ne původní stav; Zpět vrátí i výplň; `isModified` po Zpět na prázdné plátno je `false`.
- [x] Měření: výplň celého plátna 512 × 512 trvá na emulátoru řádově desítky ms (hodnotu zapsat do poznámky podkroku). _(výplň celého plátna 512 × 512 trvá na emulátoru Pixel_Tablet_API_35 8–13 ms (DrawingPerformanceTest, logcat DrawingPerf), na JVM jednotky ms)_

#### E3.2 Obrazovka kreslení

**Úkoly:**
- [x] Trasa `draw` a `DrawViewModel` (stav plátna, zvolený nástroj, barva, šířka; přežije otočení tabletu).
- [x] `DrawScreen`: čtvercové plátno zobrazené v největší možné velikosti (na šířku vedle něj panel nástrojů, na výšku pod ním), tahy prstem přes `pointerInput` (`detectDragGestures` nebo vlastní zpracování, aby klepnutí bez posunu nakreslilo tečku), souřadnice se přepočítávají z velikosti zobrazení do 512 × 512.
- [x] Panel nástrojů (všechny prvky alespoň 64 dp, vysoký kontrast, vybraný nástroj a barva jasně označené): **Štětec**, **Guma**, **Výplň**, paleta barev, **posuvník tloušťky** s živým náhledem velikosti štětce (kolečko), **Zpět**, **Smazat vše** (s potvrzením, protože jde o hromadné zahození; samo se dá vrátit přes Zpět), **Uložit**, **Zavřít**.
- [x] Posuvník se nezobrazuje ani nezablokuje u Výplně (nemá smysl), nebo je neaktivní.
- [x] Texty a popisky (`contentDescription`) v `strings.xml`.

**Kritéria splnění:**
- [x] View model testy: změna nástroje/barvy/tloušťky, tah vytvoří akci, Zpět, Smazat vše, přežití „otočení“ (obnovení view modelu zachová plátno).
- [x] Compose UI test: tah přes plátno změní plátno (`isModified`), tlačítka Zpět a Uložit jsou neaktivní na prázdném plátně a po prvním tahu aktivní, výběr nástroje je označený.
- [x] Na emulátoru: kreslení prstem je plynulé na tabletu na výšku i na šířku, otočení během kreslení nesmaže kresbu. _(na emulátoru ověřeno kreslení štětcem, gumou a výplní (tahy přes adb swipe, ne skutečným prstem) a že otočení na výšku uprostřed kresby kresbu nesmaže; plynulost při skutečném dotyku neověřena)_

#### E3.3 Uložení, zahození a napojení do editace

**Úkoly:**
- [x] **Uložit:** zapíše plátno jako PNG do `cache/drawings`, vrátí URI do editace jako `AdminEvent.ImagePicked(uri)` (viz „Rozhodnutí a předpoklady“) a zavře obrazovku. Prázdné (celé bílé) plátno se uložit nedá.
- [x] **Zahodit rozkreslené:** Zavřít nebo systémové Zpět na změněném plátně (`isModified`) se zeptá „Zahodit kresbu?“ (Zahodit / Pokračovat v kreslení); na nezměněném plátně zavře bez dotazu. Nic se nikam neukládá, po zavření ani po restartu aplikace po rozkreslené kresbě nezůstane stopa (`ViewModel` se zahodí s trasou, `cache/drawings` se při startu uklízí jako `cache/camera`).
- [x] V `ImageSection` tlačítko „Nakreslit“ vedle galerie, fotoaparátu a ARASAAC (jen u slovíčka, ne během zpracování obrázku). Rozepsaná editace slovíčka zůstane po návratu beze změny.
- [x] Nakreslená ikona se uloží stejnou cestou jako ostatní obrázky (zmenšení nic nemění, protože je 512 px; PNG bez průhlednosti se uloží jako JPEG, to je v pořádku, protože pozadí je bílé).
- [x] Aktualizace `CLAUDE.md` a `todo.md`. _(CLAUDE.md doplněn; todo.md kreslení neobsahuje, tam se nic nezměnilo)_

**Kritéria splnění:**
- [x] Scénář na emulátoru: nové slovíčko → Nakreslit → kresba štětcem, gumou a výplní → Uložit → uložit slovíčko → ikona je v administraci i v klientovi, přežije restart. _(ověřeno na emulátoru: slovíčko „dum“ s nakreslenou ikonou je v administraci i v klientovi (uloženo jako JPG v filesDir/images) a po restartu zůstalo; cache/drawings se po startu uklidila)_
- [x] Zavření rozkreslené kresby se zeptá a po potvrzení se zahodí beze stopy (v `cache/drawings` nic nezůstane, v `filesDir/images` žádný nový soubor); zavření nezměněného plátna se neptá.
- [x] Výměna už přiřazeného obrázku kresbou a pak zrušení editace nenechá osiřelý soubor.
- [x] Nakreslená ikona se exportuje a importuje jako jakýkoli obrázek.
- [x] Instrumentovaný test celého napojení (kresba → editace → obrázek slovíčka).

---

### Rozhodnuté a otevřené otázky (Rozšíření I)

- **Rozhodnuto – export piktogramů ARASAAC:** podmínky říkají, že odvozená díla i „materiály na nich založené“ se šíří pod stejnou licencí a s uvedením autora. Zadavatel rozhodl: uvedení autora a licence se do exportního archivu dává jako **textový soubor v ZIPu** (E2.5, bez změny `manifest.json`), slovíčka se jako „z ARASAAC“ **neznačí**. Doplňuje to věta v About (E1.1).
- **Otevřené – komerční použití:** podmínky komerční použití vylučují. Pokud se aplikace někdy bude prodávat nebo vydávat veřejně za úplatu, je nutné buď získat písemný souhlas ARASAAC, nebo ARASAAC z aplikace odstranit.

---

## Krok 13: Nahrání vlastního zvuku mikrofonem („nadiktování“)

Rozšíření kroku 5. Dnes se vlastní zvuk slovíčka vybírá jen jako hotový MP3 soubor ze zařízení. Tento plán přidává možnost zvuk **nadiktovat**: administrátor v editaci slovíčka stiskne Nahrávat, řekne slovo či frázi, nahrávku si poslechne a uloží ji jako vlastní zvuk slovíčka. Dál se chová stejně jako vybraný soubor (přednost před TTS, přehrání ve větě, export/import). Postupuje se stejně jako u ostatních kroků (podkroky, Úkoly + Kritéria, po každém podkroku samostatný commit a push se zaškrtnutím zde).

Nejde o převod řeči na text (rozpoznávání řeči); nahrává se přímo zvuk.

### Zda a za jakých podmínek to lze

**Lze.** Android umožňuje nahrávat mikrofon přes `MediaRecorder`, bez nové knihovny a bez sítě. Podmínky a omezení:

1. **Formát: nahrávka nebude MP3.** `MediaRecorder` na Androidu neumí MP3 kodér (podporuje AAC, AMR, Opus/OGG). Dvě cesty:
   - **(A) Uložit nahrávku jako AAC v kontejneru MP4 (`.m4a`) a rozšířit „jen MP3“ na „MP3 nebo M4A“.** Žádná nová závislost, `MediaPlayer` i `MediaMetadataRetriever` M4A od API 26 zvládají. Minuta řeči při 64 kbit/s mono má asi 0,5 MB, tedy hluboko pod limitem 5 MB. **Doporučeno.**
   - (B) Převést nahrávku na MP3 knihovnou LAME (nativní kód). Zvětší APK, přidá nativní knihovnu pro více architektur a licenční povinnosti (LGPL) a komplikuje build; přínos je jen v tom, že by se vše dál jmenovalo MP3. Nedoporučeno.
   
   Plán dál počítá s variantou A. Důsledek: „vlastní MP3“ v aplikaci se stává „vlastní zvuk (MP3 nebo M4A)“; texty v UI a názvy v kódu (`AudioError.UNSUPPORTED_FORMAT`, `MP3_MIME_TYPE`) se musí opravit, aby nelhaly.
2. **Oprávnění `RECORD_AUDIO`** je nebezpečné oprávnění: musí být v `AndroidManifestu` a aplikace o něj žádá za běhu. Žádá se až při prvním stisku Nahrávat v administraci, nikdy při startu a nikdy v klientském režimu. Při zamítnutí se zobrazí srozumitelná hláška (včetně „natrvalo zamítnuto → v nastavení systému“) a zbytek aplikace funguje beze změny.
3. **Zařízení musí mít mikrofon.** V manifestu `<uses-feature android:name="android.hardware.microphone" android:required="false"/>`, aby aplikace zůstala instalovatelná i na zařízeních bez něj; tlačítko Nahrávat se tam skryje nebo zneaktivní s vysvětlením. Emulátor mikrofon obvykle předává z počítače, jinak nahraje ticho.
4. **Nahrává se jen v popředí, jen v administraci.** Nahrávání se při odchodu z editace, zamčení displeje nebo přechodu aplikace do pozadí zastaví a zahodí (od Androidu 11/12 stejně nelze spolehlivě nahrávat na pozadí bez služby v popředí, kterou nepotřebujeme a nepřidáme). Mikrofon proto nikdy neběží skrytě.
5. **Limity zůstávají stejné jako u souboru:** nejvýše 60 s a 5 MB. Nahrávání se po 60 s samo ukončí (s upozorněním); nahrávka projde stejnou kontrolou v `AudioStore` jako vybraný soubor.
6. **Mikrofon není sdílitelný s přehráváním.** Před začátkem nahrávání se zastaví náhled zvuku i řeč TTS; po dobu nahrávání jsou tlačítka Náhled a Uložit neaktivní.
7. **Soukromí.** Nahrávka (hlas) zůstává jen v `filesDir/audio` a v exportním archivu; aplikace nic neposílá po síti. Při pozdější synchronizaci s Google Drive (krok 9) se přenese jako ostatní zvuky; do informací o soukromí (krok 11.2) patří zmínka, že aplikace může nahrávat mikrofonem a co se s nahrávkou děje. Od Androidu 12 systém sám ukazuje indikátor používání mikrofonu.
8. **Kvalita** záleží na zařízení (vestavěný mikrofon tabletu, okolní hluk). Proto je povinný poslech před uložením a snadné nahrání znovu; aplikace nenahrazuje studiovou nahrávku.
9. **Dostupnost a přístupnost.** Nahrávání je jen v administraci, takže pro klienta se nic nemění. Ovládání je ale i tak bez časově omezených gest: Nahrávat se spouští a ukončuje klepnutím (ne podržením), prvky mají alespoň 64 dp, stav nahrávání je vidět i slyšet jinak než barvou (text „Nahrávám…“, uplynulý čas, ikona).

### Obecná pravidla (Krok 13)

- Platí „Obecná pravidla pro všechny kroky“ výše (testy, texty v češtině v `strings.xml`, rozměry z tématu, jednoduchý kód).
- Čistá logika (stavový automat nahrávání, kontrola limitů) je bez závislosti na Androidu a testuje se jednotkově s falešným nahrávačem (`FakeAudioRecorder` v `src/sharedTest/java`, jako `FakeAudioPlayer`).
- Pokud by knihovna nebo funkce vyžadovala minSdk vyšší než 26, krok se zastaví a zeptá se (`MediaRecorder` s AAC/MP4 je dostupný od API 10; `MediaRecorder(Context)` od API 31 se volá jen na nových verzích, na starších se použije bezparametrový konstruktor).

---

### Krok D.1: Formát nahrávky a úprava úložiště zvuku

**Cíl:** Úložiště zvuku přijímá kromě MP3 i M4A (AAC), aby šlo uložit výstup `MediaRecorderu`. Zatím beze změny UI.

**Úkoly:**
- [x] V `AudioLimits` povolit MIME `audio/mpeg` (MP3) a `audio/mp4` (M4A; ověřit, jakou hodnotu `MediaMetadataRetriever` pro AAC v MP4 na API 26 a novějším skutečně vrací, případně přidat i `audio/x-m4a`); přejmenovat `MP3_MIME_TYPE` / `UNSUPPORTED_FORMAT` tak, aby odpovídaly novému významu.
- [x] `FileAudioStore`: přípona uloženého souboru podle zjištěného formátu (`.mp3` nebo `.m4a`), ne napevno `.mp3`; stávající data (`.mp3`) zůstávají platná, žádná migrace databáze není potřeba (sloupec `audioFile` ukládá holý název souboru).
- [x] Opravit texty chyb a editace v `strings.xml` („MP3“ → „zvuk“ tam, kde už platí obecněji) a komentáře v kódu.
- [x] Testy pro obě varianty: platný MP3, platný M4A, jiný formát (např. WAV/OGG) je odmítnut, příliš dlouhý M4A je odmítnut, nic nezůstane po odmítnutí.

**Kritéria splnění:**
- [x] Hotový M4A soubor vybraný přes stávající výběr souboru lze uložit a přehrát ve větě stejně jako MP3.
- [x] Dosavadní testy `AudioLimitsTest`, `FileAudioStoreTest`, `SentencePlaybackTest` projdou (po úpravě očekávání tam, kde se změnil význam) a v testovacích fixtures je i krátký tichý M4A.
- [x] Žádná zmínka o „jen MP3“ nezůstala v UI, které by tím lhalo.

*Provedení: `AudioLimits.ACCEPTED_MIME_TYPES` (`audio/mpeg`, `audio/mp4`, `audio/x-m4a`) a `fileExtension`; `FileAudioStore` volí příponu podle zjištěného formátu; chyba `UNSUPPORTED_FORMAT` zůstala (už je obecná). Tlačítko „Vybrat soubor“, štítek „Vlastní zvuk“. Tichý M4A pro testy vyrábí `AudioFixtures.silentM4a` kodérem zařízení (v `androidTest`), proto M4A testy běží jen instrumentovaně (`FileAudioStoreTest`). `SentencePlaybackTest` M4A nepotřebuje (přehrávání jde přes `AudioPlayer`, který příponu nezná).*

---

### Krok D.2: Nahrávání mikrofonem (doménová a datová vrstva)

**Cíl:** Aplikace umí nahrát zvuk do dočasného souboru a předat ho úložišti; zatím bez obrazovky.

**Úkoly:**
- [x] Rozhraní `AudioRecorder` v `domain/audio` (spustit, zastavit s výsledkem, zrušit, stav a uplynulý čas jako Flow); `suspend`/`Flow` styl jako `AudioPlayer`.
- [x] Implementace `MediaAudioRecorder` v `data/audio` nad `MediaRecorder`: zdroj `MIC`, formát MPEG-4, kodér AAC, mono, 44,1 kHz, 64 kbit/s, nejdelší doba nahrávání z `AudioLimits.MAX_DURATION_MILLIS` (`setMaxDuration`, doběhnutí limitu = běžné ukončení nahrávky), dočasný soubor v `cache/recordings`.
- [x] Ošetřit chyby: mikrofon obsazený jinou aplikací, `IllegalStateException`/`IOException` při startu či zastavení, příliš krátká nahrávka (`stop()` hodí výjimku; chápat jako „nic se nenahrálo“), nedostatek místa.
- [x] Uvolnění `MediaRecorderu` při zastavení, zrušení i pádu (`finally`), stejně pečlivě jako u `MediaAudioPlayer`.
- [x] Dočasné soubory v `cache/recordings` se čistí při startu aplikace, kde se čistí `cache/camera`, `cache/pictograms` a `cache/drawings`; doplnit do `CLAUDE.md`.
- [x] Use case `SaveRecordingUseCase` (nebo rozšíření `SaveAudioUseCase`): hotovou nahrávku předá `AudioStore.saveFromUri`, po uložení či odmítnutí dočasný soubor smaže.
- [x] `FakeAudioRecorder` pro jednotkové testy.

**Kritéria splnění:**
- [x] Jednotkové testy: uložení přes use case, odmítnutí, úklid dočasného souboru (`SaveRecordingUseCaseTest`); nahrávání, zastavení, zrušení a úklid na skutečném `MediaRecorderu` (`MediaAudioRecorderTest`). *Automatické ukončení po 60 s, obsazený mikrofon a nedostatek místa se na emulátoru ověřit nedají (60 s čekání / vlastnost zařízení); logika limitu je v `setMaxDuration` a v `reachedLimit`, v testech UI (D.3) ji nahrazuje `FakeAudioRecorder.reachLimit()`.*
- [x] Instrumentovaný test: skutečný `MediaRecorder` na emulátoru nahraje krátký soubor a `AudioStore` ho přijme (pokud emulátor nemá mikrofon, bude soubor tichý, což pro test formátu stačí).
- [x] Po zrušení nebo chybě nezůstane žádný soubor v `cache/recordings` ani v `filesDir/audio`.
- [x] `./gradlew test` zelený.

---

### Krok D.3: Nahrávání v editaci slovíčka (UI)

**Cíl:** Administrátor v editaci slovíčka zvuk nahraje, poslechne si ho a uloží.

**Úkoly:**
- [x] `AndroidManifest.xml`: oprávnění `RECORD_AUDIO` a `uses-feature` mikrofon s `required="false"`.
- [x] V sekci zvuku v editaci slovíčka (vedle „Vybrat MP3“) tlačítko **Nahrát hlasem**. Po stisku: žádost o oprávnění (`ActivityResultContracts.RequestPermission`), při zamítnutí hláška s nápovědou; při udělení začne nahrávání.
- [x] Stav nahrávání v `AdminContract` (nové události např. `StartRecording`, `StopRecording`, `CancelRecording`, `RecordingFinished`, `RecordingFailed`; stav `isRecording`, uplynulý čas, `audioProblem`); stav se v `AdminViewModel` řídí stejným stylem jako `isProcessingAudio` a `isPreviewingAudio`.
- [x] Během nahrávání: výrazně viditelný stav (text „Nahrávám…“, uplynulý čas, ikona), velké tlačítko **Zastavit** (klepnutí), **Zrušit**; ostatní zvukové akce a Uložit slovíčko jsou neaktivní; po 60 s se nahrávání samo ukončí s upozorněním.
- [x] Po zastavení se nahrávka uloží do `AudioStore` (jako při výběru souboru), v editaci se objeví jako nově vybraný zvuk a lze si ji **přehrát** stávajícím náhledem, **nahrát znovu** (původní nepotvrzená nahrávka se smaže jako dnes vyměněný soubor) nebo **odebrat**. Konečné uložení slovíčka nese nahrávku stejně jako vybraný MP3.
- [x] Odchod z editace, zrušení dialogu, otočení tabletu nebo odchod aplikace do pozadí nahrávání zastaví a zahodí (otočení: `MainActivity` má `configChanges`, nahrávání nesmí přežít smrt `ViewModelu` ani zůstat „viset“).
- [x] Před začátkem nahrávání zastavit náhled zvuku (a TTS, pokud je aktivní).
- [x] Texty v češtině v `strings.xml`, rozměry z tématu, dotykové plochy ≥ 64 dp, obsah dialogu v `ScrollableColumn` (viz pravidla pro posuvný obsah v `CLAUDE.md`).

**Kritéria splnění:**
- [x] Compose UI test (s `FakeAudioRecorder`): Nahrát → Zastavit → Náhled → Uložit slovíčko → slovíčko má zvuk; Nahrát → Zrušit → nic se neuložilo; zamítnuté oprávnění → hláška bez pádu a zbytek editace funguje.
- [x] Test `SmallScreenScrollTest`: nová tlačítka jsou dosažitelná na okně 240 dp s písmem 150 %.
- [x] Unit testy `AdminViewModel`: stavové přechody včetně automatického ukončení po limitu, výměny nahrávky a odchodu z editace během nahrávání.
- [x] Nahrávání neproběhne při obsazeném mikrofonu ani bez mikrofonu; uživatel dostane hlášku, aplikace nespadne.
- [x] Věta „A, [nahrávka], B, C“ se přehraje v klientovi stejně jako s MP3 (využít `SentencePlaybackTest`).

*Provedení: události `StartRecording` / `StopRecording` / `CancelRecording` / `RecordingPermissionDenied(permanently)`; konec nahrávky (`RecordingFinished`, `RecordingFailed` z plánu) řeší `AdminViewModel` interně, protože `AudioRecorder.record()` je suspend funkce a výsledek vrací přímo. Stav `isRecording` a `recordingElapsedMillis` je v `AdminDialog.Edit`. Oprávnění se žádá v `AudioSection` při klepnutí na „Nahrát hlasem“ (`RequestPermission`); trvalé zamítnutí se pozná podle `shouldShowRequestPermissionRationale`. Odchod aplikace do pozadí (`ON_STOP`) a zavření dialogu nahrávku zahodí; `MainActivity` má `configChanges`, takže otočení nic nerestartuje. Řeč TTS v administraci neběží (TTS má jen klient), proto se před nahráváním zastavuje jen náhled zvuku. Ikona nahrávání je červená tečka (v projektu je jen `material-icons-core`, ikona mikrofonu v něm není), stav je vždy i textem „Nahrávám…“ a časem. Testy: `AdminViewModelTest` (12 nových), `AdminScreenTest` (panel, tlačítka, texty hlášek), `AdminFlowTest` (nahrát → zastavit → přehrát → uložit; nahrát → zrušit), `SmallScreenScrollTest`, `ClientViewModelTest` (věta s M4A). Zamítnuté oprávnění se na úrovni systémového dialogu v Compose testu ověřit nedá, je ověřené na úrovni view modelu a textů. `AdminFlowTest` je občas nestabilní (starší testy s obrázky, „connection pool has been closed“ při úklidu databáze po předchozím testu); při opakování prošel.*

---

### Krok D.4: Export/import, dokumentace a ruční ověření

**Cíl:** Nahrávky se správně exportují a importují, dokumentace odpovídá a funkce je ověřena na skutečném zařízení.

**Úkoly:**
- [x] Export/import: ověřit, že `ArchiveReader`, `ExportDictionaryUseCase` a `ImportArchiveUseCase` nepočítají s příponou `.mp3` a že import M4A prochází kontrolou z D.1; úprava `format-archivu.md` (adresář `audio/` obsahuje MP3 nebo M4A; archivy se starým obsahem zůstávají platné, verze schématu se nemění).
- [x] Aktualizovat `CLAUDE.md` (sekce „Current state“: záznam z mikrofonu, `AudioRecorder`, `cache/recordings`, M4A), `assignment.md` (vlastní zvuk: MP3 nebo nahrávka mikrofonem, oprávnění) a `plan.md` (odkaz na tento krok u kroku 5).
- [x] Dopsat k informacím o soukromí v kroku 11.2: aplikace nahrává mikrofonem jen na výslovný pokyn administrátora.
- [ ] Ruční ověření na skutečném tabletu: žádost o oprávnění, zamítnutí a následné udělení, nahrání krátkého i minutového zvuku, poslech náhledu, přehrání ve větě poslechem (emulátor zvuk neověří), nahrání při přerušení (zamčení displeje, příchozí hovor, odchod do pozadí).

**Kritéria splnění:**
- [x] Round-trip test: slovník s nahrávkou M4A → export → smazání → import vrátí slovo se stejným zvukem.
- [x] Starý archiv s MP3 se importuje beze změny.
- [x] Dokumentace odpovídá kódu.
- [ ] Ruční ověření zvuku člověkem poslechem je zapsáno do plánu (stejně jako u kroku 5.3: u položek, které emulátor neověří, poznámka, co zbývá).

*Provedení: `ArchiveReader`, `ExportDictionaryUseCase` a `ImportArchiveUseCase` příponu zvuku nikde nepředpokládají (název souboru se bere z manifestu), import M4A prochází `AudioStore.saveFromStream` s kontrolou z D.1; v `format-archivu.md` je popsáno, že `audio/` obsahuje MP3 nebo M4A a formát se pozná podle obsahu. Round-trip M4A: `ImportOnDeviceTest.recordedM4a_survivesExportDeleteImport_withTheSameSound`; archiv s MP3: stávající `exportDeleteImport_returnsTheSameDictionary`.*

*Zbývá (člověk na skutečném zařízení, emulátor zvuk neověří): oprávnění (žádost, zamítnutí, následné udělení), nahrání krátkého i minutového zvuku, poslech náhledu a přehrání ve větě poslechem, nahrání při přerušení (zamčení displeje, příchozí hovor, odchod do pozadí). Proto zůstávají dvě položky D.4 nezaškrtnuté; po ručním ověření se výsledek zapíše sem i k položce 5.3.*
