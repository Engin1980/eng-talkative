# Plán implementace

Plán postupuje po funkcionalitách (vertikální řezy přes UI, doménu i data), ne po vrstvách. Po každém kroku je aplikace spustitelná a použitelná o kousek víc než předtím. Zadání je v `assignment.md`, odložené funkce v `todo.md` (do tohoto plánu nepatří).

Obecná pravidla pro všechny kroky:
- Každý krok končí zelenými testy (`./gradlew test`, u UI kroků i instrumentované/Compose testy) a ručním ověřením na emulátoru tabletu.
- Datový model od začátku obsahuje: ID, `profileId` (`default`), `updatedAt`, příznak smazání (tombstone), aby se synchronizace nemusela dělat zpětně přepisem.
- Texty jsou v Android resources (čeština), rozměry a barvy z tématu.
- Pokud nová knihovna vyžaduje minSdk vyšší než 26, krok se zastaví a zeptá se.

---

## Krok 0: Kostra projektu

**Cíl:** Spustitelná prázdná aplikace se základní architekturou, na které lze stavět.

**Co se bude dělat:**
- Založit Gradle projekt (`cz.talkative.app`, Kotlin, Compose, Material 3, minSdk 26).
- Nastavit Hilt, Room (prázdná databáze s verzováním schématu), Coroutines/Flow.
- Rozdělit balíčky na UI / doména / data a zavést zvolený vzor (MVVM/MVI) na jedné ukázkové obrazovce.
- Téma (barvy, typografie, rozměry) s vysokým kontrastem.
- Navigace mezi klientským a administrátorským rozhraním (zatím prázdné obrazovky).
- Doplnit příkazy pro build a testy do `CLAUDE.md`.

**Kritéria splnění:**
- Aplikace se sestaví a spustí na emulátoru tabletu (API 26 i novější) a zobrazí klientskou obrazovku.
- `./gradlew test` a `./gradlew connectedAndroidTest` (nebo ekvivalent) proběhnou s alespoň jedním testem na každé úrovni.
- `CLAUDE.md` obsahuje funkční příkazy pro build, testy a spuštění jednoho testu.

---

## Krok 1: Vstup do administrace a základ slovníku (složky a slovíčka, jen text)

**Cíl:** Administrátor umí vytvořit strom složek a slovíček s titulky.

**Co se bude dělat:**
- Datový model: složka a slovíčko (titulek, rodič, pořadí), uloženo v Room jako strom přes ID rodiče.
- Skrytý ovládací prvek v klientském režimu: podržení 10 s přepne do administrace, v administraci je tlačítko pro návrat do klienta. Žádný PIN.
- Administrace: výpis obsahu složky, vytvoření, úprava a smazání složky a slovíčka (zatím bez multi-výběru), navigace do podsložek a zpět.
- Smazání neprázdné složky: rekurzivně, s potvrzením a počtem dotčených položek.

**Kritéria splnění:**
- Aplikace po startu vždy otevře klientský režim; do administrace se dostane jen podržením po dobu nastaveného intervalu (kratší podržení nedělá nic).
- Lze vytvořit alespoň 3 úrovně zanořených složek, slovíčka v nich, upravit titulky a vše smazat.
- Smazání složky s obsahem zobrazí správný počet dotčených položek; po potvrzení zmizí celý podstrom, po zrušení se nic nestane.
- Data přežijí restart aplikace.
- Unit testy repozitáře (CRUD, rekurzivní mazání, strom) a UI test podržení.

---

## Krok 2: Klient: procházení a skládání věty

**Cíl:** Klient vidí mřížku složek a slovíček a klepnutím skládá větu.

**Co se bude dělat:**
- Konfigurační úložiště (lokální, DataStore) s výchozími hodnotami z `assignment.md`; zatím bez obrazovky nastavení, hodnoty se čtou odtud.
- Klientská obrazovka: stavový řádek věty nahoře, pod ním stránkovaná mřížka složek a slovíček (velikost mřížky z konfigurace, jiná na výšku a na šířku).
- Drobečková navigace a velká tlačítka „Zpět“ a „Domů“.
- Klepnutí na složku vstoupí do složky, klepnutí na slovíčko ho přidá do věty.
- Odebrání slova ze věty podržením (výchozí 1 s), smazání celé věty tlačítkem.
- Věta se po restartu neobnovuje.

