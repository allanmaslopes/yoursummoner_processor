# yoursummoner_processor v1 — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serviço Spring Boot que lê as partidas Mestre+ do coletor (contrato `matches` v2) e publica, por patch, campeão e rota, estatísticas de itens, árvore de compras e skills de elo alto num pacote JSON para o `live-builds`.

**Architecture:** Hexagonal, igual aos outros serviços. Um job recalcula cada patch do zero a cada hora: lê `matches` (só leitura), agrega em memória com funções puras e troca o resultado de forma atômica. Um controller serve o pacote com ETag.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Cloud 2025.1.4 (OpenFeign para o Data Dragon), Spring Data MongoDB, JUnit 5, Mockito, WireMock, ArchUnit, JaCoCo, flapdoodle.

**Spec:** `docs/superpowers/specs/2026-11-07-processor-design.md`. Contrato de entrada: `D:\Workspace\yoursummoner_collector\docs\contrato-matches.md` (`schemaVersion: 2`).

**Referência para copiar e adaptar (somente leitura):** `D:\Workspace\yoursummoner_collector` — `pom.xml`, `ArchitectureTest`, `ApplicationSmokeTest`, `CLAUDE.md`/`AGENTS.md`, `.gitignore`, `.env.example`, `.claude/run-collector.cmd`, `domain/model/{CollectedMatch,PatchVersion}` (o processor tem a própria cópia de leitura do formato).

## Global Constraints

- Pacote `com.your.summoner.processor`; artifactId `yoursummoner-processor`; porta `8082`; context path `/processor`.
- Mesmas versões/plugins do `pom.xml` do coletor (com o `argLine` e o `jvmArguments` do fix de loopback do Windows). Sem Spring Security nem Resilience4j.
- Leitura: `processor.source.uri: ${COLLECTOR_MONGODB_URI:mongodb://127.0.0.1:27017/yoursummoner_collector}`, coleção `matches`, **somente leitura** (nenhum insert/update/delete nela; um teste garante). Escrita: `spring.mongodb.uri: ${MONGODB_URI:mongodb://127.0.0.1:27017/yoursummoner_processor}`.
- Só `schemaVersion == 2`; outras são ignoradas e contadas no status.
- `processor.min-games: 30`, `processor.min-occurrences: 10`, `processor.top-n: 5`, `processor.cron: "0 15 * * * *"`, `processor.enabled: true` (`false` no perfil `test`).
- "Item completo" = sem `into`, `gold.total >= 2000`, sem tag `Boots` e sem `Consumable`. "Item da árvore" = `gold.total >= 1000` (inclui botas nível 2 e componentes caros), não consumível.
- Evolução de missão: um item cujo único `from` é um item com tag `Boots` (ex.: `3172.from = ["3006"]`) é normalizado para o item base em todas as estatísticas.
- Nunca ler, guardar ou expor identificadores de jogador (o coletor já não os grava; o processor não cruza com nada).
- Commits locais na `master`, sem push, com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; mensagem do último commit de cada tarefa referencia a issue do quadro.

## Review Focus

1. **Partida com `position` vazia:** entra só na visão por campeão (`position = "ANY"`), nunca numa rota. Teste na Task 3 (`emptyPositionCountsOnlyForChampion`).
2. **UNDO depois de BUY:** a compra desfeita some da ordem e da árvore. Teste na Task 3 (`undoRemovesPreviousPurchase`).
3. **Bota evoluída pela missão:** conta como a bota base. Teste na Task 3 (`questUpgradeCountsAsBaseBoots`).
4. **Amostra pequena:** entrada com 29 partidas não sai no pacote; sequência com 9 ocorrências não aparece. Teste na Task 4 (`belowMinimumIsNotPublished`).
5. **Recálculo concorrente com leitura:** quem baixa o pacote durante um recálculo recebe o anterior inteiro, nunca metade. Teste na Task 5 (`readersSeeOldPackageUntilSwap`).

---

### Task 1: Bootstrap
**Delegado:** Codex `gpt-5.6-terra`.
Copiar/adaptar do coletor: pom, classe principal (`@SpringBootApplication @EnableFeignClients @EnableScheduling @ConfigurationPropertiesScan`), `application.yml`/`application-test.yml`, `ArchitectureTest`, `ApplicationSmokeTest` (`/processor/actuator/health` 200), `CLAUDE.md`, `AGENTS.md`, `.gitignore`, `.env.example` (`MONGODB_URI`, `COLLECTOR_MONGODB_URI`), `.claude/run-processor.cmd` (porta 8082).
- [ ] Testes → FAIL → criar → `mvn -q verify` PASS → commit `build: inicia o yoursummoner_processor` (refs `yoursummoner_processor#3`).

