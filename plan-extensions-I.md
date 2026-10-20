# Plán rozšíření I: About, piktogramy ARASAAC a kreslení ikon

Doplněk k `plan.md` (ten se tímto plánem neupravuje). Platí stejná pravidla práce jako tam: podkroky pro jednu session, **Úkoly** a **Kritéria**, zaškrtávání `- [x]`, po každém podkroku zelené testy a samostatný commit (zpráva s číslem podkroku, např. „Krok E2.1: …“), texty česky v `strings.xml`, rozměry a barvy z tématu, dotykové plochy alespoň 64 dp, jednoduchý čitelný Kotlin, testy ověřující skutečné chování. Výsledné věci se po dokončení promítnou do `CLAUDE.md` (sekce „Current state“) a `todo.md` (položka č. 6 „Knihovna symbolů“ se tím vyřeší; sloupec `Priorita` se nevyplňuje).

Kroky jsou označené **E1–E3** (Extensions), aby se nepletly s kroky `plan.md`. Nezávisí na krocích 9–11, lze je dělat kdykoli. Pořadí: E1 (About) jde první, protože uvedení autora ARASAAC musí být v aplikaci dřív, než se z ARASAAC začne stahovat.

## Přehled postupu

- [x] Krok E1: Obrazovka „O aplikaci“ (About)
- [x] Krok E1a: Obrazovka "O aplikaci" se musí zobrazit při prvním spuštění aplikace a uživatel ji musí "odsouhlasit".
- [ ] Krok E2: Piktogramy ARASAAC (hledání a výběr uživatelem)
- [ ] Krok E3: Kreslení ikon prstem

## Rozhodnutí a předpoklady

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
- **Soukromí:** na server ARASAAC odchází jen hledaný text a ID stahovaného piktogramu (a technické údaje jako IP adresa). Nic z dat uživatele se neodesílá. Zapsat do informace o soukromí (viz `plan.md` 11.2) a do About.

---

## Krok E1: Obrazovka „O aplikaci“ (About)

**Cíl:** V administraci je obrazovka s informacemi o aplikaci a s uvedením zdroje a licence piktogramů ARASAAC.

### E1.1 Obrazovka About a navigace

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

### E1a Úvodní oznámení při prvním spuštění

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

## Krok E2: Piktogramy ARASAAC

**Cíl:** Administrátor v editaci slovíčka napíše hledané slovo, uvidí nabídku piktogramů ARASAAC, klepnutím vybere jeden a ten se stane obrázkem slovíčka. Nic se nevybírá automaticky.

### E2.1 Síťová vrstva a vyhledávání

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

### E2.2 Stažení vybraného piktogramu

**Úkoly:**
- [x] `PictogramDownloader` (rozhraní v doméně, OkHttp implementace): stáhne `{id}_500.png` do dočasného souboru v `cache/pictograms`, omezí velikost odpovědi (např. 2 MB), při chybě nebo přerušení soubor smaže.
- [x] `ImportPictogramUseCase`: stáhne piktogram a předá ho do `ImageStore.saveFromUri`/`saveFromStream` (zmenšení, uložení do `filesDir/images`), dočasný soubor smaže vždy (i při chybě) a vrátí `ImageSaveResult` nebo chybu sítě. _(samostatný use case nevznikl: podle části „Rozhodnutí a předpoklady“ stažený soubor z cache/pictograms jde jako URI do AdminEvent.ImagePicked a ukládání (zmenšení, ImageStore, úklid) dělá stávající processPickedImage. Soubor se maže před dalším stažením a při startu aplikace, ne hned po zpracování)_
- [x] Úklid `cache/pictograms` při startu aplikace (vedle stávajícího úklidu `cache/camera`).

