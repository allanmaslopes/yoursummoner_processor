# Formato do pacote de conhecimento

`GET /processor/knowledge/{patch}` retorna JSON da versão 1. O patch usa `major.minor`, por exemplo `16.22`. Um patch inexistente retorna 404 como `application/problem+json`.

| Campo | Tipo | Significado |
|---|---|---|
| `formatVersion` | número inteiro | Versão do contrato; atualmente `1`. |
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
| `purchaseTree` | array de sequências | Ordem de compras de itens da árvore. |
| `componentsBefore` | objeto | Identificador de item completo como chave e sequências de componentes anteriores como valor. |
| `skillOrders` | array de sequências | Ordem dos primeiros pontos de habilidade. |
| `skillPriority` | string | Prioridade de habilidades inferida da sequência mais comum. |
| `itemWinRates` | array de itens | Estatísticas dos itens completos. |

Cada sequência tem `sequence` (array de identificadores de itens ou habilidades), `games`, `wins` e `winRate`. Cada item tem `itemId`, `games`, `wins` e `winRate`. As taxas são números arredondados a quatro casas com HALF_UP; denominador zero resulta em `0.0000`.

O cabeçalho `ETag` é um hash SHA-256 do `computedAt`. Envie seu valor em `If-None-Match` para receber `304 Not Modified` sem corpo quando não houver mudança. Respostas JSON podem ser comprimidas com gzip conforme `Accept-Encoding`.

`GET /processor/knowledge/{patch}/{championId}/{position}` retorna uma entrada no mesmo formato ou 404 `application/problem+json`.

`GET /processor/status` retorna `{ "patches": [...] }`. Cada patch contém `patch`, `matches`, `ignoredBySchema` (partidas ignoradas por versão de schema), `entries` (quantidade publicada), `computedAt` e `durationMillis` (duração do cálculo em milissegundos).
