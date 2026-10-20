# Plán úprav uživatelského rozhraní

Seznam změn vzhledu a ovládání, které **zásadně nemění funkcionalitu**. Plán postupuje po jednotlivých úpravách (každá je samostatný krok). Zadání je v `assignment.md`, hlavní plán implementace v `plan.md`, odložené funkce v `todo.md` (do tohoto plánu nepatří).

## Jak s plánem pracovat

- Každý krok je rozdělen na **podkroky** (např. U1.1, U1.2), které jsou dostatečně malé na jednu pracovní session. Lze zadat např. „proveď podkrok U1.2 z `plan-ui-updates.md`“.
- U každého podkroku jsou dva seznamy: **Úkoly** (co udělat) a **Kritéria** (jak ověřit, že je hotovo). Podkrok je hotový, až jsou zaškrtnuté obě části.
- Po dokončení položky se v tomto souboru změní `- [ ]` na `- [x]`. Souhrnný přehled níže se zaškrtne, až jsou hotové všechny podkroky daného kroku.
- Po každém dokončeném podkroku: zelené testy, zaškrtnutí v plánu a **vždy samostatný commit a push** (kód, testy i zaškrtnutí v plánu patří do stejného commitu; zpráva commitu uvádí číslo podkroku, např. „Krok U1.2: Posouvání dialogů“). Při zadání celého kroku se commit dělá po každém jeho podkroku. Rozpracovaný podkrok se nenechává v nefunkčním stavu a necommituje se; pokud dojde limit, nechá se v plánu poznámka, co zbývá.
- Nové úpravy se připisují na konec přehledu jako další krok (U2, U3, …) ve stejné struktuře.
- Pokud se při práci objeví nová otázka, postupuje se podle workflow s `guestions.md` popsaného v `CLAUDE.md`.

## Přehled postupu

- [ ] Krok U1: Posouvání obrazovek a dialogů, které se nevejdou na výšku (zbývá ruční ověření a oprava starých testů)

## Obecná pravidla pro všechny kroky

- Úpravy nemění chování ani data; mění se jen rozložení, vzhled a ovládání. Pokud se ukáže, že úprava vyžaduje změnu funkcionality, krok se zastaví a zeptá se (viz `guestions.md`).
- Každý krok končí zelenými testy (`./gradlew test`, u UI kroků i instrumentované/Compose testy) a ručním ověřením na emulátoru tabletu.
- Texty jsou v Android resources (čeština), rozměry a barvy z tématu (`TalkativeTheme.dimens`, `MaterialTheme`); v kódu obrazovek nic natvrdo.
- Dotykové plochy zůstávají alespoň 64 dp, kontrast vysoký, žádná složitá gesta (viz `CLAUDE.md`).
- Obrazovky se musí chovat správně na výšku i na šířku, při zvětšeném písmu a se zobrazenou softwarovou klávesnicí.
- Kód je jednoduchý a čitelný (vlastník zná Javu); preferuje se jasná architektura před zkratkami.
- Testy se píší tak, aby skutečně ověřovaly požadované chování, ne jen aby procházely.

---

## Krok U1: Posouvání obrazovek a dialogů, které se nevejdou na výšku

**Cíl:** Je-li obsah obrazovky nebo dialogu vyšší než dostupná výška displeje (malé rozlišení tabletu, orientace na šířku, zobrazená klávesnice, zvětšené písmo), lze ho posunout a žádný prvek (zejména tlačítka dole) není nedosažitelný. Posouvání je navíc vidět (ukazatel posunu nebo jiná nápověda), aby bylo zřejmé, že pod okrajem je další obsah.

**Poznámka:** Problém hlášen vlastníkem: u vyšších obrazovek (zejména modálních) na rozlišení tabletu nejsou vidět posuvníky a obsah nejde posouvat na výšku, takže spodní prvky nejsou vidět. Nejprve prověřit, kde přesně k tomu dochází, a teprve potom upravovat.

### U1.1 Prověření všech obrazovek a dialogů

**Úkoly:**
- [x] Sepsat seznam všech obrazovek a dialogů (`AlertDialog`, `Dialog`, bottom sheety, celé obrazovky v `ui/client`, `ui/admin`, `ui/config`, `ui/exchange`, `ui/arasaac`, `ui/draw`, `ui/about`, oznámení při prvním spuštění `NoticeGate`, `FolderPickerDialog`) a u každého zjistit, zda je obsah v `verticalScroll`/`LazyColumn`, nebo má pevnou výšku.
- [x] Ověřit na emulátoru (malý tablet a telefon, na výšku i na šířku, s klávesnicí a se zvětšeným písmem – velikost písma v systému 130 % a více), které z nich se ořezávají nebo se nedají posunout.
- [x] Výsledek zapsat do tohoto souboru jako tabulku (obrazovka/dialog, stav: v pořádku / nejde posouvat, poznámka) a podle ní naplnit úkoly podkroku U1.2.

