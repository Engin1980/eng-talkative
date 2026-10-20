# Formát archivu pro export a import

Archiv je běžný ZIP soubor. Implementace: `domain/exchange` (`ArchiveManifest`, `ManifestCodec`).

## Obsah

```
manifest.json      popis slovníku (vždy)
images/<soubor>    obrázky slovíček (jen použité)
audio/<soubor>     vlastní MP3 slovíček (jen použité)
```

Názvy souborů v `images/` a `audio/` jsou holé názvy bez cesty; slovíčko na ně odkazuje z `manifest.json`.

## manifest.json

```json
{
  "schemaVersion": 1,
  "folders": [
    { "id": "f1", "title": "Jídlo", "sortOrder": 0 },
    { "id": "f2", "title": "Ovoce", "parentId": "f1", "sortOrder": 0 }
  ],
  "words": [
    {
      "id": "w1", "title": "chci", "sortOrder": 1,
      "imageFile": "a.jpg", "alternateText": "já chci", "audioFile": "b.mp3"
    }
  ],
  "config": { "addWordMode": "TAP", "gridPortrait": { "columns": 3, "rows": 3 } }
}
```

- `schemaVersion` je povinná. Aplikace odmítne archiv s vyšší verzí, než zná.
- Složky a slovíčka jsou ploché seznamy. `parentId` odkazuje na složku v témže archivu, chybí-li, jde o kořen archivu. `sortOrder` je společné pořadí složek i slovíček uvnitř jedné složky.
- `id` slouží jen k vazbám uvnitř archivu. Při importu dostane každá položka nové ID, takže import nikdy nic nepřepíše.
- `imageFile`, `alternateText`, `audioFile` jsou nepovinné. Chybějící hodnota se v souboru vynechává.
- `config` je jen tehdy, když uživatel při exportu zaškrtl, že ho chce. Obsahuje nastavení z obrazovky konfigurace; hodnoty mimo povolené meze se při importu srazí na nejbližší povolenou.
- Neznámá pole se ignorují (starší aplikace přečte archiv se stejnou verzí schématu), neznámá hodnota výčtu se nahradí výchozí.

## Co archiv neobsahuje

Žádný PIN ani přihlašovací údaje, žádný účet Google, žádné časy změn ani smazané položky (tombstony), ID profilu ani rozpracovanou větu. Hlídá to test `ManifestCodecTest.archive_containsOnlyAllowedKeys_...`.

## Kontrola při čtení

Archiv se před zápisem čehokoli přečte a zkontroluje (`ManifestCodec.decode`). Odmítne se, když:

- `manifest.json` není JSON v očekávaném tvaru (`NOT_AN_ARCHIVE`),
- má vyšší verzi schématu (`NEWER_VERSION`) nebo verzi menší než 1 (`INVALID_VERSION`),
- se opakuje ID (i mezi složkou a slovíčkem), chybí titulek, odkazuje na neexistující složku, obsahuje cyklus složek, nebo je název souboru s lomítkem či `..` (`INCONSISTENT`).

## Import

Import archiv nejdřív celý zkontroluje (nic nezapisuje), pak zkopíruje média do úložišť aplikace a nakonec v jedné transakci zapíše složky a slovíčka s novými ID. Každé slovíčko dostane vlastní kopii souboru, i když na stejný soubor v archivu odkazuje víc slovíček. Při jakékoli chybě se zkopírovaná média smažou a data zůstanou beze změny. Obrázky a MP3 z archivu procházejí stejnou kontrolou jako při ruční volbě v administraci (čitelný obrázek, MP3 do 60 s a 5 MB). Chybějící, nečitelná nebo příliš velká položka média znamená odmítnutí celého archivu. Meze velikosti položek: manifest 8 MB, obrázek 10 MB, zvuk 5 MB, nejvýše 50 000 položek.

Pořadí zůstává zachované (uvnitř složky se přečísluje na 0, 1, 2, ...). Importované položky nejvyšší úrovně se přidají na konec cílové složky.
