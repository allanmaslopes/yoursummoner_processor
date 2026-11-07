# Runas, feitiços e itens por runa principal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publicar, por campeão e posição, uma recomendação de elo alto sempre presente: página de runas completa (com fragmentos), dupla de feitiços e caminho de itens por runa principal, no pacote `formatVersion` 2.

**Architecture:** O coletor passa a gravar `runes.statPerks` (aditivo, `schemaVersion` 2). O processor lê runas e feitiços no `MatchRecord`, leva-os no `ParticipantView`, e o `KnowledgeAggregator` passa a publicar toda combinação com ≥ 1 partida, marcando `lowSample` em vez de esconder. O REST e o Mongo do processor serializam os campos novos.

**Tech Stack:** Java 25, Spring Boot 4, Spring Data MongoDB, JUnit 5/AssertJ, ArchUnit.

**Spec:** `docs/superpowers/specs/2026-11-07-runas-e-itens-design.md` (ler inteiro antes de cada task).

## Global Constraints

- Hexagonal: `domain` sem Spring, Jackson (`tools.jackson..`, `com.fasterxml..`) ou Mongo; `ArchitectureTest` dos dois serviços deve passar.
- Contrato de partidas: só aditivo. `runes.statPerks` = objeto `{offense, flex, defense}` (ints, como `perks.statPerks` da Riot match-v5). `schemaVersion` continua `2`. Documento sem o campo → `null`.
- Nunca guardar nem publicar identificador de jogador.
- `processor.min-games` = 30 e `processor.min-occurrences` = 10 definem `lowSample` (`games < limite`), não filtram as partes novas.
- Campos v1 do pacote mantêm os filtros atuais.
- Página igual = mesmos `primaryStyle`, `subStyle` e as 6 `perks` em ordem; runa principal = `perks[0]`.
- Desempate: mais partidas; depois maior taxa de vitória; depois ordem lexicográfica crescente (`(primaryStyle, subStyle, perks…)` para páginas; ids para feitiços e runas).
- Dupla de feitiços normalizada: menor id primeiro.
- Windows: testes já usam `-Djdk.net.unixdomain.tmpdir` no surefire; não mexer.
- Sandbox do Codex: Maven offline (`-o -Dmaven.repo.local=D:\Workspace\Languages-Frameworks\m2-repository -Daether.syncContext.named.factory=noop`); NÃO rodar git — o arquiteto commita.

## Review Focus

1. Participante com `perks` vazio ou tamanho ≠ 6 (remake, dado corrompido): conta em `games`/`wins` da entrada, mas fica fora de runas e páginas; não lança exceção. → teste `incompleteRunesAreSkippedButCounted` (Task 3).
2. Entrada com 1 partida: entrada publicada, `lowSample=true`, `primary` não nulo, página e feitiços com `lowSample=true`. → teste `singleGameStillHasPrimary` (Task 3).
3. Feitiços `[12,4]` e `[4,12]` contam juntos. → teste `spellPairsAreOrderInsensitive` (Task 3).
4. Documento antigo sem `statPerks` misturado com novos: fragmentos da página saem só dos que têm; todos sem → `statPerks=null`. → teste `statPerksFromDocumentsThatHaveThem` (Task 3) e `oldDocumentWithoutStatPerks` (Task 2).
5. Posição vazia: participante entra só em `ANY`, e `ANY` também tem `primary`. → teste `blankPositionOnlyInAnyWithPrimary` (Task 3).

---

### Task 1: Coletor grava os fragmentos (`statPerks`)

Repositório: `D:\Workspace\yoursummoner_collector`.

**Files:**
- Modify: `src/main/java/com/your/summoner/collector/application/port/out/riot/RiotMatch.java` (`Perks`)
- Modify: `src/main/java/com/your/summoner/collector/domain/model/CollectedMatch.java` (`Runes`)
- Modify: `src/main/java/com/your/summoner/collector/application/service/MatchReducer.java` (`runes(...)`)
- Modify: adapter Mongo que grava `CollectedMatch` (se mapeia campos à mão)
- Modify: `docs/contrato-matches.md` (linha de `runes`)
- Test: `src/test/java/.../application/service/MatchReducerTest.java`, `.../adapter/out/mongo/MongoPersistenceTest.java`

**Interfaces:**
- Produces: `RiotMatch.StatPerks(int defense, int flex, int offense)`; `RiotMatch.Perks(StatPerks statPerks, List<Style> styles)`; `CollectedMatch.StatPerks(int offense, int flex, int defense)`; `CollectedMatch.Runes(int primaryStyle, int subStyle, List<Integer> perks, StatPerks statPerks)` (`statPerks` pode ser `null`). No Mongo: `participants[].runes.statPerks.{offense,flex,defense}`.