**Kritéria splnění:**
- [x] V plánu je úplná tabulka obrazovek a dialogů s výsledkem prověření; žádná obrazovka ani dialog nechybí.
- [x] U každé položky označené „nejde posouvat“ je uvedeno, za jakých podmínek (rozlišení, orientace, klávesnice, velikost písma) se problém projeví.

**Výsledek prověření (U1.1)** – stav je před úpravou U1.2; prověřeno kódem a na emulátoru Pixel Tablet API 35 zmenšeném na 800 × 360 dp (`wm size 1600x720`) s písmem 130 %:

| Obrazovka / dialog | Stav před úpravou | Podmínky a poznámka |
|---|---|---|
| Klient (`ClientScreen`) | v pořádku | Záměrně stránkovaná mřížka bez posunu (posun by kolidoval s tažením slov); mění se jen velikost buněk. |
| Chyba řeči (`SpeechErrorDialog`) | text nešel posouvat | Dlouhý text při písmu 130 %+ na šířku. |
| Administrace (`AdminScreen`) | nejde posouvat | Na 360 dp výšky a písmu 130 % hlavička a tlačítka zabrala celou výšku, seznam dostal nulovou výšku; řádek tlačítek se nezalamoval. |
| Editace slova/složky (`EditDialog`) | posouval se, ale bez ukazatele | Na šířku nebo s klávesnicí; nebylo poznat, že je pod okrajem další obsah. |
| Mazání, mazání výběru, řazení (`AlertDialog`) | text nešel posouvat | Dlouhá zpráva při písmu 130 %+ na šířku. |
| Přesun (`MoveTargetDialog`), `FolderPickerDialog` | posouval se, bez ukazatele | Hodně podsložek, na šířku. |
| Nastavení (`ConfigScreen`) | posouvala se jen část, bez ukazatele | Při malé výšce se oblast posunu zmenšila. |
| Obnovení nastavení (`ResetDialog`) | text nešel posouvat | Jako ostatní potvrzovací dialogy. |
| Export a import (`ExchangeScreen`, `ImportSection`, `ConfirmReplaceDialog`) | posouvala se jen část, bez ukazatele; řada tlačítek se nezalamovala | Na výšku a při písmu 130 %. |
| O aplikaci (`AboutScreen`/`AboutContent`) | posouvala se, bez ukazatele | Na šířku. |
| Úvodní oznámení (`NoticeScreen`) | tlačítka se nezalamovala | Tlačítka dole zůstávají pevná, obsah se posouvá. |
| Hledání ARASAAC (`ArasaacScreen`) | nejde posouvat | S klávesnicí nebo na malé výšce dostala mřížka výsledků nulovou výšku. |
| Kreslení (`DrawScreen`, `ConfirmClearDialog`, `ConfirmDiscardDialog`) | panel nástrojů se posouval, bez ukazatele; na nízkém okně mohla vyjít záporná velikost plátna | Plátno má teď minimum 64 dp. |

**Rozhodnutí U1.2:** sdílený `ScrollableColumn` v `ui/common/ScrollIndicator.kt` (trvale viditelný jezdec za pravým okrajem, kreslí se jen při přetečení; varianty `Modifier.scrollIndicator` pro `ScrollState`, `LazyListState`, `LazyGridState`). Dialogy mají tlačítka pevně mimo posouvaný obsah (`AlertDialog`), obsah v `text` je vždy `ScrollableColumn`. Administrace a hledání ARASAAC jsou jediný líný seznam/mřížka včetně hlavičky.

### U1.2 Úprava rozložení, aby šlo posouvat

**Úkoly:**
- [x] Obsah každého dotčeného dialogu a obrazovky zabalit tak, aby se při nedostatku výšky posouval (`verticalScroll` nebo `LazyColumn`); tlačítka potvrzení/zrušení zůstanou stále dosažitelná (buď pevná patička dialogu mimo posouvaný obsah, nebo na konci posouvaného obsahu – zvolit jednotně pro všechny dialogy).
- [x] U dialogů zajistit maximální výšku podle displeje a správné chování s klávesnicí (`imePadding`, popř. posun k fokusovanému poli).
- [x] Sjednotit řešení do sdílené komponenty nebo modifikátoru v `ui/` (ne kopírovat do každého dialogu zvlášť).
- [x] Viditelný ukazatel posunu nebo jiná nápověda, že je obsah delší (např. posuvník nebo stín/přechod u okraje); zvolit řešení, které funguje i při dotykovém ovládání, kde systémové posuvníky mizí.

**Kritéria splnění:**
- [x] Na malém tabletu na šířku (nejnižší podporované rozlišení) i při zvětšeném písmu lze v každém dialogu a na každé obrazovce dojít posouváním ke všem prvkům, včetně tlačítek dole.
- [x] Se zobrazenou klávesnicí zůstává pole, které se edituje, viditelné a lze se dostat i k tlačítkům potvrzení.
- [x] Obsah, který se vejde, se nijak neposouvá a vypadá jako dřív (bez zbytečného prázdného místa a bez zbytečného ukazatele).
- [x] Dotykové plochy zůstávají alespoň 64 dp a posouvání neruší stávající gesta (tažení slov, změna pořadí, podržení).

