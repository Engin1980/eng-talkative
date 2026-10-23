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
- [ ] Krok 5: Vlastní zvuková definice slovíčka
- [x] Krok 6: Pokročilá správa slovníku
- [x] Krok 7: Konfigurace a pokročilé ovládání klienta
- [x] Krok 8: Export a import _(všech 22 instrumentovaných tříd, 260 testů, prošlo po jedné; jeden společný běh systém ukončil pro nedostatek paměti)_
- [ ] Krok 9: Synchronizace s Google Drive
- [ ] Krok 10: Nekonzistence a zotavení
- [ ] Krok 11: Dokončení a předání

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

*Rozšíření: nahrání zvuku mikrofonem (M4A) je v `plan-dictate.md`.* *Ruční ověření nahrávání poslechem na skutečném tabletu je zatím otevřené (viz D.4 v `plan-dictate.md`).*

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
- [ ] Ruční ověření zvuku na zařízení. _(neověřeno: emulátor nemá zvuk, ověří člověk poslechem)_

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
- [ ] Založit/ověřit Google Cloud projekt, povolit Drive API, nastavit OAuth klienta pro `cz.talkative.app` a SHA-1 debug klíče (postup zapsat do dokumentace).
- [ ] Výběr Google účtu systémovým dialogem při prvním spuštění nebo v administraci.
- [ ] Bez účtu aplikace běží jen lokálně; odhlášení/změna účtu v administraci.

**Kritéria splnění:**
- [ ] Po výběru účtu aplikace získá oprávnění `drive.appdata` a zapamatuje si účet.
- [ ] Odmítnutí přihlášení nezpůsobí pád a aplikace dál funguje lokálně.

### 9.2 Abstrakce úložiště a model sloučení

**Úkoly:**
- [ ] Rozhraní vzdáleného úložiště (výpis, čtení, zápis, mazání) s falešnou implementací pro testy.
- [ ] Návrh struktury na Drive: metadata jako JSON, média jako samostatné soubory; zdokumentovat.
- [ ] Čistá logika slučování podle `updatedAt` (last write wins) včetně tombstonů a konfliktu delete-vs-edit.
- [ ] Úklid starých tombstonů (po definované době).

**Kritéria splnění:**
- [ ] Unit testy slučování: nová položka, úprava na jedné straně, souběžné úpravy, smazání vs. úprava (vyhrává novější), tombstone.
- [ ] Slučování je idempotentní (opakované spuštění nic nemění).

### 9.3 Implementace Drive a přenos médií

**Úkoly:**
- [ ] Implementace úložiště nad Drive API (`drive.appdata`).
- [ ] Nahrávání a stahování obrázků a MP3; přenášejí se jen změněné soubory.
- [ ] Lokální data se mění až po úspěšném stažení (aplikace změn v transakci), aby výpadek sítě nenechal poškozený stav.

**Kritéria splnění:**
- [ ] Po instalaci na druhém zařízení (nebo po vymazání dat) a přihlášení stejným účtem se objeví stejný slovník včetně obrázků a zvuků.
- [ ] Výpadek sítě uprostřed synchronizace nenechá lokální data v poškozeném stavu.

### 9.4 Spouštění a stav synchronizace

**Úkoly:**
- [ ] Synchronizace po změně v administraci (je-li síť), při startu aplikace a ručním tlačítkem.
- [ ] Opakování po výpadku sítě na pozadí (WorkManager s omezením na síť, exponenciální backoff).
- [ ] Stav synchronizace v administraci (probíhá, poslední úspěšná synchronizace, chyba).
- [ ] Konfigurace se nesynchronizuje.

**Kritéria splnění:**
- [ ] Offline aplikace funguje beze změn a nahromaděné změny se po připojení odešlou.
- [ ] Smazání na jednom zařízení se po synchronizaci projeví na druhém; úprava vs. smazání vyhrává novější změna.
- [ ] Souběžné úpravy ze dvou zařízení nezpůsobí pád ani ztrátu celého slovníku; vyhrává poslední zápis.
- [ ] Ruční test se dvěma zařízeními/emulátory a skutečným účtem.

---

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
