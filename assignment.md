# Zadání: Talkative

Cílem je vytvořit Android aplikaci „Talkative“, která pomáhá lidem s problémem komunikace. Aplikace umí sestavit větu z předem definovaných slov a na pokyn ji přečíst pomocí interního mluvícího systému (TTS). Aplikaci používají 2 druhy uživatelů: **administrátor** a **klient**. Obě role používají stejné zařízení a stejnou aplikaci.

Administrátor do aplikace vkládá slovíčka. Slovíčka lze seskupovat do složek, složky lze libovolně zanořovat. Každé slovíčko i složka má titulek. Slovíčka mají volitelně i obrázek. Titulek slovíčka udává, jak se dané slovo bude číst. Volitelně lze ke slovíčku definovat jinou zvukovou definici, a to buď jako jiný text, nebo přímo jako vlastní zvuk (MP3 soubor, nebo nahrávka mikrofonem). Tímto způsobem administrátor vytvoří databázi slov, kterou aplikace následně používá.

U klienta se předpokládá nějaká forma omezení, typicky neschopnost jemné motoriky rukou a neschopnost vyjadřovat se, ale lucidní přemýšlení alespoň v nějaké oblasti. Klient dostane spuštěnou aplikaci v klientském režimu, ve kterém vidí složky, případně slovíčka. Na horní straně obrazovky je stavový řádek (věta), do kterého lze jednotlivá slovíčka postupně přidávat za sebe. Když klient větu sestaví, stiskem tlačítka ji spustí.

## Role a režimy

- Aplikace startuje vždy v **klientském režimu**.
- Přechod mezi klientským a administrátorským rozhraním se provádí **skrytým ovládacím prvkem** (dlouhé podržení, výchozí interval 10 s). Žádný PIN se nepoužívá. Účelem není bezpečnost, jen ochrana před náhodným vstupem klienta do administrace.
- Jedno zařízení, jedna aplikace. Jeden slovník na instalaci, ale data jsou uložena v profilu `default`, aby bylo později možné přidat více profilů bez přepisu datového modelu.

## Vzhled

Administrátorské rozhraní musí vypadat univerzálně použitelně, bez speciálních nároků. Očekává se běžný uživatel, ne IT specialista, spíše sociálně zaměřený pracovník. Administrace používá stejný vizuální jazyk a komponenty (Material 3) jako klient, jen s přidanými editačními prvky.

Klient téměř jistě má velmi snížené motorické i myšlenkové schopnosti. Rozhraní je základní a jednoduché, položky se přesouvají tahem nebo stiskem (podle nastavení). Na horním řádku se sestavuje věta a ve zbytku obrazovky se procházejí nabízená slovíčka.

- Primárně tablet, layout je responzivní, podporuje orientaci na výšku i na šířku.
- Slovíčka a složky se zobrazují jako **mřížka** s velkými dlaždicemi a **stránkováním**. Velikost mřížky nastavuje administrátor v konfiguraci.
- Velké dotykové plochy (minimálně 64 dp), vysoký kontrast, žádná složitá gesta, žádné časově omezené akce.
- Uživatelské rozhraní je v češtině, texty v Android resources (lokalizace připravená).
- Všechny rozměry, fonty a barvy se berou z tématu (theme), aby se dalo vzhled později snadno měnit.

## Funkce

### Administrátor
- Přidat, editovat a smazat slovíčko.
- Vytvořit, editovat a smazat složku. Smazání neprázdné složky je rekurzivní a vyžaduje potvrzení s počtem dotčených položek. Koš neexistuje.
- Přesunout slovíčka mezi složkami, i více najednou.
- Přesunout složky mezi složkami, i více najednou.
- Ručně nastavit pořadí slovíček a složek ve složce. Rychlé seřazení podle abecedy (kolace se zadává v konfiguraci).
- Slovíčka se nekopírují, každé slovíčko patří do jedné složky a má vlastní ID. Titulky nemusí být jedinečné.
- Obrázek slovíčka: výběr ze zařízení nebo vyfocení. Obrázky se při uložení zmenší (například na 512 px).
- Vlastní zvuk slovíčka: výběr MP3 nebo M4A souboru ze zařízení, nebo nahrání mikrofonem přímo v editaci slovíčka (nahrávka je M4A; omezená délka, řádově desítky sekund; oprávnění k mikrofonu se žádá až při prvním nahrávání v administraci, nahrává se jen v popředí a na výslovný pokyn administrátora).
- Náhled zvuku slovíčka v administraci.
- Export složky nebo celého datasetu pro pozdější import.
- Import dříve exportovaného bloku, celého nebo jen vybraných složek či podsložek.
- Konfigurace aplikace (viz níže).

### Klient
- Přidání slovíčka do věty (klepnutím nebo přetažením, podle konfigurace). Zahrnuje procházení složkové struktury a rychlou navigaci: drobečková navigace (breadcrumbs) s velkými tlačítky „Zpět“ a „Domů“.
- Odebrání slovíčka z věty: podržením (interval v konfiguraci), případně odtažením slova pryč z horního řádku při režimu drag & drop.
- Změna pořadí slov ve větě posunováním vlevo a vpravo na stavovém řádku (lze v konfiguraci zakázat).
- Smazání celé věty.
- Přehrání celé věty, s možností přehrávání zastavit (tlačítko Přehrát se při přehrávání mění na Stop). Věta po přehrání zůstává (lze ji přehrát znovu) a maže se tlačítkem, pokud není v konfiguraci zapnuto automatické smazání.
- Rozpracovaná věta se po restartu aplikace neobnovuje.
- Ve větě se slovíčko zobrazuje jako menší dlaždice s obrázkem a titulkem (slovíčko bez obrázku jen jako text). Titulek lze v konfiguraci skrýt.