### U1.3 Testy a ruční ověření

**Úkoly:**
- [x] Compose UI testy pro dotčené dialogy a obrazovky: na uměle sníženém okně (malá výška) a se zvětšeným písmem je poslední prvek dosažitelný přes `performScrollTo()` a lze na něj klepnout.
- [ ] Ruční průchod všech položek z tabulky v U1.1 na emulátoru (výška i šířka, klávesnice, zvětšené písmo). *Hotovo jen pro Administraci, Editaci slova, Nastavení, Export a import a O aplikaci (800 × 360 dp, písmo 130 %); zbývá ARASAAC, Kreslení, Úvodní oznámení, potvrzovací dialogy a průchod s klávesnicí.*
- [x] Aktualizovat `CLAUDE.md` (sekce „Current state“) o sdílenou komponentu pro posouvání dialogů a pravidlo, že nové dialogy ji musí používat.

**Kritéria splnění:**
- [ ] `./gradlew test` a dotčené instrumentované testy jsou zelené. *`test` a `SmallScreenScrollTest` (9 testů) jsou zelené, stejně `ClientScreenTest`; `AdminFlowTest` (13) a 2 testy v `AdminScreenTest` padají, protože hledají staré popisky „Nová složka“ / „Nové slovíčko“, které vlastník přepsal ve `strings.xml` na „+ 📁“ / „New word“ (nesouvisí s U1). Ostatní třídy ve `ui` se nepodařilo dokončit: emulátor při souběžném běhu spadl.* (instrumentované třídy se pouští po jedné, viz poznámka v `plan.md` ke kroku 8).
- [ ] Všechny položky z tabulky v U1.1 mají po úpravě stav „v pořádku“. *Podle kódu a testů ano; ručně viz výše.*
- [x] `CLAUDE.md` odpovídá kódu.


## Krok U2: Drobné úpravy UI

### U2.1 Drobné úpravy chování

- [x] Délka intervalu stisku tlačítka pro přechod Klient->Admin je nastavitelná v nastavení (`adminHoldMillis`, 3–30 s, krok 1 s, `ConfigScreen`). Už hotovo dříve, nic se nemění.
- [x] Stejně jako je interval pro odstranění slova podržením, implementuj interval pro přidání slova podržením do větného řádku. Tj. přidání nebude nutně na "klik", ale v nastavení lze říci, jak dlouho se musí slovo podržet, aby se přidalo. Pokud je slovo stisknuto pro přidání, zvýrazni zeleným orámováním, že je drženo.
  - Rozhodnutí: režimy přidávání slov zůstávají dva: **stisk** (s intervalem) a **tažení** (drag & drop, beze změny). Volí se na obrazovce nastavení, lokálně pro zařízení.
  - Režim stisku dostane nové nastavení `addHoldMillis`: výchozí 0 (slovo se přidá hned na klepnutí, jako dnes), rozsah 0–5 s, krok 0,5 s (`ConfigLimits`), uložené v DataStore jako ostatní hodnoty. Je-li hodnota větší než 0, slovo se přidá až po podržení po tuto dobu a zvýrazní se zeleným orámováním.
  - Nastavení intervalu je aktivní jen v režimu stisku. Žádná změna schématu ani migrace; stávající uložené hodnoty zůstávají platné (chybějící `addHoldMillis` = 0).

### U2.2 Drobné úpravy vzhledu

Provedení: slovo „Domů“ nahradil domeček v tlačítku klienta, v drobečkové navigaci administrace a ve výběru složky (`FolderPickerDialog`); kořen drobečků v klientovi se jmenuje „Úvod“ a zůstal textem. Orámování drženého slova je z `TalkativeTheme.holdColors` (zelené při přidání, červené při odebrání) a drženému slovu se nastaví `stateDescription` „drženo“ (čtečka obrazovky, testy). Přidáno nastavení `addHoldMillis` i do exportovaného archivu (`ArchiveConfig`, výchozí 0, starší archivy se čtou beze změny).

Rozhodnutí: použijí se ikony z Material Icons (Home, ArrowBack, PlayArrow/Stop, Delete); tlačítka bez viditelného textu mají `contentDescription` z českých řetězců ve `strings.xml` (čtečka obrazovky), dotyková plocha zůstává alespoň 64 dp.

- [x] V navigačním prvku, který ukazuje průběžné zanoření do složek nahraď výraz "Domů" za obrázek s domečkem.
- [x] V navigačním prvku nahraď tlačítko "Zpět" jen šipkou doleva. 
- [x] V klientském okně ponech u tlačítka "Přehrát" jen ikonu bez textu.
- [x] V klientském okně ponech u tlačítka "Smazat větu" jen ikonu bez textu.
- [x] Ve větném řádku, když se stiskne slovo (aby při delším stisku bylo odstraněno), zvýrazně červeným orámováním, že je drženo.