### Task 2: Catálogo de itens (Data Dragon) e domínio
**Delegado:** Codex `gpt-5.6-sol`.
**Files:** `domain/model/{MatchRecord,ItemInfo,ItemCatalog}.java`; `application/port/out/{ItemCatalogSource,MatchSource}.java`; `infrastructure/adapter/out/ddragon/*` (Feign `cdn/<patch>/data/en_US/item.json` + cache em disco `${user.home}/.yoursummoner/processor/ddragon/<patch>/item.json`); `infrastructure/adapter/out/collector/MongoMatchSource.java` (lê `matches` por `patch`, só `schemaVersion: 2`, em lotes com cursor; mapeia para `MatchRecord`, um record só com o que as agregações usam: participantes `{slot, team, championId, position, win, items}`, eventos `{t, slot, type, itemId, skillSlot, frame}` e `frames`).
**Interfaces:**
```java
record ItemInfo(int id, int totalGold, boolean boots, boolean consumable, List<Integer> from, List<Integer> into) {}
final class ItemCatalog { boolean isCompleted(int id); boolean isTreeItem(int id); int normalize(int id); /* evolução de missão → base */ }
interface ItemCatalogSource { ItemCatalog forPatch(PatchVersion patch); }      // patch "16.22" → maior "16.22.*" de versions.json
interface MatchSource { Stream<MatchRecord> byPatch(PatchVersion patch); List<PatchVersion> patches(); }
```
- [ ] Testes: `completedAndTreeRules` (3031 completo; 3006 árvore e não completo; 2003 nenhum), `questUpgradeNormalizesToBase` (3172 → 3006), `onlySchemaV2IsRead`, `sourceNeverWrites` (o adapter só usa `find`/`aggregate`; teste com Mongo embarcado verificando a contagem da coleção antes e depois), `catalogIsCachedOnDisk` (WireMock: segunda chamada não vai à rede).
- [ ] FAIL → implementar → PASS → commit (refs `#3`).

### Task 3: Extração por participante (função pura)
**Delegado:** Codex `gpt-5.6-sol`.
**Files:** `application/service/ParticipantExtractor.java` + teste.
**Interfaces:**
```java
record ParticipantView(int championId, String position /* "TOP".."UTILITY" ou "ANY" */, boolean win,
                       List<Integer> starting, Integer boots, List<Integer> completedOrder, List<Integer> treeOrder,
                       List<Integer> skillOrder /* 1..4, até 9 */, List<Integer> finalCompleted,
                       List<DecisionSample> decisions) {}
record DecisionSample(int minute, int itemId, int teamGoldDiff, int laneLevelDiff, int blueKills, int redKills, boolean win) {}
List<ParticipantView> extract(MatchRecord m, ItemCatalog c);   // 10 por partida (posição vazia → "ANY")
```
Regras: compras pelos eventos BUY do slot, ordenadas por `t`; `UNDO` remove o BUY anterior do mesmo item; SELL não altera a ordem; tudo normalizado com `c.normalize`. `starting` = BUY com `t <= 60`. `boots` = primeiro item com `boots` comprado (normalizado). `completedOrder` = itens completos na ordem (até 3). `treeOrder` = itens da árvore (≥ 1000) na ordem, até o 3º completo inclusive. `skillOrder` = `skillSlot` dos SKILL, até 9. `decisions` = um por BUY de **todo item não consumível, qualquer preço** (inclui itens baratos de acúmulo/bola de neve como Selo Sombrio 1082 e itens de início; pedido do usuário em 2026-11-07): `minute = frames[frame].minute`, `teamGoldDiff` = soma de `totalGold` do time − do outro no frame, `laneLevelDiff` = nível do slot − nível do slot inimigo da mesma `position` (0 se não houver), abates do frame.
- [ ] Testes: `startingItemsUntilSixtySeconds`, `undoRemovesPreviousPurchase`, `questUpgradeCountsAsBaseBoots`, `completedOrderStopsAtThree`, `treeIncludesComponentsAboveThousand`, `skillOrderFirstNine`, `emptyPositionCountsOnlyForChampion`, `decisionSampleUsesEventFrame`.
- [ ] FAIL → implementar → PASS → commit (refs `#3`).