**Kritéria splnění:**
- Z dat vytvořených v kroku 1 lze projít celý strom a poskládat větu z libovolných slovíček, včetně opakování stejného slova.
- Stránkování správně rozdělí položky podle velikosti mřížky (např. 10 položek v mřížce 3×3 jsou 2 stránky) a funguje při otočení tabletu.
- Breadcrumbs vždy odpovídají aktuální cestě, „Domů“ vede do kořene, „Zpět“ o úroveň výš.
- Krátké klepnutí na slovo ve větě nic neodebere, podržení po nastavený interval ano.
- Dotykové plochy mají minimálně 64 dp.
- Unit testy view modelu (skládání věty, stránkování, navigace) a Compose UI testy hlavního scénáře.

---

## Krok 3: Přehrání věty hlasem (TTS)

**Cíl:** Klient větu stiskem tlačítka přečte nahlas a může přehrávání zastavit.

**Co se bude dělat:**
- Rozhraní `SpeechProvider` a implementace nad Android `TextToSpeech` (čeština, hlas dle zařízení).
- Tlačítko Přehrát, které se při přehrávání mění na Stop.
- Sestavení výpovědi: titulky slovíček se spojí do jednoho textu.
- Ošetření chyb: chybějící český hlas nebo nedostupný TTS engine zobrazí srozumitelnou hlášku.

**Kritéria splnění:**
- Poskládaná věta se přečte jedním plynulým TTS výstupem, ne po slovech.
- Stop okamžitě přeruší řeč a tlačítko se vrátí na Přehrát; opětovné přehrání funguje.
- Prázdná věta: tlačítko Přehrát je neaktivní nebo nic nedělá.
- Unit test skládání textu s falešným `SpeechProvider`; ruční ověření hlasu na emulátoru/zařízení.

---

## Krok 4: Obrázky slovíček

**Cíl:** Slovíčka mají volitelný obrázek, který se zobrazuje v mřížce i ve větě.

**Co se bude dělat:**
- Admin: výběr obrázku z galerie a pořízení fotografie, zmenšení při uložení (např. 512 px), uložení do soukromého úložiště aplikace, odebrání obrázku.
- Dlaždice v mřížce: obrázek a titulek; slovíčko bez obrázku jen text.
- Věta: menší dlaždice s obrázkem a titulkem (skrytí titulku podle konfigurace).
- Správa oprávnění a chyb (fotoaparát zamítnut, soubor nelze přečíst).

**Kritéria splnění:**
- Obrázek lze přidat, vyměnit a odebrat; přežije restart aplikace.
- Uložený soubor je zmenšen na limit a původní velký obrázek se neukládá.
- Smazání slovíčka smaže i jeho soubor obrázku (žádné osiřelé soubory).
- Zobrazení v mřížce a ve větě odpovídá zadání, bez obrázku se zobrazí jen text.
- Testy: zmenšení obrázku, čištění souborů, UI zobrazení s obrázkem i bez.

---

## Krok 5: Vlastní zvuková definice slovíčka

**Cíl:** Slovíčko může mít jiný text pro čtení nebo vlastní MP3 a věta se přehraje správně.

**Co se bude dělat:**
- Admin: u slovíčka volitelný alternativní text pro TTS nebo výběr MP3 souboru ze zařízení (omezení délky na desítky sekund, kontrola velikosti/formátu), náhled zvuku v administraci.
- Přehrávání: alternativní text nahradí titulek; narazí-li věta na MP3, TTS se přeruší, přehraje se MP3 a pokračuje se.
- Rozšíření `SpeechProvider` o přehrávání MP3 (přehrávání na pozadí bez zamrznutí UI, Stop funguje i během MP3).
- Úklid souboru MP3 při smazání nebo výměně.