- [ ] **Step 1: Testes que falham** — `MatchReducerTest.reducesStatPerks`: participante com `perks.statPerks = {defense:5011, flex:5008, offense:5005}` → `runes().statPerks()` = `(5005, 5008, 5011)`. `MatchReducerTest.missingStatPerksIsNull`: sem `statPerks` → `null`. `MongoPersistenceTest.statPerksRoundTrip`: grava e lê de volta `runes.statPerks.offense == 5005`.
- [ ] **Step 2: Rodar e ver falhar** — `mvn -q test -Dtest=MatchReducerTest,MongoPersistenceTest` (fora do sandbox se o Mongo embarcado não subir lá; diga quais não rodaram).
- [ ] **Step 3: Implementar** nos records e em `MatchReducer.runes(...)`; atualizar a tabela de `runes` em `docs/contrato-matches.md` (`statPerks` objeto `offense`, `flex`, `defense`; ausente em partidas anteriores a 2026-11-07).
- [ ] **Step 4: Rodar e passar** — mesmo comando; depois `mvn -q verify`.
- [ ] **Step 5: Commit** (arquiteto) — `feat: coletor grava fragmentos de runas (statPerks)`.

### Task 2: Processor lê runas e feitiços

Repositório: `D:\Workspace\yoursummoner_processor`.

**Files:**
- Modify: `src/main/java/com/your/summoner/processor/domain/model/MatchRecord.java`
- Modify: `src/main/java/com/your/summoner/processor/infrastructure/adapter/out/collector/MongoMatchSource.java:90`
- Modify: `src/main/java/com/your/summoner/processor/application/service/ParticipantExtractor.java` (`ParticipantView`, linha 176)
- Test: `MongoMatchSourceUnitTest`, `ParticipantExtractorTest`

**Interfaces:**
- Produces (domain): `MatchRecord.StatPerks(int offense, int flex, int defense)`; `MatchRecord.Runes(int primaryStyle, int subStyle, List<Integer> perks, StatPerks statPerks)`; `MatchRecord.Participant(int slot, int team, int championId, String position, boolean win, List<Integer> items, List<Integer> summonerSpells, Runes runes)` — `runes` nunca `null` (sem dado → `Runes(0, 0, List.of(), null)`), `summonerSpells` nunca `null`.
- Produces (application): `ParticipantView` ganha, no fim, `List<Integer> summonerSpells, MatchRecord.Runes runes`.

- [ ] **Step 1: Testes que falham** — `MongoMatchSourceUnitTest.mapsRunesAndSpells` (documento com `summonerSpells [4,12]`, `runes {primaryStyle 8000, subStyle 8400, perks [8010,9111,9104,8299,8444,8242], statPerks {...}}` → valores no `Participant`); `MongoMatchSourceUnitTest.oldDocumentWithoutStatPerks` (sem `statPerks` → `runes().statPerks() == null`, sem `runes` → `Runes(0,0,[],null)`); `ParticipantExtractorTest.viewCarriesRunesAndSpells`.
- [ ] **Step 2: Rodar e ver falhar** — `mvn -q -o ... test -Dtest=MongoMatchSourceUnitTest,ParticipantExtractorTest`.
- [ ] **Step 3: Implementar** os records, o mapeamento no `MongoMatchSource` e a cópia no `ParticipantExtractor`; ajustar construtores usados em testes existentes.
- [ ] **Step 4: Rodar e passar** — mesmo comando; depois `ArchitectureTest`.
- [ ] **Step 5: Commit** (arquiteto) — `feat: processor lê runas e feitiços das partidas`.

### Task 3: Agregação sempre com recomendação

**Files:**
- Create: `src/main/java/com/your/summoner/processor/domain/model/KeystoneStat.java`, `RunePageStat.java`, `SpellPairStat.java`, `PrimaryRecommendation.java`
- Modify: `src/main/java/com/your/summoner/processor/domain/model/ChampionKnowledge.java`
- Modify: `src/main/java/com/your/summoner/processor/application/service/KnowledgeAggregator.java` (remover filtro `minGames` da linha 34; montar partes novas)
- Test: `src/test/java/com/your/summoner/processor/application/service/KnowledgeAggregatorTest.java`

**Interfaces:**
- Consumes: `ParticipantView.summonerSpells()`, `ParticipantView.runes()` (Task 2).
- Produces (domain records):
  - `KeystoneStat(int perk, int games, int wins, boolean lowSample)`
  - `RunePageStat(int keystone, int primaryStyle, int subStyle, List<Integer> perks, MatchRecord.StatPerks statPerks, int games, int wins, boolean lowSample, SequenceStat buildPath, boolean buildPathLowSample)` — `buildPath` pode ser `null`.
  - `SpellPairStat(List<Integer> spells, int games, int wins, boolean lowSample)`
  - `PrimaryRecommendation(int keystone, List<Integer> summonerSpells, boolean summonerSpellsLowSample)` — `summonerSpells` vazio só se nenhuma partida tiver 2 feitiços.
  - `ChampionKnowledge` ganha, no fim: `boolean lowSample, List<KeystoneStat> keystones, List<RunePageStat> runePages, List<SpellPairStat> summonerSpells, PrimaryRecommendation primary`.