**Kritéria splnění:**
- [x] Unit testy (`MockWebServer` + falešný `ImageStore`): úspěch uloží obrázek a smaže dočasný soubor; chyba sítě, HTTP chyba i příliš velká odpověď neuloží nic a nezanechají soubor v `cache`. _(testy dočasného souboru jsou v OkHttpPictogramDownloaderTest (MockWebServer), test zachování PNG s průhledností v FileImageStoreTest)_
- [x] Uložený obrázek je PNG s průhledností (nebyl převeden na JPEG s černým pozadím).

### E2.3 Obrazovka hledání a výběru

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

### E2.4 Napojení do editace slovíčka

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

### E2.5 Uvedení autora a licence v exportním archivu

Rozhodnutí zadavatele: autor a licence ARASAAC se do exportního ZIPu přidají jako **text v ZIPu**; slovíčka se jako „z ARASAAC“ neznačí, takže soubor je v každém exportu (aplikace neví, zda slovník piktogramy ARASAAC obsahuje, a text to říká obecně). Nutné až po E1.1 (znění textu).

**Úkoly:**
- [ ] Nová položka archivu `LICENSE-ARASAAC.txt` v kořeni ZIPu (konstanta v `ArchiveLayout` vedle `manifest.json`), UTF-8, zapisuje ji `ExportDictionaryUseCase` hned po `manifest.json`. Schéma `manifest.json` se **nemění** (verze schématu zůstává), starší verze aplikace položku při importu ignorují jako neznámou.
- [ ] Obsah souboru: doslovné krátké znění uvedení z E1.1 („Autor piktogramů: Sergio Palao. Původ: ARASAAC (http://www.arasaac.org). Licence: CC (BY-NC-SA). Majitel: vláda Aragonu (Španělsko)“), věta, že archiv může obsahovat piktogramy ARASAAC a že se musí šířit pod licencí CC BY-NC-SA nekomerčně a se stejným uvedením, odkazy na https://arasaac.org, podmínky použití a text licence. Jeden zdroj textu: řetězce ze `strings.xml` z E1.1, do use case se text předá přes malé rozhraní (např. `ArchiveNotice`) s implementací v `data`/`di`, aby doména nezávisela na Androidu.
- [ ] Import: `ArchiveReader`/`ImportArchiveUseCase` položku `LICENSE-ARASAAC.txt` ignorují (nepočítá se do validace jako neznámý soubor ani jako chyba, nic se z ní nezapisuje); ověřit, že stávající chování (`ArchiveLimits.maxBytes` = `null` → přeskočit) to splňuje, a případně položku povolit výslovně.
- [ ] Obrazovka exportu (`ui/exchange`): krátká poznámka, že se do archivu přidá soubor s uvedením autora a licencí ARASAAC a že archiv s piktogramy ARASAAC lze šířit jen nekomerčně pod stejnou licencí.
- [ ] Zdokumentovat v `format-archivu.md` (nová volitelná textová položka, ignorovaná při importu, žádná změna `manifest.json`) a upravit tam i případné tvrzení, že archiv obsahuje jen `manifest.json` a média.
- [ ] Upravit existující testy archivu z kroku 8, které předpokládají přesný seznam položek ZIPu.

**Kritéria splnění:**
- [ ] Unit test exportu: ZIP obsahuje `LICENSE-ARASAAC.txt` s očekávaným textem (shoda s řetězci z E1.1, správně uložená čeština v UTF-8, i při exportu prázdného slovníku a jedné složky), vedle `manifest.json` a médií.
- [ ] Round-trip export → smazání dat → import vrátí identický slovník a položka s licencí nic nezmění (nevytvoří slovíčko, soubor ani chybu); archiv bez této položky (starý export) se importuje stejně.
- [ ] Žádný výstup exportu nemá nové pole v `manifest.json` (test porovnává výstup se stávajícím schématem) a `ManifestCodec` zůstává beze změny.
- [ ] Ruční ověření: exportovaný ZIP otevřený na počítači obsahuje čitelný `LICENSE-ARASAAC.txt`.

---

## Krok E3: Kreslení ikon prstem

**Cíl:** Administrátor v editaci slovíčka nakreslí jednoduchou ikonu prstem (štětec s nastavitelnou tloušťkou, guma, výplň, zpět) a ta se uloží jako obrázek slovíčka. Pozadí je vždy bílé.

### E3.1 Jádro kreslení (bez UI)

**Úkoly:**
- [ ] Balíček `domain/drawing`, bez závislosti na Compose, aby šel testovat na JVM, kde to jde (`Bitmap` lze v JVM testech nahradit vlastním polem pixelů `IntArray`; operace pracují nad rozhraním/pixelovou mřížkou, ne přímo nad `android.graphics.Bitmap`):
  - `DrawingCanvas`: 512 × 512 pixelů, vždy inicializované bílou barvou, neprůhledné.
  - `DrawingTool` (`BRUSH`, `ERASER`, `FILL`), `DrawingColor` (paleta cca 8 barev z tématu včetně černé; barva se ukládá jako hodnota, ne podle tématu, aby výsledek byl stejný všude).
  - Tah štětcem/gumou: spojení bodů úsečkami s kulatými konci a zadanou šířkou (`BrushWidth` s mezemi v konstantách, např. 4–64 px); guma = totéž bílou. Samotné klepnutí bez posunu nakreslí tečku.
  - Výplň `floodFill(x, y, color, tolerance)`: scanline algoritmus (bez rekurze, nepadá na zásobníku) nad pixely ohraničenými oblastmi podobné barvy; tolerance kvůli vyhlazeným hranicím; výplň stejnou barvou, jakou už pixel má, nedělá nic. Souřadnice mimo plátno se ignorují.
  - Historie pro Zpět: zásobník snímků (nejvýše 20), operace `push` před každou akcí (jeden tah od přiložení po zvednutí prstu = jedna akce), `undo()` vrací poslední snímek, `isModified` (je na plátně něco jiného než bílá).
  - Převod na PNG (`toPng()`/zápis do souboru v `cache/drawings`).
- [ ] Výpočet výplně a vykreslení do bitmapy běží mimo hlavní vlákno (`Dispatchers.Default`) a UI během výplně zobrazuje krátkou indikaci; více akcí se nezpracovává souběžně.

**Kritéria splnění:**
- [ ] Unit testy: nové plátno je celé bílé; tah zbarví pixely podél úsečky a nic mimo ni; širší štětec zbarví víc pixelů; guma vrací bílou; klepnutí vytvoří tečku; výplň zaplní jen souvislou oblast uvnitř uzavřeného tvaru a ne oblast za čárou; výplň mezi čárou a pozadím se tolerancí dostane i k vyhlazeným okrajům; výplň na plném plátně (nejhorší případ) doběhne rychle a bez pádu; výplň stejnou barvou nic nezmění; nejvýše 20 kroků Zpět, Zpět po 21. akci vrátí 20., ne původní stav; Zpět vrátí i výplň; `isModified` po Zpět na prázdné plátno je `false`.
- [ ] Měření: výplň celého plátna 512 × 512 trvá na emulátoru řádově desítky ms (hodnotu zapsat do poznámky podkroku).

### E3.2 Obrazovka kreslení

**Úkoly:**
- [ ] Trasa `draw` a `DrawViewModel` (stav plátna, zvolený nástroj, barva, šířka; přežije otočení tabletu).
- [ ] `DrawScreen`: čtvercové plátno zobrazené v největší možné velikosti (na šířku vedle něj panel nástrojů, na výšku pod ním), tahy prstem přes `pointerInput` (`detectDragGestures` nebo vlastní zpracování, aby klepnutí bez posunu nakreslilo tečku), souřadnice se přepočítávají z velikosti zobrazení do 512 × 512.
- [ ] Panel nástrojů (všechny prvky alespoň 64 dp, vysoký kontrast, vybraný nástroj a barva jasně označené): **Štětec**, **Guma**, **Výplň**, paleta barev, **posuvník tloušťky** s živým náhledem velikosti štětce (kolečko), **Zpět**, **Smazat vše** (s potvrzením, protože jde o hromadné zahození; samo se dá vrátit přes Zpět), **Uložit**, **Zavřít**.
- [ ] Posuvník se nezobrazuje ani nezablokuje u Výplně (nemá smysl), nebo je neaktivní.
- [ ] Texty a popisky (`contentDescription`) v `strings.xml`.

**Kritéria splnění:**
- [ ] View model testy: změna nástroje/barvy/tloušťky, tah vytvoří akci, Zpět, Smazat vše, přežití „otočení“ (obnovení view modelu zachová plátno).
- [ ] Compose UI test: tah přes plátno změní plátno (`isModified`), tlačítka Zpět a Uložit jsou neaktivní na prázdném plátně a po prvním tahu aktivní, výběr nástroje je označený.
- [ ] Na emulátoru: kreslení prstem je plynulé na tabletu na výšku i na šířku, otočení během kreslení nesmaže kresbu.

### E3.3 Uložení, zahození a napojení do editace

**Úkoly:**
- [ ] **Uložit:** zapíše plátno jako PNG do `cache/drawings`, vrátí URI do editace jako `AdminEvent.ImagePicked(uri)` (viz „Rozhodnutí a předpoklady“) a zavře obrazovku. Prázdné (celé bílé) plátno se uložit nedá.
- [ ] **Zahodit rozkreslené:** Zavřít nebo systémové Zpět na změněném plátně (`isModified`) se zeptá „Zahodit kresbu?“ (Zahodit / Pokračovat v kreslení); na nezměněném plátně zavře bez dotazu. Nic se nikam neukládá, po zavření ani po restartu aplikace po rozkreslené kresbě nezůstane stopa (`ViewModel` se zahodí s trasou, `cache/drawings` se při startu uklízí jako `cache/camera`).
- [ ] V `ImageSection` tlačítko „Nakreslit“ vedle galerie, fotoaparátu a ARASAAC (jen u slovíčka, ne během zpracování obrázku). Rozepsaná editace slovíčka zůstane po návratu beze změny.
- [ ] Nakreslená ikona se uloží stejnou cestou jako ostatní obrázky (zmenšení nic nemění, protože je 512 px; PNG bez průhlednosti se uloží jako JPEG, to je v pořádku, protože pozadí je bílé).
- [ ] Aktualizace `CLAUDE.md` a `todo.md`.

**Kritéria splnění:**
- [ ] Scénář na emulátoru: nové slovíčko → Nakreslit → kresba štětcem, gumou a výplní → Uložit → uložit slovíčko → ikona je v administraci i v klientovi, přežije restart.
- [ ] Zavření rozkreslené kresby se zeptá a po potvrzení se zahodí beze stopy (v `cache/drawings` nic nezůstane, v `filesDir/images` žádný nový soubor); zavření nezměněného plátna se neptá.
- [ ] Výměna už přiřazeného obrázku kresbou a pak zrušení editace nenechá osiřelý soubor.
- [ ] Nakreslená ikona se exportuje a importuje jako jakýkoli obrázek.
- [ ] Instrumentovaný test celého napojení (kresba → editace → obrázek slovíčka).

---

## Rozhodnuté a otevřené otázky

- **Rozhodnuto – export piktogramů ARASAAC:** podmínky říkají, že odvozená díla i „materiály na nich založené“ se šíří pod stejnou licencí a s uvedením autora. Zadavatel rozhodl: uvedení autora a licence se do exportního archivu dává jako **textový soubor v ZIPu** (E2.5, bez změny `manifest.json`), slovíčka se jako „z ARASAAC“ **neznačí**. Doplňuje to věta v About (E1.1).
- **Otevřené – komerční použití:** podmínky komerční použití vylučují. Pokud se aplikace někdy bude prodávat nebo vydávat veřejně za úplatu, je nutné buď získat písemný souhlas ARASAAC, nebo ARASAAC z aplikace odstranit.
