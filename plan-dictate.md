# Plán rozšíření: Nahrání vlastního zvuku mikrofonem („nadiktování“)

Rozšíření kroku 5 z `plan.md`. Dnes se vlastní zvuk slovíčka vybírá jen jako hotový MP3 soubor ze zařízení. Tento plán přidává možnost zvuk **nadiktovat**: administrátor v editaci slovíčka stiskne Nahrávat, řekne slovo či frázi, nahrávku si poslechne a uloží ji jako vlastní zvuk slovíčka. Dál se chová stejně jako vybraný soubor (přednost před TTS, přehrání ve větě, export/import). Postupuje se stejně jako v `plan.md` (podkroky, Úkoly + Kritéria, po každém podkroku samostatný commit a push se zaškrtnutím zde).

Nejde o převod řeči na text (rozpoznávání řeči); nahrává se přímo zvuk.

## Přehled postupu

- [x] Krok D.1: Formát nahrávky a úprava úložiště zvuku
- [x] Krok D.2: Nahrávání mikrofonem (doménová a datová vrstva)
- [x] Krok D.3: Nahrávání v editaci slovíčka (UI)
- [ ] Krok D.4: Export/import, dokumentace a ruční ověření

## Zda a za jakých podmínek to lze

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

## Obecná pravidla

- Platí „Obecná pravidla pro všechny kroky“ z `plan.md` (testy, texty v češtině v `strings.xml`, rozměry z tématu, jednoduchý kód).
- Čistá logika (stavový automat nahrávání, kontrola limitů) je bez závislosti na Androidu a testuje se jednotkově s falešným nahrávačem (`FakeAudioRecorder` v `src/sharedTest/java`, jako `FakeAudioPlayer`).
- Pokud by knihovna nebo funkce vyžadovala minSdk vyšší než 26, krok se zastaví a zeptá se (`MediaRecorder` s AAC/MP4 je dostupný od API 10; `MediaRecorder(Context)` od API 31 se volá jen na nových verzích, na starších se použije bezparametrový konstruktor).

---

## Krok D.1: Formát nahrávky a úprava úložiště zvuku

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

## Krok D.2: Nahrávání mikrofonem (doménová a datová vrstva)

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

## Krok D.3: Nahrávání v editaci slovíčka (UI)

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

## Krok D.4: Export/import, dokumentace a ruční ověření

**Cíl:** Nahrávky se správně exportují a importují, dokumentace odpovídá a funkce je ověřena na skutečném zařízení.

**Úkoly:**
- [ ] Export/import: ověřit, že `ArchiveReader`, `ExportDictionaryUseCase` a `ImportArchiveUseCase` nepočítají s příponou `.mp3` a že import M4A prochází kontrolou z D.1; úprava `format-archivu.md` (adresář `audio/` obsahuje MP3 nebo M4A; archivy se starým obsahem zůstávají platné, verze schématu se nemění).
- [ ] Aktualizovat `CLAUDE.md` (sekce „Current state“: záznam z mikrofonu, `AudioRecorder`, `cache/recordings`, M4A), `assignment.md` (vlastní zvuk: MP3 nebo nahrávka mikrofonem, oprávnění) a `plan.md` (odkaz na tento plán u kroku 5).
- [ ] Dopsat k informacím o soukromí v `plan.md` krok 11.2: aplikace nahrává mikrofonem jen na výslovný pokyn administrátora.
- [ ] Ruční ověření na skutečném tabletu: žádost o oprávnění, zamítnutí a následné udělení, nahrání krátkého i minutového zvuku, poslech náhledu, přehrání ve větě poslechem (emulátor zvuk neověří), nahrání při přerušení (zamčení displeje, příchozí hovor, odchod do pozadí).

**Kritéria splnění:**
- [ ] Round-trip test: slovník s nahrávkou M4A → export → smazání → import vrátí slovo se stejným zvukem.
- [ ] Starý archiv s MP3 se importuje beze změny.
- [ ] Dokumentace odpovídá kódu.
- [ ] Ruční ověření zvuku člověkem poslechem je zapsáno do `plan.md` (stejně jako u kroku 5.3: u položek, které emulátor neověří, poznámka, co zbývá).