### Task 4: Agregação e amostra mínima (função pura)
**Delegado:** Codex `gpt-5.6-sol`.
**Files:** `application/service/KnowledgeAggregator.java` + `domain/model/{ChampionKnowledge,SequenceStat,ItemStat}.java` + teste.
**Interfaces:**
```java
record SequenceStat(List<Integer> sequence, int games, int wins) {}   // winRate calculado na serialização
record ItemStat(int itemId, int games, int wins) {}
record ChampionKnowledge(String patch, int championId, String position, int games, int wins,
    List<SequenceStat> starting, List<SequenceStat> boots, List<SequenceStat> buildPaths /* 3 completos */,
    List<SequenceStat> purchaseTree /* árvore ≥1000 */, Map<Integer, List<SequenceStat>> componentsBefore /* por item completo */,
    List<SequenceStat> skillOrders, String skillPriority /* "R > Q > E > W" */, List<ItemStat> itemWinRates) {}
List<ChampionKnowledge> aggregate(String patch, Stream<ParticipantView> views, int minGames, int minOccurrences, int topN);
```
`skillPriority`: para cada skill Q/W/E, o nível em que chega ao máximo (5) na sequência mais comum estendida até 18 pontos não existe; use a ordem em que Q/W/E receberam o 2º ponto na sequência mais comum, com R sempre primeiro. `componentsBefore`: para cada item completo em `completedOrder`, a sequência de itens da árvore comprados entre o item completo anterior e ele.
- [ ] Antes da Task 4, ajuste a Task 3: `decisions` passa a cobrir toda compra não consumível (regra acima) e `startingItemsUntilSixtySeconds` usa itens baratos reais (Lâmina de Doran 1055 e duas Poções 2003, ambos abaixo de 1000) e confere que os dois aparecem, com repetição.
- [ ] Testes: `belowMinimumIsNotPublished`, `sequencesSortedByGamesThenWinRate`, `topNLimits`, `skillPriorityFromMostCommonOrder`, `componentsBeforeCompletedItem`, `anyPositionAggregatesAllRoles`.
- [ ] FAIL → implementar → PASS → commit (refs `#3`).

### Task 5: Recálculo, publicação atômica e decisões
**Delegado:** Codex `gpt-5.6-sol`.
**Files:** `application/port/out/KnowledgeStore.java`; `application/service/RecomputeService.java`; `infrastructure/adapter/out/mongo/MongoKnowledgeStore.java` (coleções `knowledge_<patch>_<version>` + documento `knowledge_meta` com o ponteiro `activeCollection` por patch, trocado com `findAndModify` depois de a coleção nova estar completa; coleção antiga apagada depois); `decision_samples_<patch>` (substituída do mesmo jeito); `infrastructure/adapter/in/scheduler/RecomputeScheduler.java` (cron + na subida, se `processor.enabled`).
- [ ] Testes: `recomputeIsIdempotent` (duas rodadas → mesmo resultado), `readersSeeOldPackageUntilSwap`, `oldCollectionsAreDropped`, `patchesOutsideSourceAreRemoved` (patch que saiu do coletor some do processor), `disabledInTestProfile`.
- [ ] FAIL → implementar → PASS → commit (refs `#3`).

### Task 6: API do pacote, status e contrato
**Delegado:** Codex `gpt-5.6-terra`.
**Files:** `infrastructure/adapter/in/rest/{KnowledgeController,StatusController}.java`; `docs/formato-pacote.md` (formato `formatVersion: 1` campo a campo); `README.md`.
- `GET /processor/knowledge/{patch}` → `{formatVersion, patch, computedAt, matches, entries:[ChampionKnowledge com winRate]}`; `ETag` = hash de `computedAt`; `If-None-Match` igual → 304; gzip ligado (`server.compression.enabled: true`, `mime-types: application/json`).
- `GET /processor/knowledge/{patch}/{championId}/{position}` → uma entrada ou 404 problem.
- `GET /processor/status` → por patch: partidas lidas, ignoradas por schema, entradas publicadas, `computedAt`, duração.
- [ ] Testes: `packageHasEtagAndReturns304`, `singleEntryOr404`, `statusShowsPerPatch`.
- [ ] FAIL → implementar → PASS; `mvn verify` completo → commit (refs `#3`).

### Task 7: Verificação real
**Delegado:** Claude (coordena).
- [ ] Subir o processor contra o Mongo local com as partidas v2 do coletor; conferir `/processor/status` e o pacote do patch atual (com poucas partidas, a maioria das entradas fica abaixo do mínimo: rodar uma vez com `processor.min-games=3` só para conferir o formato).
- [ ] Commit da documentação final (refs `#3`).
