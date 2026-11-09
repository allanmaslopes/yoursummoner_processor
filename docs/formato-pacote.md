# Formato do pacote de conhecimento

`GET /processor/knowledge/{patch}` retorna JSON da versão 2 (a versão 2 só acrescenta campos; todos os da versão 1 continuam). O patch usa `major.minor`, por exemplo `16.22`. Um patch inexistente retorna 404 como `application/problem+json`.

| Campo | Tipo | Significado |
|---|---|---|
| `formatVersion` | número inteiro | Versão do contrato; atualmente `2`. |
| `patch` | string | Patch das partidas agregadas. |
| `computedAt` | string | Instante do cálculo em ISO 8601 UTC. |
| `matches` | número inteiro | Quantidade de partidas lidas para o patch. |
| `entries` | array | Entradas publicadas por campeão e posição. |

Cada entrada contém:

| Campo | Tipo | Significado |
|---|---|---|
| `patch` | string | Patch da entrada. |
| `championId` | número inteiro | Identificador do campeão. |
| `position` | string | Posição; `ANY` agrega todas as posições do campeão. |
| `games` | número inteiro | Partidas da amostra. |
| `wins` | número inteiro | Vitórias da amostra. |
| `winRate` | número | `wins / games`, arredondado a quatro casas; zero se `games` for zero. |
| `starting` | array de sequências | Combinações de itens iniciais. |
| `boots` | array de sequências | Botas compradas. |
| `buildPaths` | array de sequências | Ordem dos três primeiros itens completos. |
| `fullBuildPaths` | array de builds completas | Até os seis primeiros itens completos, na ordem, calculados somente com participantes que fecharam cinco ou mais itens completos. |
| `purchaseTree` | array de sequências | Ordem de compras de itens da árvore. |
| `componentsBefore` | objeto | Identificador de item completo como chave e sequências de componentes anteriores como valor. |
| `skillOrders` | array de sequências | Ordem dos primeiros pontos de habilidade. |
| `skillPriority` | string | Prioridade de habilidades inferida da sequência mais comum. |
| `itemWinRates` | array de itens | Estatísticas dos itens completos. |
| `lowSample` | booleano | `true` quando `games` < `processor.min-games` (30). |
| `keystones` | array | Runas principais mais frequentes: `perk`, `games`, `wins`, `winRate`, `lowSample`. |
| `runePages` | array | Uma página por runa principal listada (a página completa mais frequente dela). |
| `summonerSpells` | array | Duplas de feitiços mais frequentes: `sequence` (menor id primeiro), `games`, `wins`, `winRate`, `lowSample`. |
| `primary` | objeto ou `null` | Recomendação principal: `keystone`, `summonerSpells`, `summonerSpellsLowSample`. |
| `goldCurve` | array | Referência de ouro total acumulado por minuto: pontos a cada 5 minutos de 5 a 40, com `minute`, `medianTotalGold` e `games`; um ponto só é publicado com ao menos 10 partidas. |

Cada elemento de `runePages` tem `keystone`, `primaryStyle`, `subStyle`, `perks` (6 runas na ordem dos slots; a primeira é a runa principal), `statPerks` (`{offense, flex, defense}` ou `null` quando nenhuma partida da página guardou fragmentos), `games`, `wins`, `winRate`, `lowSample`, `buildPath` (`{sequence, games, wins, winRate}` ou `null`), `buildPathLowSample` e `fullBuildPath` (build completa ou `null`). `buildPath` é o caminho de itens mais frequente de quem usou a runa principal; se ele tiver menos de `processor.min-occurrences` (10) partidas e o caminho geral tiver mais, usa o geral; é `null` só quando nenhuma partida da entrada tem três itens completos. `fullBuildPath` é a build completa mais frequente entre as partidas daquela página com cinco ou mais itens; quando não há uma, usa a primeira de `fullBuildPaths` da entrada, ou `null` se também não houver build geral. `primary.keystone` aponta para a entrada de `runePages` a usar.

`lowSample` marca confiança, não esconde dados: nas partes novas vale `games` < `processor.min-occurrences` (10) e, na entrada, `games` < `processor.min-games` (30). Em `fullBuildPaths`, a build mais frequente é publicada mesmo abaixo de `processor.min-occurrences`; a lista só fica vazia quando nenhuma partida tem cinco ou mais itens completos. Há entrada para toda combinação campeão e posição com pelo menos uma partida, e `primary` só é `null` quando nenhuma partida da entrada tem página de runas completa (6 runas), ou seja, sempre há recomendação quando existe dado de runas. Os campos da versão 1 mantêm os filtros de amostra de antes. Quem lê a versão 1 passa a receber também entradas com menos de `processor.min-games` partidas, marcadas com `lowSample` (antes eram filtradas).

Cada sequência tem `sequence` (array de identificadores de itens ou habilidades), `games`, `wins` e `winRate`. Cada build completa acrescenta `lowSample`. Cada item tem `itemId`, `games`, `wins` e `winRate`. As taxas são números arredondados a quatro casas com HALF_UP; denominador zero resulta em `0.0000`.

O cabeçalho `ETag` é um hash SHA-256 do `computedAt`. Envie seu valor em `If-None-Match` para receber `304 Not Modified` sem corpo quando não houver mudança. Respostas JSON podem ser comprimidas com gzip conforme `Accept-Encoding`.

`GET /processor/knowledge/{patch}/{championId}/{position}` retorna uma entrada no mesmo formato ou 404 `application/problem+json`.

`GET /processor/status` retorna `{ "patches": [...] }`. Cada patch contém `patch`, `matches`, `ignoredBySchema` (partidas ignoradas por versão de schema), `entries` (quantidade publicada), `computedAt` e `durationMillis` (duração do cálculo em milissegundos).
