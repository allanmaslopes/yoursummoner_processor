# Pesquisa: serviço de dados e recomendação (yoursummoner_ml)

Data da pesquisa: 2026-11-07. Escopo: escolher stack, abordagem de recomendação, skills de IA úteis e arquitetura para o serviço que administra as partidas coletadas (Mestre+) e treina/publica o modelo de recomendação de itens usado pelo app Rust no PC do jogador.

Legenda: **[F]** = fato apoiado por fonte citada. **[O]** = opinião/recomendação minha, sem fonte direta. Benchmarks de fornecedores e blogs são indicativos; reexecute com os seus dados.

---

## 1. Linguagem e stack do serviço

### 1.1 Comparação

| Critério | Python (Polars, DuckDB, scikit-learn, LightGBM, PyTorch) | Java/Kotlin (Spring + Tribuo/Smile/DJL) | Rust (Polars, linfa) | SQL analítico (DuckDB / ClickHouse) |
|---|---|---|---|---|
| Ecossistema de dados e ML | Maior. Padrão de fato para ML tabular e sequencial | Bom para ML clássico; Tribuo e Smile são sólidos mas com comunidade menor. DJL cobre deep learning | Polars é maduro; linfa é menor e com menos algoritmos | Não é ML; ótimo para agregações e features |
| Desempenho em dados | Polars e DuckDB são rápidos; pandas é o mais lento [F] | JVM boa, mas o ecossistema de dataframe é pior | Excelente | Excelente em agregação |
| Exportar modelo para Rust | Melhor caminho: LightGBM em texto ou ONNX, ou tabelas puras | Possível via ONNX, mas um passo a mais | Nativo, mas o treino em Rust é trabalhoso | Não treina modelo; exporta tabelas |
| Manutenção por uma pessoa | Alta produtividade, notebooks e scripts curtos | Mais verboso | Mais lento de iterar | Baixa complexidade, mas precisa de servidor no caso do ClickHouse |
| Integração com MongoDB | `pymongo` direto; extensão DuckDB `mongo` (comunidade) | Driver oficial e Spring Data | Driver oficial | Só via conector/ETL ou tabela MongoDB do ClickHouse |
| Reuso do processor atual | Precisa de ponte (Java continua calculando estatísticas simples) | Mantém o código atual | Reescrita | Não substitui o processor |

Fatos relevantes:

