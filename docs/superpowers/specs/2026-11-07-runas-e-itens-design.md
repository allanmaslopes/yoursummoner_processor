# Runas, feitiços e itens por runa principal — design

Data: 2026-11-07. Projetos afetados: `yoursummoner_collector` (contrato de partidas) e `yoursummoner_processor` (agregação e pacote). É o projeto 1 de 3; os seguintes usam este pacote.

## Por que

Numa partida real de Irelia o live-builds não mostrou build nenhuma: 54 campeões (cerca de 1 em 3, ex.: Irelia, Jinx, Darius, Vayne) não têm build oficial da Riot para Summoner's Rift nos arquivos do jogo (live-builds#12). O usuário decidiu:

- A build de **elo alto** passa a ser a recomendação **principal para todos os campeões** (não só os sem build oficial). Fontes alternativas (oficial da Riot, melhores jogadores) virão depois como "arsenais".
- A recomendação junta **runa + itens**, para a build ser coerente: o caminho de itens recomendado é o mais feito por quem usou a mesma runa principal.
- A **página de runas** é essencial: será exibida na partida e, no projeto 3, aplicada e editada pelo cliente (LCU).
- Critério principal: **frequência** ("os itens feitos com mais frequência"), não taxa de vitória.

Ordem acordada: (1) este projeto → (2) live-builds na partida usa o pacote (live-builds#1, #12) → (3) seleção de campeões pela LCU: aplicar e editar runas, arsenais, importações ligáveis (live-builds#4, #7, #8, #9 e issue nova de arsenais).

## Objetivos

1. O coletor passa a guardar os 3 fragmentos (runas de atributo) de cada participante.
2. O processor publica, por campeão e posição:
   - runas principais (keystones) mais usadas, com partidas, vitórias e taxa;
   - para cada runa principal com amostra suficiente: a página completa mais frequente (árvore primária, árvore secundária, 6 runas, 3 fragmentos) e o caminho de itens mais frequente entre quem a usou;
   - duplas de feitiços mais usadas;
   - uma **recomendação principal**: a runa principal mais frequente, com sua página, seu caminho de itens e a dupla de feitiços mais frequente do campeão e posição.
3. O pacote sobe para `formatVersion` 2, mantendo todos os campos da versão 1.

## Não objetivos

- Mudar o live-builds (projeto 2) ou escrever no cliente (projeto 3).
- Recomendação situacional (time inimigo, vantagem): é do motor (live-builds-engine).
- Escolher por taxa de vitória ou intervalo de confiança.
- Reprocessar partidas antigas na Riot para obter fragmentos.

## Anti-objetivos (seria falha mesmo funcionando)

- Publicar página de runas inválida para o cliente (runa que não pertence à árvore, fragmento fora do slot). A página sai exatamente como um jogador real usou.
- Misturar runas de jogadores diferentes numa "página Frankenstein": a página é a combinação completa mais frequente, não a runa mais frequente de cada slot.
- Guardar ou publicar qualquer identificador de jogador (regra do coletor continua).
- Quebrar quem lê o pacote v1.

## Restrições

- Contrato de partidas: mudança **aditiva** em `runes` — campo novo `statPerks` (objeto `offense`, `flex`, `defense`, ints, como a Riot manda em `perks.statPerks`). `schemaVersion` continua 2. Partidas já salvas não têm o campo; o processor deve aceitar ausência.
- Amostra mínima: os parâmetros existentes `processor.min-games` (30, por campeão e posição) e `processor.min-occurrences` (10) passam a definir `lowSample`, não a esconder dados (ver "Regra do usuário: sempre há recomendação").
- Fragmentos: a página usa os fragmentos mais frequentes entre as partidas com aquela página que **têm** `statPerks`. Se nenhuma tiver, `statPerks` sai `null` (o cliente usará o padrão do projeto 3).
- Arquitetura hexagonal e `ArchitectureTest` atuais dos dois serviços; mesma estratégia de recálculo completo por patch e troca atômica.
- Coletor: contrato documentado em `docs/contrato-matches.md` deve ser atualizado.

## Decidido

- "Caminho de itens" = mesma definição de `buildPaths` atual (ordem dos três primeiros itens completos).
- "Página igual" = mesma árvore primária, árvore secundária e as 6 runas na ordem dos slots (fragmentos à parte, pela regra acima).
- Empate: maior número de partidas; depois maior taxa de vitória; depois menor id (determinístico).
- A runa principal é o primeiro elemento de `perks` (slot 0 da árvore primária).

## Regra do usuário: sempre há recomendação

"Sempre tem que ter pelo menos uma recomendação." Os limites de amostra deixam de **esconder** dados e passam a **marcar** a confiança:

- Toda combinação campeão e posição com pelo menos 1 partida no patch gera entrada (hoje só com `min-games` 30). Entrada nova: `lowSample` (booleano) = `games < min-games`.
- `primary` nunca é `null` numa entrada: usa a runa principal, a página, o caminho de itens e a dupla de feitiços mais frequentes, mesmo abaixo de `min-occurrences`. Cada parte que ficar abaixo do limite vai com `lowSample: true` no seu objeto.
- `runePages[].buildPath`: caminho próprio mais frequente da runa principal; se ele estiver abaixo de `min-occurrences` e o caminho geral `buildPaths[0]` tiver mais partidas, usa o geral. Só é `null` se nenhuma partida da entrada tiver três itens completos (partida curta); nesse caso, o live-builds (projeto 2) mostra os itens iniciais e as botas.
- A entrada `ANY` (todas as posições do campeão) também segue a regra, para o live-builds usar quando a posição do jogador não tiver partidas.
- Campeão sem nenhuma partida no patch: fica a cargo do live-builds (projeto 2) cair para o patch anterior; o processor não inventa dado.

## Decisões do arquiteto (após revisão do construtor)

- `keystones` lista as mais frequentes (mesmo `topN`); `runePages` traz uma página para cada runa principal listada.
- Empate entre páginas da mesma runa principal: mais partidas; depois maior taxa de vitória; depois comparação lexicográfica crescente de `(primaryStyle, subStyle, perks…)`.
- Feitiços: a dupla mais frequente sempre entra em `primary.summonerSpells`, marcada `lowSample` quando abaixo de `min-occurrences`.
- Compatibilidade v1: os campos antigos mantêm os filtros de hoje; quem lê v1 só passa a ver mais entradas (as de amostra baixa, com `lowSample`).

## Formato v2 (acréscimos por entrada)

```json
"keystones": [ { "perk": 8010, "games": 120, "wins": 66, "winRate": 0.55 } ],
"runePages": [
  {
    "keystone": 8010,
    "primaryStyle": 8000, "subStyle": 8400,
    "perks": [8010, 9111, 9104, 8299, 8444, 8242],
    "statPerks": { "offense": 5008, "flex": 5008, "defense": 5011 },
    "games": 80, "wins": 45, "winRate": 0.5625,
    "buildPath": { "sequence": [3153, 3078, 3053], "games": 30, "wins": 17, "winRate": 0.5667 }
  }
],
"summonerSpells": [ { "sequence": [4, 12], "games": 140, "wins": 75, "winRate": 0.5357 } ],
"primary": { "keystone": 8010, "summonerSpells": [4, 12] }
```

`primary.keystone` aponta para a entrada de `runePages` a usar; `primary` nunca é `null`. Objetos abaixo do limite levam `"lowSample": true` (entrada, runa, página, caminho e feitiços). Feitiços: dupla ordenada (menor id primeiro) para `[4,12]` e `[12,4]` contarem juntas.

## Deixado ao construtor

- Nomes de classes e onde a agregação de runas mora (estender `KnowledgeAggregator` ou classe irmã).
- Quantas entradas de `keystones`/`runePages`/`summonerSpells` publicar (sugestão: as 3 mais frequentes, como `topN`).
- Testes: casos de empate, partida sem `statPerks`, runa abaixo da amostra caindo no caminho geral, compatibilidade v1.
- Atualizar `docs/formato-pacote.md` e `docs/contrato-matches.md`.