**Kritéria splnění:**
- Slovo s alternativním textem se čte podle něj, ne podle titulku.
- Věta „A, [MP3], B, C“ se přehraje jako TTS(A) → MP3 → TTS(B C) bez překryvu a bez zbytečných pauz.
- Stop během TTS i během MP3 zastaví vše a další slova se nepřehrají.
- Příliš dlouhý nebo nečitelný MP3 je odmítnut s hláškou.
- Testy: plánování úseků věty (sloučení TTS, dělení MP3) jako čistá logika, ruční ověření zvuku.

---

## Krok 6: Pokročilá správa slovníku

**Cíl:** Administrátor pohodlně přesouvá a řadí položky, včetně hromadných operací.

**Co se bude dělat:**
- Režim multi-výběru v administraci (slovíčka i složky najednou).
- Hromadný přesun do cílové složky (s ochranou proti přesunu složky do sebe sama nebo do vlastního podstromu).
- Ruční řazení položek ve složce (přesouváním), uložené pořadí se používá v administraci i v klientu.
- Rychlé seřazení podle abecedy s kolací z konfigurace (čeština: správné řazení „ch“, diakritiky).

**Kritéria splnění:**
- Označení více položek a přesun do jiné složky je jedna operace; po ní je vše na novém místě a ve staré složce zmizelo.
- Pokus o přesun složky do jejího potomka je odmítnut.
- Po ručním přeřazení a restartu je pořadí stejné, klient vidí stejné pořadí.
- Abecední řazení řadí česky (např. „cibule“ před „chléb“, „d“ po „ch“), změna kolace v konfiguraci změní výsledek.
- Unit testy přesunů, ochrany cyklů a řazení.

---

## Krok 7: Konfigurace a pokročilé ovládání klienta

**Cíl:** Administrátor nastaví chování klienta a klient může věty skládat i tažením.

**Co se bude dělat:**
- Obrazovka konfigurace v administraci: způsob přidání slova (klepnutí/tažení), povolení změny pořadí, interval podržení, přehrání slova při klepnutí, velikost mřížky, kolace, skrytí titulků ve větě, automatické smazání věty po přehrání.
- Režim tažení: přetažení slova do stavového řádku, odtažení slova pryč z řádku jej odebere.
- Změna pořadí slov ve větě posunováním vlevo/vpravo (lze zakázat).
- Přehrání jednotlivého slova při klepnutí (pokud zapnuto) a automatické smazání věty po přehrání (pokud zapnuto).
- Konfigurace je lokální a nesynchronizuje se.

**Kritéria splnění:**
- Každé nastavení má viditelný efekt v klientovi hned po návratu z administrace a přežije restart.
- V režimu tažení klepnutí slovo nepřidá a tažení ano (a naopak v režimu klepnutí).
- Zakázaná změna pořadí opravdu nejde provést.
- Výchozí hodnoty odpovídají `assignment.md`.
- Compose UI testy pro oba režimy ovládání a pro nastavení.

---

## Krok 8: Export a import

**Cíl:** Slovník nebo jeho část lze uložit do souboru a později načíst.

**Co se bude dělat:**
- Formát: ZIP s `manifest.json` (verze schématu, struktura složek a slovíček) a adresáři pro obrázky a zvuky.
- Export celého slovníku nebo vybrané složky (včetně podstromu) přes systémový výběr souboru (SAF); volitelně i konfigurace (zaškrtávátko).
- Import: celého archivu nebo vybraných složek/podsložek do zvolené cílové složky (výchozí kořen); vždy přidává s novými ID a nic nepřepisuje; volitelně nahrazení celého slovníku (s potvrzením).
- Kontrola verze schématu a odolnost vůči poškozenému archivu.

**Kritéria splnění:**
- Export → smazání dat → import vrátí identický slovník (struktura, titulky, pořadí, obrázky, zvuky).
- Opakovaný import téhož archivu vytvoří duplicity s novými ID, původní data zůstanou nedotčena.
- Částečný import jen vybrané složky ji vloží do zvolené cílové složky včetně jejích médií.
- Poškozený nebo cizí archiv i archiv s novější verzí schématu skončí srozumitelnou chybou bez změny dat.
- Archiv neobsahuje nic jiného než povolená data (žádný PIN, žádné přihlašovací údaje).
- Unit testy (de)serializace a import/export round-trip.