### Konfigurace (nastavuje administrátor)
Konfigurace je **lokální pro každé zařízení** a nesynchronizuje se přes Google Drive (každé zařízení může mít jiné zobrazení). Výchozí hodnoty jsou v závorkách.
- Způsob přidání slova do věty: klepnutí, tažení (klepnutí).
- Povolení změny pořadí slov ve větě (povoleno).
- Interval podržení pro odebrání slova (1 s).
- Přehrání slova při klepnutí na ně (vypnuto).
- Velikost mřížky (3×3 na výšku, 4×3 na šířku).
- Kolace pro abecední řazení (čeština).
- Skrytí titulků slovíček ve větě (zobrazeny).
- Automatické smazání věty po přehrání (vypnuto).

## Přehrávání věty

Věta se přehrává sekvenčně. Sousední slovíčka čtená pomocí TTS se spojují do jednoho textu pro plynulejší intonaci. Když věta narazí na slovíčko s vlastním MP3, TTS se přeruší, přehraje se MP3 a poté se pokračuje. Jazyk je výchozí čeština, hlas dle TTS enginu zařízení. TTS a přehrávání MP3 jsou schované za společné rozhraní (například `SpeechProvider`), aby šly později přidat další poskytovatele hlasů.

## Architektura

Aplikace běží na OS Android (minimálně 8.0, API 26), je **offline-first** a Google Drive slouží jako záloha a synchronizace mezi zařízeními.

### Technologie
- Nativní Android, **Kotlin**, **Jetpack Compose**, Material 3. (Kotlin zůstává, protože Compose je dostupný jen v Kotlinu.)
- Architektura MVVM / MVI s jasným rozdělením vrstev (UI, doména, data). Cílem je přehledná a srozumitelná architektura.
- Hilt pro DI, Kotlin Coroutines a Flow.
- Lokální databáze Room, strom složek se ukládá tak, že každá položka zná ID svého rodiče.
- Pokud některá knihovna bude vyžadovat novější Android než 8.0, je potřeba to zjistit a dotázat se.
- Testy se píší tak, aby zajistily požadované chování: unit testy domény a repozitářů, UI testy Compose pro klíčové scénáře klienta.
- Distribuce zatím interně (APK / interní testování), Google Play až později.
- Identifikátor aplikace (applicationId): `cz.talkative.app`. Lze později změnit, ale po zavedení Google přihlášení nebo publikaci je změna pracnější.
- Cílové zařízení: obecný Android tablet (konkrétní model a verze budou upřesněny).

### Úložiště a synchronizace
- Primární úložiště je lokální (Room + soubory). Aplikace plně funguje offline.
- Na Google Drive se ukládá **všechno** (metadata jako JSON i média: obrázky a MP3), aby se po instalaci na jiném zařízení data na základě účtu sama dotáhla. Používá se skrytá složka aplikace (`drive.appdata`). Výjimkou je konfigurace zobrazení, která zůstává lokální.
- Google účet vybere administrátor přes systémový dialog při prvním spuštění (nebo v administraci). Do té doby aplikace funguje jen lokálně. Výhledově možnost vázat Drive na přihlášeného uživatele.
- Synchronizace probíhá automaticky po každé změně v administraci (když je síť), při startu aplikace a ručně tlačítkem v administraci. Při chybě sítě se pokus později opakuje.
- Smazané položky se krátce pamatují jako „smazané“ (s časem), aby se smazání přeneslo na ostatní zařízení. U konfliktu (smazáno vs. upraveno) vyhrává novější změna.
- Výkřičník u slovíčka se zobrazí, když chybí soubor obrázku nebo MP3, když slovíčko odkazuje na neexistující složku, nebo když při synchronizaci vznikl konflikt, který nebylo možné bezpečně vyřešit. Po opravě administrátorem zmizí.
- Se stejným Google účtem může hypoteticky pracovat více instalací. Zápisy na Drive proto musí být hlídané: aplikace nezajišťuje plnou konzistenci, ale musí být připravená na to, že se data mohou rozsypat, a umět se z toho zotavit.
- Konflikty: **last write wins**. Pokud vznikne nekonzistence, zobrazí se u slovíčka zvýraznění (výkřičník) a administrátor si to případně opraví sám.

### Export a import
- Formát volí implementace: ZIP archiv s `manifest.json` (obsahuje verzi schématu) a médii v adresářích.
- Ukládání a výběr souborů přes systémový výběr souborů (Storage Access Framework).
- Import vždy jen přidává (nová ID), nic nepřepisuje. Volitelně lze nahradit celý slovník.
- Částečný import: uživatel zvolí cílovou složku, výchozí je kořen.
- Export obsahuje složky, slovíčka a jejich média. Konfigurace je volitelná (zaškrtávátko).

### Soukromí
Data zůstávají na zařízení a v soukromém Drive uživatele, žádné vlastní servery. Uživatel je informován, co se ukládá.

## Budoucí funkcionality

Podrobný seznam je v `todo.md`. Architektura s nimi počítá již nyní:

- Různé hlasy od různých hlasových poskytovatelů nebo generátorů hlasů (`SpeechProvider`).
- Změna velikosti fontů různých prvků, vizuálních barev a podobně.
- Ukládání historie přehraných vět: délku historie nastavuje administrátor, lze ji na požádání smazat. Slouží k analýze pokroku externím subjektem na základě exportu, který obsahuje jen čas a poskládanou větu.
- Předdefinované oblíbené fráze a slova (administrátor definuje, klient používá). Fráze je posloupnost slovíček, například „dobré ráno, jak se máš“ na jedno klepnutí. V klientském rozhraní se zobrazují například ve sbalovacím panelu vlevo.
