# Synchronizace s Google Drive: struktura dat a pravidla slučování

Platí pro krok 9 z `plan.md`. Synchronizace zálohuje slovník na Google Drive uživatele a dotahuje ho na dalším zařízení se stejným účtem. Zdrojem pravdy zůstává místní databáze a soubory (offline-first). Konfigurace zařízení se **nesynchronizuje**.

## Co a kam se ukládá

Používá se skrytá složka aplikace `appDataFolder` (oprávnění `https://www.googleapis.com/auth/drive.appdata`). Uživatel ji v Disku nevidí a aplikace nemá přístup k žádným jiným souborům na Disku. Složka se v API vybírá parametrem `spaces=appDataFolder` a rodičem `appDataFolder`.

| Soubor na Drive | Obsah |
|---|---|
| `dictionary.json` | metadata: všechny složky a slovíčka včetně smazaných (tombstonů) |
| `img_<název>` | obrázek slovíčka (název je název souboru v `filesDir/images`) |
| `aud_<název>` | vlastní zvuk slovíčka, MP3 nebo M4A (název souboru v `filesDir/audio`) |

Drive v `appDataFolder` dovoluje víc souborů se stejným názvem. Aplikace proto při načtení `dictionary.json` sloučí všechny kopie, které najde, ponechá jednu a ostatní smaže. Média se hledají podle názvu; duplicitní kopie téhož názvu se ignorují (obsah je stejný, viz níže).

### `dictionary.json`

```json
{
  "schemaVersion": 1,
  "items": [
    {"id": "…", "kind": "FOLDER", "profileId": "default", "title": "Jídlo", "parentId": null,
     "sortOrder": 0, "updatedAt": 1790000000000, "deleted": false},
    {"id": "…", "kind": "WORD", "profileId": "default", "title": "Chléb", "parentId": "…",
     "sortOrder": 1, "updatedAt": 1790000000500, "deleted": false,
     "imageFile": "….png", "alternateText": "chleba", "audioFile": "….m4a"}
  ]
}
```

- `kind` je `FOLDER` nebo `WORD`; `imageFile`, `alternateText` a `audioFile` mají jen slovíčka (`null` nebo chybí = nemá).
- `updatedAt` jsou milisekundy od epochy, ukládají se beze změny z místní databáze. Synchronizace čas nikdy nepřepisuje.
- `deleted: true` je tombstone: položka byla smazána v čase `updatedAt`. Smazané slovíčko nemá odkazy na média.
- Neznámá pole se při čtení ignorují. Soubor s novější `schemaVersion` než umí aplikace se odmítne (`NEWER_VERSION`), nic se nepřepíše.

### Média

Název souboru média se po uložení nikdy nemění (každé uložení dostane nové UUID). Stejný název proto znamená stejný obsah a synchronizace médií je jen rozdíl množin názvů: co je jen místně, se nahraje; co je jen na Drive, se stáhne. Média se přenášejí před metadaty, takže jiné zařízení nikdy nevidí odkaz na soubor, který ještě na Drive není.

## Pravidla slučování (last write wins)

Pro každé `id` se vezme verze s novějším `updatedAt`. Při shodě času vyhrává smazání, pak menší obsahový klíč (pevné pořadí polí), aby oba konce vždy vybraly totéž. Sloučení je proto:

- **komutativní** (místní + vzdálené = vzdálené + místní),
- **idempotentní** (sloučení výsledku s kteroukoli stranou nic nezmění), takže opakovaná synchronizace bez změn nic nezapisuje.

Důsledky:

- Nová položka na jedné straně se objeví na obou.
- Souběžné úpravy téže položky: vyhraje pozdější zápis, druhá se ztratí.
- **Smazání proti úpravě:** vyhrává novější změna, protože tombstone má čas smazání jako každá jiná změna.
- Pořadí (`sortOrder`) se řeší po položkách, takže po sloučení se mohou dvě položky ve složce sejít na stejném pořadí; ve výpisu se pak řadí podle `id`.

### Úklid tombstonů

Tombstone starší než **30 dní** se po sloučení zahodí na obou stranách (z místní databáze i z `dictionary.json`). Zařízení, které bylo offline déle než 30 dní a má smazanou položku ještě jako živou, ji po připojení vrátí; to se bere jako přijatelné.

### Úklid médií

- Místně: po použití vzdálených změn se smažou soubory, na které už žádné slovíčko neodkazuje (stejný úklid jako při startu aplikace).
- Na Drive: nepoužitý soubor se smaže až po 7 dnech od poslední změny, protože ho mohlo nahrát jiné zařízení, které ještě nestihlo zapsat metadata.

## Víc zařízení a souběh

Synchronizace je „stáhnout, sloučit, nahrát“. Dvě zařízení, která synchronizují současně, si mohou přepsat `dictionary.json`; vzdálená kopie je ale vždy sloučením, takže přepsaná změna zůstává v místní databázi autora a při další synchronizaci se vrátí (je novější než vzdálená verze). Aplikace nezaručuje okamžitou konzistenci, jen že se zařízení časem sejdou a nic se kvůli souběhu nerozbije.
