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

- [ ] Krok U1: Posouvání obrazovek a dialogů, které se nevejdou na výšku

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
- [ ] Sepsat seznam všech obrazovek a dialogů (`AlertDialog`, `Dialog`, bottom sheety, celé obrazovky v `ui/client`, `ui/admin`, `ui/config`, `ui/exchange`, `ui/arasaac`, `ui/draw`, `ui/about`, oznámení při prvním spuštění `NoticeGate`, `FolderPickerDialog`) a u každého zjistit, zda je obsah v `verticalScroll`/`LazyColumn`, nebo má pevnou výšku.
- [ ] Ověřit na emulátoru (malý tablet a telefon, na výšku i na šířku, s klávesnicí a se zvětšeným písmem – velikost písma v systému 130 % a více), které z nich se ořezávají nebo se nedají posunout.
- [ ] Výsledek zapsat do tohoto souboru jako tabulku (obrazovka/dialog, stav: v pořádku / nejde posouvat, poznámka) a podle ní naplnit úkoly podkroku U1.2.

**Kritéria splnění:**
- [ ] V plánu je úplná tabulka obrazovek a dialogů s výsledkem prověření; žádná obrazovka ani dialog nechybí.
- [ ] U každé položky označené „nejde posouvat“ je uvedeno, za jakých podmínek (rozlišení, orientace, klávesnice, velikost písma) se problém projeví.

### U1.2 Úprava rozložení, aby šlo posouvat

**Úkoly:**
- [ ] Obsah každého dotčeného dialogu a obrazovky zabalit tak, aby se při nedostatku výšky posouval (`verticalScroll` nebo `LazyColumn`); tlačítka potvrzení/zrušení zůstanou stále dosažitelná (buď pevná patička dialogu mimo posouvaný obsah, nebo na konci posouvaného obsahu – zvolit jednotně pro všechny dialogy).
- [ ] U dialogů zajistit maximální výšku podle displeje a správné chování s klávesnicí (`imePadding`, popř. posun k fokusovanému poli).
- [ ] Sjednotit řešení do sdílené komponenty nebo modifikátoru v `ui/` (ne kopírovat do každého dialogu zvlášť).
- [ ] Viditelný ukazatel posunu nebo jiná nápověda, že je obsah delší (např. posuvník nebo stín/přechod u okraje); zvolit řešení, které funguje i při dotykovém ovládání, kde systémové posuvníky mizí.

**Kritéria splnění:**
- [ ] Na malém tabletu na šířku (nejnižší podporované rozlišení) i při zvětšeném písmu lze v každém dialogu a na každé obrazovce dojít posouváním ke všem prvkům, včetně tlačítek dole.
- [ ] Se zobrazenou klávesnicí zůstává pole, které se edituje, viditelné a lze se dostat i k tlačítkům potvrzení.
- [ ] Obsah, který se vejde, se nijak neposouvá a vypadá jako dřív (bez zbytečného prázdného místa a bez zbytečného ukazatele).
- [ ] Dotykové plochy zůstávají alespoň 64 dp a posouvání neruší stávající gesta (tažení slov, změna pořadí, podržení).

### U1.3 Testy a ruční ověření

**Úkoly:**
- [ ] Compose UI testy pro dotčené dialogy a obrazovky: na uměle sníženém okně (malá výška) a se zvětšeným písmem je poslední prvek dosažitelný přes `performScrollTo()` a lze na něj klepnout.
- [ ] Ruční průchod všech položek z tabulky v U1.1 na emulátoru (výška i šířka, klávesnice, zvětšené písmo).
- [ ] Aktualizovat `CLAUDE.md` (sekce „Current state“) o sdílenou komponentu pro posouvání dialogů a pravidlo, že nové dialogy ji musí používat.

**Kritéria splnění:**
- [ ] `./gradlew test` a dotčené instrumentované testy jsou zelené (instrumentované třídy se pouští po jedné, viz poznámka v `plan.md` ke kroku 8).
- [ ] Všechny položky z tabulky v U1.1 mají po úpravě stav „v pořádku“.
- [ ] `CLAUDE.md` odpovídá kódu.


## Krok U2: Drobné úpravy UI

### U2.1 Drobné úpravy chování

- [x] Délka intervalu stisku tlačítka pro přechod Klient->Admin je nastavitelná v nastavení (`adminHoldMillis`, 3–30 s, krok 1 s, `ConfigScreen`). Už hotovo dříve, nic se nemění.
- [ ] Stejně jako je interval pro odstranění slova podržením, implementuj interval pro přidání slova podržením do větného řádku. Tj. přidání nebude nutně na "klik", ale v nastavení lze říci, jak dlouho se musí slovo podržet, aby se přidalo. Pokud je slovo stisknuto pro přidání, zvýrazni zeleným orámováním, že je drženo.
  - Rozhodnutí: režimy přidávání slov zůstávají dva: **stisk** (s intervalem) a **tažení** (drag & drop, beze změny). Volí se na obrazovce nastavení, lokálně pro zařízení.
  - Režim stisku dostane nové nastavení `addHoldMillis`: výchozí 0 (slovo se přidá hned na klepnutí, jako dnes), rozsah 0–5 s, krok 0,5 s (`ConfigLimits`), uložené v DataStore jako ostatní hodnoty. Je-li hodnota větší než 0, slovo se přidá až po podržení po tuto dobu a zvýrazní se zeleným orámováním.
  - Nastavení intervalu je aktivní jen v režimu stisku. Žádná změna schématu ani migrace; stávající uložené hodnoty zůstávají platné (chybějící `addHoldMillis` = 0).

### U2.2 Drobné úpravy vzhledu

Rozhodnutí: použijí se ikony z Material Icons (Home, ArrowBack, PlayArrow/Stop, Delete); tlačítka bez viditelného textu mají `contentDescription` z českých řetězců ve `strings.xml` (čtečka obrazovky), dotyková plocha zůstává alespoň 64 dp.

- [ ] V navigačním prvku, který ukazuje průběžné zanoření do složek nahraď výraz "Domů" za obrázek s domečkem.
- [ ] V navigačním prvku nahraď tlačítko "Zpět" jen šipkou doleva. 
- [ ] V klientském okně ponech u tlačítka "Přehrát" jen ikonu bez textu.
- [ ] V klientském okně ponech u tlačítka "Smazat větu" jen ikonu bez textu.
- [ ] Ve větném řádku, když se stiskne slovo (aby při delším stisku bylo odstraněno), zvýrazně červeným orámováním, že je drženo.