- **[F] Benchmarks 2026 de Polars x pandas** (fornecedores e blogs, não suíte independente): Uvik reporta Polars 5,7x mais rápido que pandas em group-by e 3,75x em joins em 10M linhas; DuckDB 1.5 foi 4,2x mais rápido que pandas em group-by e foi o único motor que concluiu todas as consultas em 50M linhas com limite de 5,84 GiB. Fontes: [Uvik](https://uvik.net/blog/polars-vs-pandas/), [TildAlice/DEV](https://dev.to/tildalice/polars-vs-pandas-2026-benchmarks-that-actually-matter-aom), [Tech Insider](https://tech-insider.org/polars-vs-pandas-2026/), [Kanaries](https://docs.kanaries.net/articles/polars-vs-pandas).
- **[F] Pandas ainda tem o maior ecossistema**, e a recomendação geral é mantê-lo para trabalho com scikit-learn e outros pacotes PyData. Fonte: [Kanaries](https://docs.kanaries.net/articles/polars-vs-pandas).
- **[F] LightGBM -> Rust via ONNX** é um caminho testado por terceiros: treino em Python, conversão com `onnxmltools.convert_lightgbm` (`zipmap=False`), inferência com o crate `ort` (pykeio, binding do ONNX Runtime, Apache-2.0). Fonte: [Zenn, artigo japonês com ort 2.0.0-rc.10](https://zenn.dev/potato_iidx/articles/528b3a4a4002f9). Versões citadas podem estar desatualizadas; confirmar no crates.io.
- **[F] Inferência LightGBM em Rust sem FFI**: o crate `bosk` lê o formato de texto do LightGBM em Rust puro e declara paridade de até 1e-9 com LightGBM 4.6 nos testes do autor (crate de terceiros; validar antes de depender). Fonte: [docs.rs/bosk](https://docs.rs/crate/bosk/latest/source/Cargo.toml).
- **[F] Treelite 4.0+ não gera mais código C**: a geração de C migrou para o TL2cgen. Fonte: [docs TL2cgen/Treelite](https://tl2cgen.readthedocs.io/en/latest/_sources/index.rst.txt). Relevante se a opção for compilar a árvore em código.
- **[F] Tribuo** aceita modelos treinados em Python, como ONNX, e é recomendado para ML clássico em dados tabulares; **Smile** é leve e focado em estatística. Não há posicionamento para deep learning em nenhum dos dois (DJL ou DL4J cobrem isso). Fontes: [Java Magazine/Oracle](https://blogs.oracle.com/javamagazine/post/java-machine-learning-tribuo-python-scikit-learn), [HackerNoon](https://hackernoon.com/machine-learning-in-java-getting-started-with-deeplearning4j-tribuo-and-smile).
- **[F] DuckDB + MongoDB**: a extensão comunitária `mongo` lê coleções com `mongo_scan`, faz pushdown de filtro, projeção, limite e agregações, e na versão 0.3.0 ganhou escrita (`INSERT`, `COPY TO`). Foi escrita por um engenheiro da MongoDB, segundo a newsletter MotherDuck de fev/2026. Fontes: [DuckDB community extensions](https://www.duckdb.org/community_extensions/extensions/mongo), [MotherDuck fev/2026](https://motherduck.com/blog/duckdb-ecosystem-newsletter-february-2026/). Risco: é extensão da comunidade.
- **[F] ClickHouse x DuckDB**: ClickHouse para muitas máquinas e muitos usuários (TB a PB); DuckDB para uma máquina e um usuário (GB a centenas de GB). Fonte: [elest.io](https://blog.elest.io/clickhouse-vs-duckdb-which-analytical-database-for-embedded-vs-distributed-workloads-in-2026/) (blog, comparação geral).

### 1.2 Recomendação

**Python 3.12+ com Polars + DuckDB (sobre arquivos Parquet) + LightGBM + scikit-learn; PyTorch só se a fase sequencial justificar.** Motivo: é o ecossistema com mais ferramenta para dados e ML, o treino é curto e reprodutível, e o modelo sai como artefato pequeno (texto do LightGBM ou ONNX; ou, preferencialmente, tabelas) que o app Rust carrega sem Python.

- **[O]** Ingestão: `pymongo` lendo o coletor e gravando Parquet por patch/dia. A extensão DuckDB `mongo` é opção, mas começar pelo `pymongo` reduz risco de extensão comunitária.
- **[O]** Java fica onde está: o processor continua com as estatísticas simples e as APIs atuais. Não reescrever em Java o que é ML.
- **[O]** Rust: só consome o artefato. Não treina.
- **[O]** SQL (DuckDB) é a camada de agregação e features dentro do Python; ClickHouse não se justifica no volume atual (dezenas de milhares de partidas por patch, cerca de 46 KB cada, ou seja, da ordem de 1 a 2 GB brutos por patch antes de extrair features).

---

## 2. Abordagem de recomendação

### 2.1 Opções, do mais simples ao mais complexo

| Abordagem | Como funciona | Quando usar | Exportável para Rust |
|---|---|---|---|
| Regras + estatística condicional | Taxa de vitória e frequência de cada item condicionada a um "estado" (ex.: time inimigo com muito dano físico + 2 de CC + vantagem de ouro > X no minuto 15) com intervalo de confiança | Primeira versão; muito dado por célula | Trivial: tabela JSON/SQLite pequena |
| Árvores de decisão / gradient boosting por situação | LightGBM multiclasse ou ranking: entrada = estado da partida + campeão + runas; saída = próximo item ou score por item | Quando há features demais para tabela; dados suficientes | Sim: texto LightGBM (parser Rust, ex.: `bosk`) ou ONNX (`ort`) |
| Modelos sequenciais | Transformer/GRU sobre a sequência de compras e eventos por minuto (estilo next-item) | Só depois do baseline vencer; exige volume | ONNX |
| Filtragem colaborativa / team-aware | Modelo com contexto do time (ex.: Transformer com contexto de time) | Pesquisa; não é o primeiro passo | ONNX |

Literatura relevante (fontes):

- **[F] Team-aware item recommendation (Villa, Araujo, Cattan, Parra, 2020)**: Transformer com contexto de time para recomendação de itens em MOBA; o contexto foi essencial nos resultados. Fonte: [arXiv 2204.12750 (DraftRec, referência relacionada)](https://arxiv.org/pdf/2204.12750), [Araujo & Rios, MOBArecsys 2019](https://dparra.sitios.ing.uc.cl/pdfs/MOBArecsys2019-preprint.pdf).
- **[F] ICTC 2025**: 12.074 partidas ranqueadas solo (Challenger, Grandmaster, Master) via API da Riot, eventos de compra e venda minuto a minuto, field-aware factorization machines para recomendação de itens. É o estudo mais próximo do seu caso. Fonte: [ICTC 2025, P05-24](https://d2j16w31g89z0j.cloudfront.net/site/ictc2025/abs/P05-24.pdf).
- **[F] SBGames (estado minuto a minuto)**: reconstrução de estados por minuto usando só o que é observável até aquele minuto; útil para não vazar informação futura na validação. Fonte: [SBGames](https://sol.sbc.org.br/index.php/sbgames/article/view/45416).
- **[F] Sequencial em Dota 2** com RNN/Transformer para próximo item (arXiv 2201.08724): referência de método, não de jogo. Fonte: [arXiv 2201.08724](https://arxiv.org/pdf/2201.08724v1.pdf).

**Recomendação para começar:** (a) estatística condicional com intervalo de confiança como baseline; (b) LightGBM para ranquear itens dado o estado; (c) sequencial só se (b) não bater (a) de forma estável em holdout. **[O]**

### 2.2 Validação

- **Holdout por patch (temporal)**: treinar com patches anteriores e testar no seguinte. Nunca embaralhar aleatoriamente, porque o meta muda entre patches. **[O]**
- **Vazamento**: features só com dados observáveis até o minuto da recomendação (ouro, abates, nível, itens já comprados naquele minuto). Usar a linha do tempo com cuidado para não usar o resultado da partida. **[O]**, alinhado ao SBGames acima.
- **Métricas**:
  - Acerto do próximo item (top-1, top-3, top-5) contra o que o jogador comprou de fato. Medir com cuidado: o jogador pode ter comprado algo bom que não foi o "ótimo". **[O]**
  - Taxa de vitória condicional: entre partidas em que o jogador seguiu a recomendação versus não seguiu, com controle de estado. É observacional, então é viés de seleção; apresentar como indicativo. **[O]**
  - Calibração das probabilidades (reliability) e intervalo de Wilson para cada célula de regra. **[O]**
- **Mínimo de dados (opinião, sem fonte)**: para uma célula de regra, pelo menos algumas centenas de partidas por célula e por patch; abaixo disso, agrupar estados ou usar prior bayesiano. Para o LightGBM, dezenas de milhares de partidas por patch já é um volume razoável para o baseline. **[O]**
- **Estabilidade por patch**: se a taxa de vitória de um item muda muito entre patches, o modelo é por patch ou com peso decrescente no tempo. **[O]**

---

## 3. Dados e conformidade (Riot)

- **[F] Regras de chave**: chave de desenvolvimento expira em 24 h e não serve para produto público; chave pessoal não pode servir ao público (inclusive beta); chave de produção exige aprovação e é de um produto só. Fonte: [Riot Developer Portal, API keys](https://developer.riotgames.com/api-keys.html) e [Policies](https://developer.riotgames.com/policies.html) (última atualização indicada: 11/03/2025).
- **[F] Retenção de histórico**: em 2019 a Riot reduziu a retenção de Match History de 3 para 2 anos, com remoção contínua; **dados de timeline ficam 1 ano**. Fonte: [Riot DevRel, match history retention](https://www.riotgames.com/en/DevRel/match-history-retention-Change). **Atenção**: a fonte é de 2019; confirmar a regra vigente nos termos atuais.
- **[F] Não consegui confirmar** no texto atual da política de armazenamento, cache e uso de dados para ML. A página de policies remete aos Terms of Use (https://developer.riotgames.com/terms), que precisam ser lidos antes de publicar qualquer modelo ou dataset derivado.
- **[O]** Implicações: (a) se a timeline só fica 1 ano na Riot, o coletor precisa preservar o que já foi baixado e a política de retenção do próprio projeto precisa ser escrita; (b) o modelo publicado no app não deve embutir dados brutos de partida nem identificar jogadores; (c) não publicar o dataset bruto; (d) chave e token nunca no pacote do app Rust.

---

## 4. Skills e agentes de IA para desenvolver isso

Listagem apenas. Nada foi instalado. Skills de terceiros rodam código e pedem permissão; revisar antes de instalar.

| Nome | URL | O que faz | Risco |
|---|---|---|---|
| anthropics/skills (oficial) | https://github.com/anthropics/skills | Repositório oficial de skills; marketplace `/plugin marketplace add anthropics/skills`. Tem `docx`, `pdf`, `pptx`, `xlsx`, entre exemplos. Não encontrei skill de ciência de dados ou ML nele na checagem | Baixo; `xlsx` cobre planilhas, não o pipeline |
| K-Dense-AI/claude-scientific-skills | https://github.com/K-Dense-AI/claude-scientific-skills (citado em lista de awesome; checar) | Coleção de skills científicas (a lista cita 148+) | Médio: coleção grande de terceiros, validar cada skill |
| marimo-team/skills (marimo-notebook) | https://claudemarketplaces.com/skills/marimo-team/skills/marimo-notebook | Gera notebooks Marimo como arquivo Python puro, bom para versionar | Baixo a médio: terceiro, mas da equipe do Marimo |
| dakesan/marimo-cc | https://skillselion.com/plugin/dakesan/marimo-cc | Plugin de skills Marimo para análise exploratória | Médio: terceiro individual |
| supercent-io/skills-template (data-analysis) | https://claudemarketplaces.com/skills/supercent-io/skills-template/data-analysis | Gera relatório markdown com visão do dataset, estatísticas e insights | Médio: terceiro; contagem de instalações é autodeclarada |
| davila7 (senior-data-scientist) | https://claudemarketplaces.com/skills/davila7/claude-code-templates/senior-data-scientist | Foco em ML de produção além de notebook | Médio: terceiro |
| 404kidwiz (machine-learning-engineer) | https://claudemarketplaces.com/skills/404kidwiz/claude-supercode-skills/machine-learning-engineer | Scripts para pipelines scikit-learn, engenharia de features e fluxos Jupyter | Médio: terceiro |
| Diretório de skills de data science | https://claudemarketplaces.com/skills/category/data-science | Índice independente com centenas de skills | Diretório não afiliado à Anthropic; as contagens são do próprio diretório |

- **[F]** Não achei marketplace oficial de data science da Anthropic nas buscas; checar a documentação de plugins do Claude Code antes de confiar em qualquer listagem.
- **[O] Para o Codex e o Claude Code**: o que mais ajuda é um `AGENTS.md`/`CLAUDE.md` do projeto com as convenções (Polars, nomes de patch, validação temporal), mais um skill de "pipeline de dados e validação temporal" escrito à mão. Isso é mais seguro e mais útil do que instalar skill genérica.

---

## 5. Arquitetura

### 5.1 Componentes

```
[coletor Mongo] --(pymongo)--> [yoursummoner_ml]  (Python)
                                  |  ingestão -> Parquet por patch (validação de qualidade)
                                  |  features por minuto (sem vazamento)
                                  |  treino LightGBM + baseline condicional
                                  |  validação holdout por patch
                                  v
                       [pacote do modelo versionado]  (tabelas + texto LightGBM / ONNX + manifesto)
                                  |
                                  v
                       [app Rust no PC do jogador]  (carrega o pacote, sem Python)

[yoursummoner_processor] (Java/Spring)  -> mantém as estatísticas simples e as APIs atuais
```

### 5.2 Jobs e versionamento

- **Job por patch**: ao detectar um patch novo, roda ingestão -> features -> treino -> validação -> publicação. Reprocessar um patch é idempotente (mesmo input, mesmo output). **[O]**
- **Qualidade de dados**: checagens de schema, duplicatas por `matchId`, partidas com timeline incompleta, participantes != 10, campeões e itens fora do catálogo do patch. Quarentena em vez de descartar silenciosamente. **[O]**
- **Versionamento de modelo**: cada pacote tem `manifesto.json` com patch, data de treino, versão do schema de features, hash dos dados de entrada (contagem e intervalo de datas), métricas de holdout, versão do LightGBM/ONNX. Um pacote por patch e um "ativo" por canal. **[O]**
- **Compatibilidade com o app Rust**: o app declara a versão do schema de features que suporta; se o pacote for de outra versão, não carrega. **[O]**
- **Tamanho**: tabelas condicionais e modelo LightGBM pequenos (ordem de MB), para cabe no app. Confirmar com o modelo real. **[O]**

### 5.3 O processor atual

- **[O]** Recomendo **manter** o processor como serviço de estatísticas simples e de API (taxa de vitória por build, ordem de skills), porque já funciona e é Java. Ele pode ler as tabelas que o `yoursummoner_ml` publica, mas não treina modelo.
- **[O]** Absorver tudo no Java só faria sentido se a equipe for só Java; com ML, o custo de manter duas stacks compensa menos do que a ponte entre serviços.
- **[O]** Alternativa: mover as agregações do processor para o `yoursummoner_ml` depois que o pipeline estiver estável, deixando o processor só com a API. Decidir depois do baseline.

---

## 6. Top 3 próximos passos

1. **Esquema de dados e qualidade por patch**: definir o contrato de extração (Parquet por patch), as checagens de qualidade e a política de retenção da timeline (1 ano pela Riot, confirmar nos termos). Sem isso o resto não é confiável.
2. **Baseline com estatística condicional e validação temporal**: taxa de vitória e acerto do próximo item por estado, com holdout por patch e intervalo de Wilson. Esse número é o que o LightGBM precisa vencer.
3. **Primeiro modelo exportável**: LightGBM ranqueando itens com features sem vazamento, exportado como pacote versionado (manifesto + texto LightGBM ou ONNX) e carregado por um protótipo Rust, comparando com o baseline.

---

## Fontes

- Polars x pandas 2026: [Uvik](https://uvik.net/blog/polars-vs-pandas/), [TildAlice/DEV](https://dev.to/tildalice/polars-vs-pandas-2026-benchmarks-that-actually-matter-aom), [Tech Insider](https://tech-insider.org/polars-vs-pandas-2026/), [Kanaries](https://docs.kanaries.net/articles/polars-vs-pandas)
- LightGBM -> ONNX -> Rust: [Zenn](https://zenn.dev/potato_iidx/articles/528b3a4a4002f9); [bosk (docs.rs)](https://docs.rs/crate/bosk/latest/source/Cargo.toml)
- Treelite / TL2cgen: [TL2cgen docs](https://tl2cgen.readthedocs.io/en/latest/_sources/index.rst.txt)
- Tribuo / Smile: [Java Magazine/Oracle](https://blogs.oracle.com/javamagazine/post/java-machine-learning-tribuo-python-scikit-learn), [HackerNoon](https://hackernoon.com/machine-learning-in-java-getting-started-with-deeplearning4j-tribuo-and-smile)
- DuckDB + MongoDB: [DuckDB community extensions](https://www.duckdb.org/community_extensions/extensions/mongo), [MotherDuck fev/2026](https://motherduck.com/blog/duckdb-ecosystem-newsletter-february-2026/)
- ClickHouse x DuckDB: [elest.io](https://blog.elest.io/clickhouse-vs-duckdb-which-analytical-database-for-embedded-vs-distributed-workloads-in-2026/)
- Recomendação de itens em LoL e MOBA: [ICTC 2025](https://d2j16w31g89z0j.cloudfront.net/site/ictc2025/abs/P05-24.pdf); [SBGames](https://sol.sbc.org.br/index.php/sbgames/article/view/45416); [arXiv 2204.12750](https://arxiv.org/pdf/2204.12750); [MOBArecsys 2019](https://dparra.sitios.ing.uc.cl/pdfs/MOBArecsys2019-preprint.pdf); [arXiv 2201.08724](https://arxiv.org/pdf/2201.08724v1.pdf)
- Riot: [API keys](https://developer.riotgames.com/api-keys.html); [Policies](https://developer.riotgames.com/policies.html); [Match history retention (2019)](https://www.riotgames.com/en/DevRel/match-history-retention-Change); [Terms](https://developer.riotgames.com/terms)
- Skills: [anthropics/skills](https://github.com/anthropics/skills); [claudemarketplaces data-science](https://claudemarketplaces.com/skills/category/data-science); [marimo-notebook](https://claudemarketplaces.com/skills/marimo-team/skills/marimo-notebook); [dakesan/marimo-cc](https://skillselion.com/plugin/dakesan/marimo-cc); [data-analysis](https://claudemarketplaces.com/skills/supercent-io/skills-template/data-analysis); [senior-data-scientist](https://claudemarketplaces.com/skills/davila7/claude-code-templates/senior-data-scientist); [machine-learning-engineer](https://claudemarketplaces.com/skills/404kidwiz/claude-supercode-skills/machine-learning-engineer)

**Limitações:** benchmarks são de fornecedores e blogs; não rodei nada localmente. A regra de retenção da Riot vem de 2019 e a política atual de dados não foi confirmada. Nenhuma skill foi instalada.