---

## Krok 9: Synchronizace s Google Drive

**Cíl:** Data se zálohují na Google Drive a po instalaci na jiném zařízení se stejným účtem se dotáhnou.

**Co se bude dělat:**
- Výběr Google účtu systémovým dialogem při prvním spuštění nebo v administraci; bez účtu aplikace běží jen lokálně.
- Úložiště ve skryté složce aplikace (`drive.appdata`): metadata jako JSON, média jako samostatné soubory.
- Slučování změn podle `updatedAt` (last write wins), mazání přes tombstony, úklid starých tombstonů.
- Spouštění synchronizace: po změně v administraci (je-li síť), při startu aplikace, ručně tlačítkem; opakování po výpadku sítě na pozadí.
- Stav synchronizace viditelný v administraci (poslední úspěšná synchronizace, chyba).
- Konfigurace se nesynchronizuje.

**Kritéria splnění:**
- Offline aplikace funguje beze změn a nahromaděné změny se po připojení odešlou.
- Po instalaci na druhém zařízení (nebo po vymazání dat) a přihlášení stejným účtem se objeví stejný slovník včetně obrázků a zvuků.
- Smazání na jednom zařízení se po synchronizaci projeví na druhém; úprava vs. smazání vyhrává novější změna.
- Souběžné úpravy ze dvou zařízení nezpůsobí pád ani ztrátu celého slovníku; vyhrává poslední zápis.
- Výpadek sítě uprostřed synchronizace nenechá lokální data v poškozeném stavu.
- Testy slučování a konfliktů nad falešným úložištěm; ruční test se dvěma zařízeními/emulátory a skutečným účtem.

---

## Krok 10: Nekonzistence a zotavení

**Cíl:** Aplikace se po poškození dat nerozsype a upozorní administrátora výkřičníkem.

**Co se bude dělat:**
- Detekce: chybějící soubor obrázku nebo MP3, slovíčko odkazující na neexistující složku, nevyřešitelný konflikt při synchronizaci.
- Označení výkřičníkem u dotčeného slovíčka v administraci (a bezpečné chování v klientu: chybějící obrázek se zobrazí jako text, chybějící MP3 se přeskočí nebo přečte titulek).
- Zotavení: osiřelé položky se zobrazí na dohledatelném místě, aby je šlo opravit nebo smazat; po opravě výkřičník zmizí.
- Pokus o opětovné stažení chybějících souborů z Drive při další synchronizaci.

**Kritéria splnění:**
- Ručně smazaný soubor obrázku/MP3 nebo uměle rozbitá reference vede k výkřičníku, nikoli k pádu ani prázdné obrazovce.
- Po opravě administrátorem (nahrání souboru, přesun, smazání) výkřičník zmizí.
- Klient s poškozenými daty dál skládá a přehrává věty.
- Testy pro každou z tří situací.

---

## Krok 11: Dokončení a předání

**Cíl:** Stabilní první verze připravená k interní distribuci.

**Co se bude dělat:**
- Projít přístupnost: velikosti dotykových ploch, kontrast, žádná časově omezená gesta, orientace na výšku i na šířku na různých velikostech tabletu.
- Informace o soukromí (co se ukládá a kam) dostupná v administraci.
- Kontrola výkonu při velkém slovníku (stovky položek, desítky obrázků).
- Podepsaný release APK pro interní distribuci, návod k instalaci.
- Aktualizace `CLAUDE.md`, `assignment.md` a `todo.md` podle skutečného stavu.

**Kritéria splnění:**
- Všechny testy jsou zelené a projdou ručním scénářem: vytvořit slovník → poskládat větu → přehrát → export/import → synchronizace na druhé zařízení.
- Aplikace běží plynule na minimálně podporovaném Androidu 8.0 i na novějším.
- Release APK se nainstaluje na čisté zařízení a funguje včetně přihlášení k Drive.
- Dokumentace odpovídá kódu.
