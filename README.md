# yoursummoner_processor

Microsserviço Spring Boot que lê partidas anônimas do coletor, agrega estatísticas de campeões por patch e posição e publica pacotes JSON para consumo por outros serviços. O domínio e os casos de uso ficam separados dos adapters de MongoDB, Data Dragon, agendamento e HTTP. O recálculo publica um snapshot completo por patch.

## Configuração e execução

Requer Java 25, Maven e acesso aos bancos MongoDB do processor e do coletor. Configure as variáveis de ambiente:

| Variável | Padrão | Uso |
|---|---|---|
| `MONGODB_URI` | `mongodb://127.0.0.1:27017/yoursummoner_processor` | Banco de saída do processor. |
| `COLLECTOR_MONGODB_URI` | `mongodb://127.0.0.1:27017/yoursummoner_collector` | Banco de origem, somente leitura. |

No PowerShell, com o JDK do workspace:

```powershell
$env:JAVA_HOME = 'D:\Workspace\Languages-Frameworks\jdk-25.0.4.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

O serviço escuta na porta 8082 com context path `/processor`. Por padrão, recalcula na inicialização e a cada hora. Limites e agendamento podem ser configurados em `application.yml`.

## API

- `GET /processor/knowledge/{patch}`: pacote completo, com ETag e suporte a gzip.
- `GET /processor/knowledge/{patch}/{championId}/{position}`: entrada individual.
- `GET /processor/status`: estado de cada patch.
- `GET /processor/actuator/health`: saúde da aplicação.

O contrato completo está em [docs/formato-pacote.md](docs/formato-pacote.md).

## Testes

Com Java 25 configurado, execute `mvn test` ou `mvn verify`. O perfil de teste desativa o agendador.

O processor apenas lê a coleção `matches` do coletor, aceita somente schema versão 2 e não lê, armazena nem expõe identificadores de jogadores. Os dados calculados são gravados no banco próprio do processor.
