# yoursummoner_processor

Microsserviço Spring Boot 4 / Java 25 com arquitetura hexagonal, que lê partidas anônimas do coletor e produz estatísticas por patch, campeão e rota.

Design: [spec](docs/superpowers/specs/2026-11-07-processor-design.md). Plano: [implementação](docs/superpowers/plans/2026-11-07-processor-v1.md).

Stack: OpenFeign, MongoDB, JUnit/Mockito/WireMock/ArchUnit/JaCoCo. Use o JDK 25 em `D:\Workspace\Languages-Frameworks\jdk-25.0.4.1+1` para `mvn test`, `mvn verify` ou `mvn spring-boot:run`.

## Regras

- A coleção `matches` do coletor é somente leitura; nunca escreva no banco do coletor.
- Nunca leia, guarde ou exponha identificadores de jogador.
- Não comite segredos ou `.env`; configure `MONGODB_URI` e `COLLECTOR_MONGODB_URI` por ambiente.
- No Windows, o Surefire e o plugin Spring Boot definem `jdk.net.unixdomain.tmpdir` para evitar falhas de loopback do JDK.
- Siga o plano e as instruções de desenvolvimento aplicáveis à sessão.

## Ferramentas do workspace

Scripts de rotina em `D:\Workspace\Scripts\yoursummoner` (ver `README.md` lá): `verify.ps1 -Repo <repo>` (build e testes com resumo), `stack.ps1 up|down|status` (Mongo, API, coletor, processor), `collector-check.ps1`, `board.ps1` (quadro). JDK 25 em `D:\Workspace\Languages-Frameworks\jdk-25.0.4.1+1`, Maven em `D:\Workspace\Languages-Frameworkspache-maven-3.10.0`. Agente delegado não roda git.
