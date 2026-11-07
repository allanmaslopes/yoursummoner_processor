# Design — yoursummoner_processor v1: conhecimento de builds e skills de elo alto

- Data: 2026-11-07
- Repositório: `D:\Workspace\yoursummoner_processor` (GitHub `allanmaslopes/yoursummoner_processor`)
- Lê: coleção `matches` do `yoursummoner_collector` (contrato em `D:\Workspace\yoursummoner_collector\docs\contrato-matches.md`, `schemaVersion: 2`)
- Serve: o `live-builds` (e, depois, o `live-builds-engine`)

## 1. Intenção

O usuário quer um motor de recomendação próprio, que aprenda com jogadores Mestre+ e repasse a jogadores casuais o que eles fazem. O coletor já guarda partidas Mestre+ reduzidas e anônimas. Este serviço transforma essas partidas em **conhecimento consultável por patch, campeão e rota**, que o `live-builds` baixa e usa no lugar (ou ao lado) da recomendação oficial da Riot.

## 2. Objetivos da v1

Para cada `(patch, championId, position)` com amostra suficiente:

- **Visão geral:** partidas, vitórias, taxa de vitória.
- **Itens iniciais:** os conjuntos de compra até 60 s (ex.: `[3865, 2003, 2003]`), com contagem e taxa de vitória.
- **Botas:** qual bota completa, contagem e taxa de vitória.
- **Caminho de build:** os 3 primeiros **itens completos** em ordem de compra (sequência exata), com contagem e taxa de vitória; e a frequência de cada item completo por posição na ordem (1º, 2º, 3º, 4º+).
- **Ordem de skills:** as sequências dos 9 primeiros pontos (ex.: `Q E W Q Q R Q E Q`) e a prioridade de evolução derivada (ex.: `R > Q > E > W`), com contagem e taxa de vitória.
- **Taxa de vitória por item completo** (aparecer nos itens finais).

Também na v1, como **base para a versão situacional**: guardar amostras de decisão `(patch, championId, position, situação resumida, item comprado, venceu)` para cada compra de item completo. A situação resumida vem de `frames[event.frame]` (estado dos 10 jogadores no minuto anterior ao evento) e tem: minuto, diferença de ouro da equipe, diferença de nível com o oponente de rota, abates por time, e a mistura de dano físico/mágico do time inimigo (dos dados finais da partida). A v1 só guarda essas amostras; não as consome.

Publicação: um **pacote de conhecimento por patch** (JSON) servido por HTTP, mais consulta por campeão e rota.

## 3. Não-objetivos

- Recomendação situacional ("nesta situação compre X") — v2, quando houver amostra.
- Runas, feitiços de invocador, confrontos (matchups) e tier list — depois.
- Integrar o `live-builds` ao pacote (é outra tarefa, no repositório dele).
- Escrever na coleção `matches` ou no banco do coletor.

## 4. Anti-objetivos

- Publicar estatística com amostra pequena como se fosse confiável. Toda estatística sai com `games`, e o pacote só inclui entradas com amostra mínima (seção 6).
- Reidentificar jogadores: o processor só lê o que o coletor guardou (sem identificadores) e não cruza com nada externo.
- Mostrar taxa de vitória de Arena (a política da Riot restringe; e a v1 é só fila 420).
- Calcular MMR ou ELO estimado (proibido pela Riot).

## 5. Como processa

- **Recalcular por patch, não incrementar.** A cada 1 h (configurável), para cada patch presente em `matches` (no máximo os 3 que o coletor retém), lê todas as partidas daquele patch e recalcula tudo do zero, substituindo o resultado anterior de forma atômica (grava numa coleção/documento novo e troca o ponteiro). Motivo: com milhares a dezenas de milhares de partidas por patch, recalcular é barato, é idempotente e evita contar duas vezes depois de uma queda. O contrato do coletor permitia um ponto de leitura incremental; a v1 não precisa dele.
- **Item completo:** item do `item.json` do Data Dragon do patch sem `into` (não vira outro) e com custo ≥ 2000, excluindo botas (tag `Boots`) e consumíveis. A lista de itens vem do Data Dragon, com cache em disco por patch.
- **Ordem de compra:** pelos eventos `BUY` do participante; `UNDO` desfaz o `BUY` anterior do mesmo item; venda (`SELL`) não remove da ordem de compra.
- **Sequência de skills:** eventos `SKILL` do participante, em ordem, até 9.
- **Posição:** a `position` do participante; vazia → partida ignorada para estatísticas por rota (conta só na visão por campeão).
- **Quem saiu da partida:** conta normalmente, com o que chegou a comprar e evoluir (call do arquiteto; o efeito na taxa de vitória é pequeno e some com amostra).

## 6. Amostra mínima

- Entrada `(champion, position)` só entra no pacote com **≥ 30 partidas**.
- Uma sequência (build, skills, iniciais, botas) só aparece com **≥ 10 ocorrências**, ordenada por contagem e, depois, por taxa de vitória; no máximo 5 por lista.
- Valores configuráveis.

## 7. Saída

- Coleção `knowledge` (banco `yoursummoner_processor`), um documento por `(patch, championId, position)`, e um documento `knowledge_meta` por patch com `computedAt`, `matches`, `version` do formato.
- `GET /processor/knowledge/{patch}` → pacote do patch inteiro (JSON, `Content-Encoding: gzip` quando o cliente aceita), com `ETag` para o `live-builds` só baixar quando mudar.
- `GET /processor/knowledge/{patch}/{championId}/{position}` → uma entrada.
- `GET /processor/status` → por patch: partidas lidas, entradas publicadas, último cálculo, duração.
- Formato versionado (`formatVersion: 1`) e documentado em `docs/formato-pacote.md` (contrato com o `live-builds`).

## 8. Decisões do usuário

- Processor separado do coletor, consumindo o que ele guarda.
- Seguir as políticas da Riot; aprender com Mestre+; dados anônimos.
- O objetivo final é o motor situacional próprio.

## 9. Decisões do arquiteto (revise)

1. v1 simples (estatística por campeão e rota) + guardar amostras de decisão para a v2 situacional. O usuário não escolheu; a v1 situacional pediria muito mais partidas do que a chave pessoal coleta.
2. Recalcular por patch a cada 1 h, em vez de processamento incremental.
3. Amostra mínima 30 partidas por entrada e 10 por sequência.
4. Item completo pela árvore do Data Dragon (sem `into`, ≥ 2000, sem botas e consumíveis).
5. Stack igual aos outros serviços: Spring Boot 4.1, Java 25, hexagonal com `ArchitectureTest`, Spring Data MongoDB, OpenFeign (Data Dragon), JUnit/Mockito/WireMock/JaCoCo, Mongo embarcado nos testes. Porta 8082, context path `/processor`.
6. Leitura direta da coleção `matches` do banco `yoursummoner_collector` (só leitura), pelo contrato versionado. Partidas com `schemaVersion` diferente de 2 são ignoradas com log (a v1, que repetia a situação em cada evento, não é mais produzida).

## 10. O que fica com o builder

Nomes de classes e pacotes, formato exato dos documentos de saída (respeitando a seção 7), forma de troca atômica, algoritmo de agregação em memória, estratégia de cache do Data Dragon, índices.