- Regras (do spec): `lowSample` da entrada = `games < minGames`; das partes = `games < minOccurrences`. `keystones` e `summonerSpells` = `topN` mais frequentes, sem filtro. `runePages` = uma por keystone listada (a página completa mais frequente daquela keystone; fragmentos = os mais frequentes entre partidas dessa página com `statPerks`, senão `null`). `buildPath` = caminho próprio mais frequente (3 primeiros itens completos) da keystone; se ele tiver `< minOccurrences` e `buildPaths` geral tiver sequência com mais partidas, usa a geral; `null` se não houver nenhum caminho. `primary` = keystone mais frequente + dupla mais frequente; `primary` nunca `null` quando houver ao menos 1 participante com runas completas; se nenhum tiver, `primary = null` e `runePages` vazio (único caso).

- [ ] **Step 1: Testes que falham** (nomes exatos): `singleGameStillHasPrimary`, `pagesGroupByKeystoneWithOwnBuildPath`, `lowSampleBuildPathFallsBackToGeneral`, `pageTieBreakIsDeterministic`, `statPerksFromDocumentsThatHaveThem`, `spellPairsAreOrderInsensitive`, `incompleteRunesAreSkippedButCounted`, `blankPositionOnlyInAnyWithPrimary`, `v1FieldsKeepFilters` (entrada com 5 partidas: `starting`/`buildPaths` respeitam `minOccurrences` como hoje).
- [ ] **Step 2: Rodar e ver falhar** — `mvn -q -o ... test -Dtest=KnowledgeAggregatorTest`.
- [ ] **Step 3: Implementar** reaproveitando `sequences`/`rank`/`SEQUENCE_ORDER` existentes onde couber.
- [ ] **Step 4: Rodar e passar** — `KnowledgeAggregatorTest`, `RecomputeServiceTest`, `ArchitectureTest`.
- [ ] **Step 5: Commit** (arquiteto) — `feat: recomendação de runas, feitiços e itens sempre publicada`.

### Task 4: Pacote formatVersion 2

**Files:**
- Modify: `src/main/java/com/your/summoner/processor/infrastructure/adapter/in/rest/KnowledgeResponse.java`
- Modify: `src/main/java/com/your/summoner/processor/infrastructure/adapter/out/mongo/MongoKnowledgeStore.java:49` (`formatVersion` 2; gravar e ler os campos novos)
- Modify: `docs/formato-pacote.md`
- Test: `KnowledgeApiTest`, `MongoKnowledgeStoreTest`

**Interfaces:**
- Consumes: records da Task 3.
- Produces: JSON por entrada com `lowSample`, `keystones[] {perk, games, wins, winRate, lowSample}`, `runePages[] {keystone, primaryStyle, subStyle, perks, statPerks{offense,flex,defense}|null, games, wins, winRate, lowSample, buildPath{sequence,games,wins,winRate}|null, buildPathLowSample}`, `summonerSpells[] {sequence, games, wins, winRate, lowSample}`, `primary {keystone, summonerSpells, summonerSpellsLowSample}|null`; raiz `formatVersion: 2`. `winRate` como hoje (4 casas, HALF_UP).

- [ ] **Step 1: Testes que falham** — `KnowledgeApiTest.v2FieldsPresent` (JSON tem `formatVersion == 2`, `entries[0].primary.keystone`, `entries[0].runePages[0].statPerks.offense`, `entries[0].lowSample`); `MongoKnowledgeStoreTest.v2RoundTrip` (grava e lê `ChampionKnowledge` com todas as partes novas, incluindo `statPerks` e `buildPath` nulos).
- [ ] **Step 2: Rodar e ver falhar** (fora do sandbox se o Mongo embarcado não subir).
- [ ] **Step 3: Implementar** e atualizar `docs/formato-pacote.md` (tabela nova, regra `lowSample`, "sempre há recomendação").
- [ ] **Step 4: Rodar e passar** — `mvn -q verify`.
- [ ] **Step 5: Commit** (arquiteto) — `feat: pacote de conhecimento v2 com runas e feitiços`.

### Task 5: Verificação com dados reais (arquiteto)

- [ ] **Step 1:** Reiniciar o coletor BR1 com a Task 1 (`stack.ps1 down -Only collector` e `up -Only collector`); esperar novas partidas; `collector-check.ps1` mostra anonimato OK e `mongosh` confirma `runes.statPerks` nas partidas novas.
- [ ] **Step 2:** Subir `processor-test` e checar `GET /processor/knowledge/16.22`: `formatVersion 2`; Irelia (`championId` 39) `TOP` publicada com `primary` não nulo; uma entrada de amostra baixa com `lowSample: true`.
- [ ] **Step 3:** Revisão linha a linha (`review.ps1`) de cada task antes do commit; fechar `yoursummoner_processor#5` e comentar em `live-builds#12`.
