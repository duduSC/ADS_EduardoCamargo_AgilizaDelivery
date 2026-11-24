# Agiliza Delivery — Backend (API)

> API REST em Spring Boot que guarda os dados e aplica **todas** as regras de negócio do Agiliza Delivery.
> A visão do projeto inteiro (problema, as três partes, repositórios e como rodar tudo junto) está no [README da raiz](../README.md).
> A fonte da verdade de negócio é o [Documento de Visão do Produto (DVP 3.1)](docs/DVP_Agiliza_Delivery_latest_version.md); as seções citadas abaixo (1.5.5, UC03...) são dele.

---

## Sumário

1. [O que é o backend](#1-o-que-é-o-backend)
2. [Pilha e versões](#2-pilha-e-versões)
3. [Arquitetura em camadas](#3-arquitetura-em-camadas)
4. [Modelo de domínio, máquinas de estado e regras](#4-modelo-de-domínio-máquinas-de-estado-e-regras)
5. [Endpoints](#5-endpoints)
6. [Tempo real (WebSocket/STOMP)](#6-tempo-real-websocketstomp)
7. [Segurança e formato de erro](#7-segurança-e-formato-de-erro)
8. [Como rodar](#8-como-rodar)
9. [Testes, Postman, health e problemas comuns](#9-testes-postman-health-e-problemas-comuns)
10. [Limitações e decisões PROVISÓRIAS](#10-limitações-e-decisões-provisórias)

---

## 1. O que é o backend

O Agiliza Delivery tem três partes: o **Backoffice Web** (operador e administradores), o **app do entregador** (Android) e este **backend**. Os dois clientes consomem a mesma API (DVP 1.5.1) e não calculam nada: exibem o que o servidor envia.

O backend:
- autentica os quatro perfis e separa os canais (o token do app não abre o painel, e o do painel não serve no app);
- importa os pedidos do provedor a cada 30 s (hoje, um simulador), com idempotência e repetição em falhas;
- geocodifica o endereço, calcula a distância de **rota de moto** e aplica a faixa de repasse;
- controla as máquinas de estado de entrega, lote, entregador, recibo e caixa;
- fecha o acerto financeiro: extrato, recibo, pagamento, caixa do dia e relatório do dia;
- isola os dados de cada loja (multi-tenant);
- avisa o painel em tempo real por WebSocket/STOMP.

## 2. Pilha e versões

| Camada | Tecnologia | Versão |
|---|---|---|
| Linguagem | Java | 21 |
| Build | Maven, pelo wrapper (`mvnw`) | 3.9.16 |
| Framework | Spring Boot (Web MVC, Data JPA, Security, WebSocket, Validation, Actuator, Scheduler) | 4.1.1 |
| | Spring Framework / Spring Security | 7.0.9 / 7.1.1 |
| Servidor | Tomcat embutido | 11.0.24 |
| Persistência | Hibernate (JPA) | 7.4.5.Final |
| Banco | PostgreSQL (produção, `compose.yaml`) / H2 em modo PostgreSQL (demonstração e testes) | 16 / 2.4.240 |
| Migrações | Flyway (`db/migration`, V1 e V2) | 12.4.0 |
| Token | JWT HS256 com jjwt | 0.13.0 |
| JSON | Jackson | 3.1.5 |
| Outros | Lombok; HikariCP | 1.18.46; 7.0.2 |
| Testes | JUnit Jupiter, Mockito, AssertJ, Spring Test (MockMvc) | 6.0.3, 5.23.0, 3.27.7 |

As versões vêm do Spring Boot 4.1.1; só a do jjwt é fixada no `pom.xml`. O DVP 1.5.2 cita Flyway 10.x; a versão gerida pelo Spring Boot 4.1.1 é a 12.4.0.

Serviços externos: **Google Maps Platform** (Routes API para a distância; Geocoding API para o endereço sem coordenada). Os dois são opcionais: sem chave, há uma reserva (seção 8.8).

## 3. Arquitetura em camadas

### 3.1 Pacotes

Em `src/main/java/br/com/agilizadelivery/`:

| Pacote | Responsabilidade |
|---|---|
| `controller/` | 14 controllers REST. Recebem o HTTP, validam a entrada (`@Valid`), tiram do token o usuário, a loja e o canal, e chamam um serviço. Não têm regra de negócio. A autorização por perfil e canal fica em `@PreAuthorize` |
| `dto/` (`request/`, `response/`, `DtoMapper`) | Contratos da API, separados das entidades. Um campo novo na entidade não vaza para o cliente |
| `service/` e `service/impl/` | Regras de negócio por caso de uso (entregas, lotes, entregadores, acerto, recibos, caixa, relatório, autenticação, loja, integração) e as transações (`@Transactional`) |
| `service/support/` | Regras compartilhadas, cada uma num lugar só: `TransicaoEntrega` (único ponto que muda o status de uma entrega, com histórico), `CalculadoraRepasse`, `LocalizacaoEntrega` (geocodificação, rota e revisão), `PoliticaCorrecao` (caixa aberto), `AjusteRepasse`, `EncerramentoLote`, `ImportacaoPedido`, `BuscadorTenant` (busca por loja e trava da loja), `CadastroUsuario`, `Provedores` e `Coordenadas` |
| `service/rota/` | Portas `ServicoDeRotas` e `ServicoDeGeocodificacao`, os caches LRU (`RotasComCache`, `GeocodificacaoComCache`) e a reserva sem chave (`DistanciaEmLinhaReta`) |
| `service/seguranca/` | Porta `ProtetorCpf` |
| `repository/` | 14 repositórios Spring Data JPA. As consultas de negócio filtram pela loja: pelo `estabelecimentoId` ou a partir de um registro já conferido como dela |
| `entity/model/` (`base/`, `embeddable/`, `enums/`) | As 14 entidades do DVP; `EntidadeBase` (id UUID, `criado_em`, `atualizado_em`) e `EntidadeDoEstabelecimento` (vínculo com a loja); a `Coordenada`; os 12 enums do dicionário de dados |
| `integracao/maps/` | Adapters da Google Routes API e da Geocoding API, e as propriedades (`agiliza.maps.*`) |
| `integracao/pedidos/` | Porta `ProvedorDePedidos`, o contrato neutro `PedidoRecebido`, o `SimuladorAdapter` (com `EnderecosPassoFundo`), o worker `CicloIntegracao` e o `AgendadorIntegracao` |
| `seguranca/` (`jwt/`) | JWT (`ServicoJwt`, `FiltroAutenticacaoJwt`, `UsuarioLogado`), expressões de perfil e canal (`Perfis`), cifra do CPF (`AesGcmProtetorCpf`), CORS e limite de corpo |
| `websocket/` | STOMP: autenticação no CONNECT, inscrição só nos tópicos da própria loja, publicação depois do commit |
| `exception/` (`handler/`) | Exceções de negócio (`AgilizaException` e subclasses), o catálogo `CodigoErro`, o `ApiErrorResponse` e os tratadores (Spring MVC, segurança, Tomcat) |
| `config/` | Montagem: segurança, WebSocket, portas de mapas, leitura do JSON, health e erros do Tomcat |
| `tempo/` | `HorarioLocal`: a única fonte de "agora" e "hoje" (São Paulo) |
| `demo/` | `CargaDemonstracao`: dados de desenvolvimento (D009) |

### 3.2 O caminho de uma requisição

Exemplo: o entregador marca uma entrega como ENTREGUE (`POST /api/entregador/entregas/{id}/status`).

```
 App ──HTTP──▶ FiltroAutenticacaoJwt      valida o token; UsuarioLogado(usuario, loja, perfil, canal)
                 │
                 ▼
               @PreAuthorize(ENTREGADOR)  perfil; o controller exige o canal APP
               EntregadorController       valida o JSON; a loja e o entregador vêm do token
                 │
                 ▼
               EntregadorServiceImpl      trava a loja; confere que a entrega é de um lote dele (senão, 404)
               EntregaServiceImpl         TransicaoEntrega: EM_ROTA → ENTREGUE pela máquina de estados (senão, 422)
                                          CalculadoraRepasse: reaplica a faixa vigente e congela o repasse
                                          EncerramentoLote: última entrega final → lote CONCLUIDO, entregador ONLINE
                 │
                 ▼
               Repositories (JPA)         grava entrega, histórico e lote numa transação
                 │ commit
                 ▼
               PublicadorStompTempoReal   STATUS_ALTERADO em /topic/estabelecimento/{loja}/entregas
```

Qualquer recusa vira uma exceção de negócio, que o `GlobalExceptionHandler` transforma em `ApiErrorResponse` (seção 7.6).

### 3.3 Por que foi feito assim

- **RNF05:** "nenhuma camada inferior acessa uma camada superior". O controller não tem regra, o serviço não recebe nem devolve objetos HTTP, e o repositório só acessa dados. A regra fica no serviço, e a mesma regra vale para o painel, para o app e para o worker.
- **Trocar o banco sem tocar na regra:** as mesmas entidades e repositórios rodam no H2 em memória (demonstração e testes) e no PostgreSQL 16 com Flyway (produção). A diferença está só em `application-h2.yaml` e `application-postgres.yaml`.
- **Trocar o serviço de rotas sem tocar na regra:** os serviços dependem da porta `ServicoDeRotas`. `IntegracaoConfig` escolhe, ao subir, a Google Routes API (com chave, rota de moto e reserva de carro) ou a linha reta × 1,3 (sem chave). O cache envolve qualquer uma delas. O cálculo do repasse é o mesmo nos dois casos.
- **Trocar o provedor de pedidos sem tocar na regra (padrão Adapter, DVP 1.5.6 e risco R01):** o worker só conhece a porta `ProvedorDePedidos` e o contrato neutro `PedidoRecebido`. Hoje roda o `SimuladorAdapter`; o `IFoodAdapter` entra no mesmo lugar, sem mudar a importação.
- **Uma regra, um lugar:** o status de uma entrega só muda em `TransicaoEntrega`, a faixa de repasse só é aplicada em `CalculadoraRepasse`, o "hoje" só vem de `HorarioLocal`, e as entidades compartilhadas entre os serviços são buscadas por loja em `BuscadorTenant`. Um bug de fuso (o servidor em UTC fazia o "hoje" virar amanhã depois das 21:00) foi corrigido centralizando o relógio em `HorarioLocal`; um teste impede o uso direto do relógio da JVM.

## 4. Modelo de domínio, máquinas de estado e regras

### 4.1 Entidades (DVP 1.5.3 e 1.5.4)

Diagrama: [`docs/img/der-modelo-logico.png`](docs/img/der-modelo-logico.png). Os nomes de tabela, coluna e enum seguem o dicionário de dados do DVP.

| Entidade | O que é |
|---|---|
| `ESTABELECIMENTO` | A loja parceira, raiz da multilocação: coordenada, limite de pedidos por lote, repasse de fora de área |
| `USUARIO` | Login (e-mail único, senha só como hash) e perfil: `ADMIN_GERAL`, `ADMIN_RESTAURANTE`, `OPERADOR` ou `ENTREGADOR`. Vinculado a uma loja, exceto o `ADMIN_GERAL` |
| `MOTOBOY` | O entregador: um para um com o `USUARIO` (de onde vêm o nome e a loja), CPF cifrado, disponibilidade, cadastro e última posição |
| `PEDIDO_EXTERNO` e `ITEM_PEDIDO` | Pedido do provedor com o JSON original (RNF13). O par (`provedor`, `id_externo`) é único: é a idempotência da HU03 |
| `ENTREGA` | A unidade logística e de cobrança: cliente e pagamento copiados, coordenada usada, distância, faixa, repasse, status, revisão e tempos |
| `ENTREGA_STATUS_HISTORICO` | Cada transição de status, com autor, coordenada e horário de origem |
| `ENTREGA_AJUSTE_VALOR` | Cada ajuste manual de repasse: valor anterior, novo, autor e motivo |
| `LOTE_ENTREGA` | Entregas despachadas juntas para um entregador |
| `DISTANCIA_PRECO` | Faixas de preço por km da loja: começam em 0, contíguas e sem sobreposição |
| `RECIBO` | O acerto de um entregador num período, ligado ao caixa do dia |
| `CAIXA` | O expediente da loja: um por dia. Fechado, torna o dia imutável |
| `CONFIGURACAO_INTEGRACAO` | O vínculo da loja com o provedor (`merchant_id`, ativo, último polling) |
| `FALHA_INTEGRACAO` | A fila de erro do worker (RNF04): etapa, tentativas e o JSON do pedido |

Enums: `StatusEntrega`, `StatusLote`, `StatusDisponibilidadeMotoboy`, `StatusCadastroMotoboy`, `StatusRecibo`, `StatusCaixa`, `StatusEstabelecimento`, `StatusFalhaIntegracao`, `EtapaFalhaIntegracao`, `MotivoRevisao`, `FormaPagamento` (`DINHEIRO`, `CREDITO`, `DEBITO`, `PIX`, `VALE`) e `PerfilUsuario`. `provedor` é texto (`VARCHAR(20)`), validado em `Provedores`: `IFOOD` ou `SIMULADOR`.

### 4.2 Máquinas de estado (DVP 1.4.4)

Toda transição é validada no backend. Uma transição inválida é recusada com um erro 4xx, em geral **422 `TRANSICAO_ESTADO_INVALIDA`**, e nunca é aceita em silêncio.

| Objeto | Transições |
|---|---|
| **Entrega** | `AGUARDANDO_DESPACHO` → `DESPACHADA` ou `CANCELADA`<br>`DESPACHADA` → `EM_ROTA`, `AGUARDANDO_DESPACHO` (devolução do lote) ou `CANCELADA`<br>`EM_ROTA` → `ENTREGUE`, `FALHA` (com motivo) ou `CANCELADA`<br>`ENTREGUE`, `FALHA` e `CANCELADA` são finais |
| **Lote** | `DESPACHADO` → `EM_ROTA` → `CONCLUIDO` (todas as entregas finais)<br>`DESPACHADO` → `CANCELADO` (devolução, ou todas canceladas antes da saída) |
| **Entregador (disponibilidade)** | `OFFLINE` ⇄ `ONLINE`; `ONLINE` → `EM_ROTA` ao receber o lote; `EM_ROTA` → `ONLINE` ao concluir ou ter o lote devolvido. Pelo app, ele só escolhe `ONLINE` ou `OFFLINE` |
| **Entregador (cadastro)** | `ATIVO` ⇄ `BLOQUEADO` |
| **Recibo** | `GERADO` → `PAGO`; `GERADO` ou `PAGO` → `CANCELADO`, só com o caixa aberto |
| **Caixa** | `ABERTO` → `FECHADO` → `REABERTO` → `FECHADO`. Reabrir é só do `ADMIN_RESTAURANTE`, com motivo |
| **Loja** | `ATIVO` ⇄ `INATIVO`. Inativa, ninguém dela entra e o worker não a consulta (HU12) |
| **Falha de integração** | `PENDENTE` → `REPROCESSADA` ou `DESCARTADA` |

No código: a tabela da entrega está em `StatusEntrega.podeTransitarPara`, aplicada só por `TransicaoEntrega`, que também grava o histórico. O encerramento do lote está em `EncerramentoLote`.

### 4.3 Regras principais

**Repasse (DVP 1.5.5)**
- É apurado **por entrega**, nunca por lote, pela distância de rota loja → cliente.
- Vale a faixa ativa com `de_km ≤ distância < ate_km`. Acima da última faixa, vale o `valor_repasse_fora_de_area` da loja, sem revisão.
- O valor é provisório enquanto a entrega anda. No estado final, a faixa **vigente** é reaplicada e o valor **congela**: mudar a tabela depois não afeta entregas finalizadas (HU10).
- `FALHA` paga integral. Cancelada antes de sair da loja paga zero; durante o percurso, integral.
- **Ajuste manual** (UC03 A1): só em entrega finalizada, **fora de recibo** (`recibo_id` nulo), com o caixa do dia aberto e motivo obrigatório. Cada ajuste fica em `ENTREGA_AJUSTE_VALOR`. Para corrigir uma entrega de um recibo, cancela-se o recibo antes (UC03 A2).
- **Recibo:** soma as entregas do período em estado final, sem revisão pendente e ainda sem recibo (passo 8). Uma entrega só está em um recibo por vez.

**Caixa (UC03 e 1.4.4)**
- Um por loja por dia. Aberto ou reaberto, aceita correções. **Fechado, é imutável:** recibo, pagamento, cancelamento, ajuste e contestação daquele dia são recusados (422 `CAIXA_FECHADO` ou `RECIBO_INALTERAVEL`).
- O caixa de uma entrega é o do dia em que ela foi finalizada (`PoliticaCorrecao`). Um recibo que alcança um dia de caixa fechado também é recusado.

**Revisão pendente (`pendente_revisao`)**
- Só três motivos, nenhum outro: `SEM_COORDENADA` e `SEM_DISTANCIA` (falha da Google Maps Platform) e `CONTESTACAO_ENTREGADOR` (UC08 A1).
- Entrega em revisão não entra em lote nem em recibo.
- Sai da revisão pelo reposicionamento do pino (recalcula a distância e o repasse) ou pela análise da contestação, que mantém o valor ou leva ao ajuste.

**Multi-tenant (HU13 e DVP 1.2.1)**
- Nas operações da loja, ela vem **sempre do token**, nunca do corpo nem da URL. Só o `ADMIN_GERAL` informa o id de um restaurante, para administrá-lo.
- Recurso de outra loja responde 404, igual a um id inexistente, para não revelar que existe (`BuscadorTenant`).
- O tempo real também é por loja (seção 6).

**Concorrência (T014)**
- Toda transação de escrita de uma loja começa travando a linha da loja (`SELECT ... FOR UPDATE`, `BuscadorTenant.travarLoja`). Despacho, status, recibo, caixa, ajuste e contestação da mesma loja rodam um depois do outro.
- Assim, dois despachos simultâneos não usam a mesma entrega, e nada escreve num caixa que acabou de fechar.
- A espera tem limite de 10 s; depois, 409 `DADO_DUPLICADO`, que pode ser repetido. A posição do GPS e a entrega nova não travam.

**Relatório do dia (D013)**
- Decisão de escopo do Eduardo: um resumo simples, sem editar o DVP 1.2.4, que deixa "relatórios analíticos avançados e BI" fora do MVP.
- Considera as entregas finalizadas no dia (horário de São Paulo). Todos os totais saem do servidor, e valor indefinido (`null`) não vira zero (seção 10).

## 5. Endpoints

Perfis (D004, DVP 3.1):
- **Operação:** `OPERADOR` ou `ADMIN_RESTAURANTE`.
- **Loja:** só `ADMIN_RESTAURANTE`.
- **Plataforma:** só `ADMIN_GERAL`.

Os três exigem token do canal **BACKOFFICE**. **Entregador** exige o perfil `ENTREGADOR` e o canal **APP**. Perfil ou canal errado responde 403 `ACESSO_NEGADO`; sem token, 401 `NAO_AUTENTICADO`.

**Públicos**

| Método | Caminho | O que faz |
|---|---|---|
| POST | `/api/auth/login` | Login (UC07): `{ loginEmail, senha, canal }`, com `canal` `BACKOFFICE` (padrão) ou `APP` |
| POST | `/api/auth/refresh` | Novo par de tokens a partir do refresh token |
| GET | `/actuator/health` | Saúde da aplicação e do banco, sem detalhes |

**Plataforma** (`ADMIN_GERAL`, BACKOFFICE)

| Método | Caminho | O que faz |
|---|---|---|
| GET | `/api/estabelecimentos` | Restaurantes parceiros (`?status=ATIVO`) |
| POST | `/api/estabelecimentos` | Cadastra o restaurante com o administrador inicial (UC06, HU11) |
| GET | `/api/estabelecimentos/{id}` | Um restaurante |
| PATCH | `/api/estabelecimentos/{id}/status` | Ativa ou inativa (HU12) |

**Loja** (`ADMIN_RESTAURANTE`, BACKOFFICE)

| Método | Caminho | O que faz |
|---|---|---|
| PATCH | `/api/estabelecimento` | Parâmetros da loja: limite de pedidos por lote, repasse de fora de área e telefone |
| GET, PUT | `/api/faixas-preco` | Tabela de faixas; o PUT troca a tabela inteira numa transação (UC05, HU10) |
| GET, PUT | `/api/integracoes` | Vínculo com o provedor (`provedor`, `merchantId`, `ativo`); liga e desliga o simulador |
| GET, POST | `/api/usuarios` | Usuários da loja |
| POST | `/api/motoboys` | Cadastra entregador (cria o usuário `ENTREGADOR`) |
| PUT | `/api/motoboys/{id}` | Edita o entregador |
| POST | `/api/motoboys/{id}/bloquear`, `/desbloquear` | Cadastro `BLOQUEADO` ou `ATIVO` |
| POST | `/api/caixas/{id}/reabrir` | Reabre um caixa fechado, com motivo (UC03 A3) |
| POST | `/api/falhas-integracao/{id}/reprocessar`, `/descartar` | Trata a fila de erro da integração |

**Operação** (`OPERADOR` ou `ADMIN_RESTAURANTE`, BACKOFFICE)

| Método | Caminho | O que faz |
|---|---|---|
| GET | `/api/estabelecimento` | Dados da própria loja |
| GET | `/api/entregas` | Sem filtro: pendentes de despacho sem revisão. `?status=` filtra; `?pendenteRevisao=true` é a fila de revisão |
| POST | `/api/entregas` | Entrega avulsa (UC01 A2) |
| GET | `/api/entregas/{id}` | Uma entrega |
| POST | `/api/entregas/{id}/pronto` | Cozinha concluiu (`pronto_para_despacho_em`) |
| PATCH | `/api/entregas/{id}/coordenada` | Reposiciona o pino; recalcula distância e repasse (UC01 A1, R04) |
| POST | `/api/entregas/{id}/cancelar` | Cancela, com motivo (UC02 A3) |
| POST | `/api/entregas/{id}/revisao/resolver` | Analisa a contestação do entregador |
| POST, GET | `/api/entregas/{id}/ajustes` | Ajuste manual de repasse e o histórico (UC03 A1) |
| POST | `/api/lotes` | Despacha um lote para um entregador ONLINE |
| GET | `/api/lotes`, `/api/lotes/{id}` | Lotes (`?status=`) e um lote |
| GET | `/api/lotes/{id}/rota` | Entregas do lote na ordem de paradas |
| POST | `/api/lotes/{id}/devolver` | Devolve o lote ainda não iniciado (UC01 A4) |
| GET | `/api/motoboys`, `/api/motoboys/{id}` | Frota (`?statusDisponibilidade=`, `?statusCadastro=`) |
| GET | `/api/acertos` | Extrato de um entregador no período (`motoboyId`, `inicio`, `fim`) |
| POST | `/api/recibos` | Gera o recibo do período |
| GET | `/api/recibos`, `/api/recibos/{id}` | Recibos (`?motoboyId=`, `?caixaId=`) e um recibo |
| POST | `/api/recibos/{id}/pagar` | PIX confirmado: `GERADO` → `PAGO` |
| POST | `/api/recibos/{id}/cancelar` | Cancela, com o caixa aberto (UC03 A2) |
| GET | `/api/caixas/atual` | Caixa do dia |
| GET | `/api/caixas` | Histórico (`dataInicio`, `dataFim`; sem eles, os últimos 30 dias) |
| POST | `/api/caixas`, `/api/caixas/{id}/fechar` | Abre e fecha o caixa |
| GET | `/api/relatorios/dia` | Relatório do dia (`?data=AAAA-MM-DD`; sem data, hoje em São Paulo) |
| GET | `/api/falhas-integracao` | Fila de erro (`?status=`, padrão `PENDENTE`) |

**Entregador** (`ENTREGADOR`, APP; o entregador é sempre o do token)

| Método | Caminho | O que faz |
|---|---|---|
| GET | `/api/entregador/me` | Cadastro e disponibilidade |
| PATCH | `/api/entregador/disponibilidade` | `ONLINE` ou `OFFLINE` |
| PATCH | `/api/entregador/posicao` | Última posição GPS, só em `ONLINE` ou `EM_ROTA` (RNF07) |
| GET | `/api/entregador/lote-ativo` | Lote despachado ou em rota, com entregas e itens; 204 sem lote |
| POST | `/api/entregador/lotes/{id}/iniciar` | Saída da loja: lote e entregas vão para `EM_ROTA` |
| POST | `/api/entregador/entregas/{id}/status` | `ENTREGUE` ou `FALHA` (com motivo) |
| POST | `/api/entregador/entregas/{id}/contestar` | Contesta o valor (UC08 A1) |
| GET | `/api/entregador/acerto` | Acerto do dia (ou de `inicio`/`fim` num único dia) e situação do caixa |
| GET | `/api/entregador/recibos` | Os próprios recibos, do mais recente para o mais antigo (D013) |
| GET | `/api/entregador/recibos/{id}` | Um recibo com as entregas; o de outro entregador responde 404 |
| PATCH | `/api/motoboys/{id}/posicao` | **Obsoleto** desde a T009 (use `/api/entregador/posicao`); só aceita o próprio id |

A coleção Postman (seção 9.2) tem um request por endpoint, com um corpo de exemplo.

## 6. Tempo real (WebSocket/STOMP)

- **Endpoint:** `/ws` (WebSocket, protocolo STOMP, broker simples em memória). As origens de navegador aceitas são as mesmas do CORS.
- **Autenticação:** no frame `CONNECT`, cabeçalho `Authorization: Bearer <token de acesso>`. Sem token válido, a conexão é recusada.
- **Quem assina:** só o painel da loja, ou seja, `OPERADOR` ou `ADMIN_RESTAURANTE` com token do BACKOFFICE.
- **Tópicos**, só os exatos da loja do token, sem curingas:

| Tópico | Tipos de evento | Conteúdo (`payload`) |
|---|---|---|
| `/topic/estabelecimento/{id}/entregas` | `CRIADA`, `DESPACHADA`, `STATUS_ALTERADO`, `DEVOLVIDA` | a entrega (`EntregaResponse`) |
| `/topic/estabelecimento/{id}/frota` | `POSICAO_ATUALIZADA`, `DISPONIBILIDADE_ALTERADA` | o entregador (`MotoboyResponse`) |

- **Mensagem:** `{ estabelecimentoId, tipo, payload }`.
- O evento sai **depois do commit**: quem refaz a consulta ao recebê-lo já lê o dado novo, e um rollback não publica nada.
- **Regras de segurança:**
  - o cliente nunca publica; `SEND` ou inscrição fora da lista responde com um frame `ERROR` sem detalhe interno, e a sessão fecha;
  - o HTTP comum em `/ws` responde no `ApiErrorResponse`.
- O app do entregador não usa WebSocket: consulta o lote ativo periodicamente (D008).

## 7. Segurança e formato de erro

### 7.1 Autenticação e JWT
- `POST /api/auth/login` devolve `{ token, refreshToken, usuario: { usuarioId, estabelecimentoId, motoboyId, nome, perfil } }`.
- Credencial errada: 401 `CREDENCIAIS_INVALIDAS`, com mensagem genérica, sem dizer se o erro é no e-mail ou na senha (UC07 E1).
- Loja inativa: 403 `ESTABELECIMENTO_INATIVO`. Usuário inativo ou entregador bloqueado: 403 `USUARIO_BLOQUEADO`.
- **Token de acesso:**
  - JWT HS256, chave `JWT_SECRET` em Base64 com 32 bytes ou mais (sem ela, a aplicação não sobe);
  - emissor `agilizadelivery`, validade de 3 h (`JWT_ACCESS_TTL`);
  - claims `sub` (usuário), `estabelecimentoId` (ausente para `ADMIN_GERAL`), `perfil`, `canal` e `tipo`.
- **Refresh token:** 30 dias (`JWT_REFRESH_TTL`). O `tipo` (`ACESSO` ou `REFRESH`) impede usar um no lugar do outro. O refresh devolve um par novo; o antigo vale até expirar.
- A API é stateless: sem sessão, sem cookie, sem CSRF. Nenhum token é logado.

### 7.2 Canais (UC07 E4)
- O token só vale no canal para o qual foi emitido.
- O `ENTREGADOR` entra só pelo app (`canal: APP`). Os demais perfis entram só pelo backoffice (`BACKOFFICE`, o padrão).
- As expressões de `Perfis` exigem BACKOFFICE; o `EntregadorController` exige APP.

### 7.3 Senhas e CPF (RNF02 e RNF07)
- **Senhas:** só como hash BCrypt, custo 10, com até 72 bytes.
- **CPF:** cifrado com AES-256-GCM (IV aleatório) e indexado por HMAC-SHA256, um índice cego usado só para a unicidade.
  - O CPF nunca volta pela API: não existe operação de decifrar.
  - As chaves são `AGILIZA_CPF_CHAVE_CIFRA` (32 bytes) e `AGILIZA_CPF_CHAVE_HASH` (32 bytes ou mais). Trocar a de cifra torna ilegíveis os CPFs gravados.

### 7.4 CORS e entradas
- **CORS:** só as origens de `CORS_ORIGENS`, separadas por vírgula. No perfil `h2`, `http://localhost:5173`; no `postgres`, nenhuma até ser configurada. Nunca `*`.
  - Métodos GET, POST, PUT, PATCH, DELETE e OPTIONS; cabeçalhos `Authorization` e `Content-Type`.
  - Origem fora da lista: 403 `ACESSO_NEGADO`.
- **Corpo:** até 1 MB; acima disso, 413.
- **JSON de entrada endurecido:**
  - decimal com expoente absurdo: 400;
  - data e hora com `Z` no fim valem o instante, convertido para São Paulo.
- **Actuator:** só `/actuator/health` é exposto e público; o resto é negado.

### 7.5 Isolamento entre lojas
- A loja sai do token em todas as consultas operacionais (seção 4.3).
- Há testes de acesso cruzado em REST e em WebSocket (`IsolamentoEntreLojasTest`, `WebSocketSegurancaTest`).

### 7.6 Formato de erro: `ApiErrorResponse`
Todo erro, de qualquer camada (controller, segurança, Tomcat, WebSocket), sai no mesmo formato, sem stacktrace nem detalhe interno:

```json
{
  "timestamp": "2026-11-22T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "codigo": "RECURSO_NAO_ENCONTRADO",
  "message": "O recurso solicitado não existe.",
  "path": "/api/nao-existe"
}
```

`campos` (`[{ campo, mensagem }]`) só aparece em erro de validação. O cliente decide pela combinação de `status` e `codigo` (`CodigoErro`):

| Status | Códigos |
|---|---|
| 400 | `DADOS_INVALIDOS` (validação, com `campos`), `PARAMETRO_INVALIDO` (parâmetro ou caminho), `CORPO_REQUISICAO_INVALIDO` (JSON malformado) |
| 401 | `NAO_AUTENTICADO` (token ausente, inválido ou expirado), `CREDENCIAIS_INVALIDAS` |
| 403 | `ACESSO_NEGADO`, `ESTABELECIMENTO_INATIVO`, `USUARIO_BLOQUEADO` |
| 404 | `RECURSO_NAO_ENCONTRADO` (também para recurso de outra loja), `MOTOBOY_NAO_ENCONTRADO` |
| 405, 406, 413, 415 | `RECURSO_NAO_ENCONTRADO`, `PARAMETRO_INVALIDO`, `CORPO_REQUISICAO_INVALIDO` |
| 409 | `DADO_DUPLICADO`, `CNPJ_JA_CADASTRADO`, `CPF_JA_CADASTRADO`, `EMAIL_JA_CADASTRADO`, `CAIXA_JA_ABERTO`, `PEDIDO_JA_IMPORTADO` |
| 422 | `REGRA_NEGOCIO_VIOLADA`, `TRANSICAO_ESTADO_INVALIDA`, `CAIXA_FECHADO`, `RECIBO_INALTERAVEL`, `LIMITE_LOTE_EXCEDIDO`, `MOTOBOY_INDISPONIVEL`, `ENTREGA_INAPTA_LOTE`, `ENTREGA_INAPTA_RECIBO`, `FAIXA_PRECO_INVALIDA`, `AJUSTE_VALOR_NAO_PERMITIDO` |
| 502 | `INTEGRACAO_EXTERNA_ERRO` |
| 500, 503 | `ERRO_INTERNO_SERVIDOR` (503: banco indisponível) |

O log também não leva dado sensível: token, senha, CPF, valor rejeitado pela validação e linhas do banco ficam de fora.

## 8. Como rodar

### 8.1 Pré-requisitos
- **JDK 21.** O Maven vem pelo wrapper (`./mvnw`; no Windows, `mvnw.cmd`). Sem JDK, dá para rodar num container Maven (seção 8.5).
- **Docker**, para o PostgreSQL (`compose.yaml`) ou para rodar sem JDK.
- **Opcional:**
  - uma chave da Google Maps Platform com a Routes API e a Geocoding API ativas;
  - o Postman.

A aplicação sobe em `http://localhost:8080`. Para outra porta, use `SERVER_PORT=8081`.

### 8.2 Variáveis de ambiente e `.env`
`.env.example` lista todas as variáveis lidas pelos YAML e pelo `compose.yaml`, com finalidade, padrão e formato. Copie para `.env` (ignorado pelo git) e preencha; nunca ponha segredo real no `.env.example`.

- `docker compose up -d` lê o `.env` da pasta sozinho (`DB_NAME`, `DB_USER`, `DB_PASSWORD`). `DB_PASSWORD` é obrigatória: sem ela o compose não sobe.
- A aplicação **não** lê o `.env`. Exporte as variáveis antes de subir, ou cadastre-as na configuração de execução da IDE (no IntelliJ, o campo *Environment variables* aceita o conteúdo do arquivo). No bash:
  ```bash
  set -a; . ./.env; set +a; ./mvnw spring-boot:run
  ```
- Variável definida **vazia** anula o padrão do YAML (`JWT_SECRET=` vazio impede até o perfil `h2` de subir). Por isso as opcionais vêm comentadas no arquivo.

| Variável | Para quê | Padrão |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `h2` ou `postgres` | `h2` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão do perfil `postgres` | sem padrão (obrigatórias no `postgres`) |
| `DB_NAME` | Banco criado pelo `compose.yaml` | `agiliza` |
| `JWT_SECRET` | Chave do JWT (Base64, 32+ bytes) | só no `h2`, de desenvolvimento |
| `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` | Validade dos tokens | `3h`, `30d` |
| `AGILIZA_CPF_CHAVE_CIFRA`, `AGILIZA_CPF_CHAVE_HASH` | Chaves do CPF, em Base64: 32 bytes a de cifra, 32 ou mais a de hash | só no `h2`, de desenvolvimento |
| `CORS_ORIGENS` | Origens do navegador | `http://localhost:5173` no `h2`; vazio no `postgres` |
| `GOOGLE_MAPS_API_KEY` | Routes e Geocoding | vazio (reserva sem chave) |
| `AGILIZA_MAPS_MODO_VIAGEM` | `TWO_WHEELER` ou `DRIVE` | `TWO_WHEELER` |
| `AGILIZA_DEMO`, `AGILIZA_DEMO_SENHA` | Carga de demonstração e a senha dela | `true` no `h2`; `agiliza123` |
| `AGILIZA_WORKER` | Worker de integração (simulador) | `true` no `h2`; `false` no `postgres` |

### 8.3 Perfil `h2`: demonstração, sem nada instalado além do JDK

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Sem nenhuma variável, sobe no perfil `h2`:
- **Banco:** H2 em memória, em modo PostgreSQL, recriado a cada início. O esquema vem do Hibernate (`create-drop`), sem Flyway.
- **Chaves:** JWT e CPF com valores só de desenvolvimento.
- **Ligados:** CORS para `http://localhost:5173`, a carga de demonstração e o worker do simulador.

### 8.4 Perfil `postgres`

```bash
docker compose up -d            # PostgreSQL 16 na porta 5432; exige DB_PASSWORD no .env
set -a; . ./.env; set +a        # DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET, AGILIZA_CPF_CHAVE_*...
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
```

- **Esquema:** vem das migrações Flyway (`V1__schema_inicial.sql` e `V2__indices_de_consulta.sql`). O Hibernate só confere o mapeamento (`validate`).
- **Obrigatórias:** `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `AGILIZA_CPF_CHAVE_CIFRA` e `AGILIZA_CPF_CHAVE_HASH`.
- **Desligados por padrão:**
  - a carga de demonstração (`AGILIZA_DEMO=true` liga; ela é idempotente);
  - o worker (`AGILIZA_WORKER=true` liga);
  - o CORS (configure `CORS_ORIGENS`).

### 8.5 Docker, sem JDK
O mesmo perfil `h2`, dentro da imagem oficial do Maven, como no README da raiz:

```bash
cp .env.example .env    # preencha; veja os comentários no arquivo
docker run --rm -it --name agiliza-backend -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=h2 -v "${PWD}:/app" -v agiliza_m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn spring-boot:run
```

Aqui quem lê o `.env` é o Docker (`--env-file`). O volume `agiliza_m2` guarda as dependências entre execuções.

### 8.6 Carga de demonstração (D009): SÓ DESENVOLVIMENTO

> **Aviso:** credenciais fixas, públicas e só para desenvolvimento. Nunca ligue a carga em produção.

Propriedade `agiliza.demo.carga-inicial` (`AGILIZA_DEMO`): `true` no perfil `h2`, `false` no padrão e no `postgres`.

| Perfil | E-mail | Canal do login |
|---|---|---|
| ADMIN_GERAL | `admin@agiliza.dev` | `BACKOFFICE` |
| ADMIN_RESTAURANTE | `loja@agiliza.dev` | `BACKOFFICE` |
| OPERADOR | `operador@agiliza.dev` | `BACKOFFICE` |
| ENTREGADOR | `entregador1@agiliza.dev` | `APP` |
| ENTREGADOR | `entregador2@agiliza.dev` | `APP` |

Senha de todos: `agiliza123` (troque com `AGILIZA_DEMO_SENHA`). No banco ela fica só como hash BCrypt.

O que é criado:
- a loja "Pizzaria Demo": ATIVO, Av. Brasil Centro, 500, centro de Passo Fundo/RS, até 5 pedidos por lote e repasse fora de área de R$ 20,00;
- dois entregadores ATIVOS e OFFLINE, com CPFs fictícios gravados cifrados;
- as faixas 0–3 km = R$ 9,00, 3–5 km = R$ 12,00 e 5–8 km = R$ 15,00;
- o caixa do dia ABERTO;
- três entregas avulsas AGUARDANDO_DESPACHO (PIX, DINHEIRO e CREDITO);
- a integração `SIMULADOR` da loja, **inativa** (`merchantId` `demo-simulador`).

A carga passa pelos serviços e é idempotente: reiniciar sobre um PostgreSQL já carregado não duplica nada.

### 8.7 Simulador de pedidos (T011 e T015)
O worker de integração (DVP 1.5.6) roda a cada 30 s quando `agiliza.integracao.worker.habilitado` (`AGILIZA_WORKER`) é `true`. Ele vem ligado no perfil `h2` e desligado nos outros.

Para começar a apresentação, o admin da loja liga a integração:

```
PUT /api/integracoes   (token de loja@agiliza.dev)
{"provedor": "SIMULADOR", "merchantId": "demo-simulador", "ativo": true}
```

Na coleção, o request é Loja → "Ligar o simulador de pedidos". Para desligar, use `"ativo": false`.

**O que ele gera:**
- A cada ciclo, de 0 a 2 pedidos fictícios, com cliente, itens e pagamento (no dinheiro, às vezes com troco).
- O endereço é sorteado de uma lista fixa de 32 **endereços reais** de Passo Fundo (`EnderecosPassoFundo`).
  - Rua, faixa de número, bairro e CEP foram conferidos na base dos Correios (pelo ViaCEP).
  - Os bairros são o Centro, onde fica a loja, e seis bairros próximos; o centro de cada um fica a até 2,7 km da loja, em linha reta.
  - O formato é `Rua Morom, 980 - Centro, Passo Fundo - RS, 99010-030`.
- O sorteio não repete o endereço de uma entrega da loja ainda no mapa (aguardando, despachada ou em rota), nem dois pedidos do mesmo ciclo.

**Com e sem chave do Google:**
- **Com `GOOGLE_MAPS_API_KEY`:** o pedido vai sem coordenada. O backend geocodifica o endereço (DVP 1.5.6, passo 5), e o km é o da rota de moto até ele.
- **Sem a chave (D009):** não há geocodificação. Para a entrega não cair em revisão, vai o centro do bairro no OpenStreetMap, deslocado até 300 m.
  - Esse caminho é **PROVISÓRIO**: o pino e o km são aproximados e não saem do endereço exato.

**Depois de gerado:**
- Cada pedido vira uma entrega AGUARDANDO_DESPACHO, publicada em `/topic/estabelecimento/{id}/entregas` com o tipo `CRIADA`.
- O simulador para de gerar quando a loja tem 15 ou mais entregas aguardando despacho.
- Quando a importação de um pedido falha, ela é repetida até 5 vezes com recuo exponencial (RNF04). Esgotadas as tentativas, a falha vai para `GET /api/falhas-integracao`, e o ciclo continua.
- O mesmo pedido nunca é importado duas vezes (HU03).

### 8.8 Google Maps: rota de moto e reserva em linha reta

**Com `GOOGLE_MAPS_API_KEY`:**
- A distância é a do percurso viário da **moto** (DVP 1.5.5, passo 1): a Routes API recebe `"travelMode": "TWO_WHEELER"`.
- Propriedade `agiliza.maps.modo-viagem` (`AGILIZA_MAPS_MODO_VIAGEM`): `TWO_WHEELER` (padrão) ou `DRIVE`. Outro valor impede a subida.
- Onde o Google não tem rota de moto para o trecho, o backend tenta **uma vez** com `DRIVE` e usa esse km. Isso vale para resposta sem rota e para HTTP 400 (modo indisponível na região).
- Chave recusada (401/403), cota (429), erro do Google (5xx) e timeout (5 s) não têm reserva, porque a segunda chamada falharia igual. A entrega vai para revisão (`SEM_DISTANCIA`).
- O log diz o modo usado, sem coordenadas do cliente: `Routes API: distancia calculada com travelMode TWO_WHEELER.`, ou `... sem rota com travelMode TWO_WHEELER; tentando DRIVE.` e `... com travelMode DRIVE (reserva).`
- O endereço sem coordenada é geocodificado pela Geocoding API (região `br`, idioma `pt-BR`). Se ela não achar, a entrega vai para revisão (`SEM_COORDENADA`), e o operador reposiciona o pino.
- **Custo (risco R06):** coordenada e distância ficam num cache LRU de 1000 entradas, por endereço normalizado e por par de coordenadas. Só acertos são guardados.

**Sem a chave (PROVISÓRIO, D009):**
- A distância é a linha reta (Haversine) × 1,3, com 2 casas. O log avisa ao subir: `GOOGLE_MAPS_API_KEY ausente: distância em linha reta (PROVISÓRIO, D009)`.
- Uma entrega sem coordenada vai para revisão (`SEM_COORDENADA`), porque não há geocodificação.

### 8.9 Fuso horário (PROVISÓRIO, T014)
O DVP não define fuso. As datas da API são locais, sem fuso (`2026-11-22T21:30:00`), sempre no horário de `America/Sao_Paulo`, qualquer que seja o fuso da máquina ou do container. Não é preciso passar `TZ` nem `-Duser.timezone` para subir.

- No código, "agora" e "hoje" vêm só de `tempo/HorarioLocal`; um teste barra `LocalDate.now()` e `LocalDateTime.now()` sem fuso.
- Os testes rodam com a JVM em UTC (`pom.xml`), como no container.
- Os horários do log seguem o fuso da JVM, e o `timestamp` dos erros sai em UTC (`Z`).

## 9. Testes, Postman, health e problemas comuns

### 9.1 Testes automatizados

```bash
./mvnw test                                    # a suíte inteira (Windows: mvnw.cmd test)
./mvnw test -Dtest='RelatorioControllerTest*'  # uma classe; o * inclui as classes @Nested
./mvnw test -DargLine="-Xmx512m"               # argumentos extras da JVM dos testes
```

- A suíte tem **886 testes** (T015) e roda com a JVM em UTC.
- Usa o H2 em modo PostgreSQL: cada classe Spring monta os próprios dados, com a carga de demonstração e o agendamento do worker desligados.
- Chamadas externas não saem da máquina: os testes da Google Maps usam um servidor HTTP falso local ou o `MockRestServiceServer`.

| Área | O que cobre (exemplos de classe) |
|---|---|
| Endpoints (MockMvc) | Cada grupo da seção 5, sucesso e erro, perfil e canal (`EntregaControllerTest`, `EntregadorControllerTest`, `EntregadorRecibosControllerTest`, `RelatorioControllerTest`...) |
| Regras de negócio | Máquinas de estado, repasse, caixa fechado, ajuste fora de recibo, revisão (`MaquinasDeEstadoTest`, `RegrasFinanceirasTest`, `PoliticaCorrecaoTest`, `CalculadoraRepasseTest`, `MotivoRevisaoTest`) |
| Isolamento | Acesso cruzado entre lojas e entre entregadores, em REST e WebSocket (`IsolamentoEntreLojasTest`, `WebSocketSegurancaTest`) |
| Segurança | JWT, refresh por canal, cifra do CPF, nada sensível no log ou no erro (`ServicoJwtTest`, `RefreshPorCanalTest`, `AesGcmProtetorCpfTest`, `SemVazamentoTest`) |
| Erros | Formato único, banco fora (503), unicidade, entradas extremas (`ContratoDeErrosTest`, `BancoIndisponivelTest`, `EntradasExtremasTest`) |
| Concorrência e desempenho | Trava da loja, sem N+1 nas telas principais (`ConcorrenciaTest`, `TravaDaLojaConvivenciaTest`, `DesempenhoConsultasTest`) |
| Integração | Simulador (endereços, com e sem chave, idempotência), worker com RNF04, Routes API com rota de moto e reserva, geocodificação (`SimuladorAdapterTest`, `CicloIntegracaoTest`, `RotaDeMotoTest`) |
| Tempo | Horário de São Paulo com a JVM em UTC e o relógio congelado (`FusoHorarioTest`) |
| Adversarial | Uma lista de "golpes" (caminhos, datas, corpos e tokens forjados) que nunca podem dar 5xx nem expor detalhe interno, mais trava presa e importação simultânea (`SegundaPassadaTest`) |

### 9.2 Coleção Postman / Insomnia

Importe os dois arquivos de `postman/`:
- `agiliza-delivery.postman_collection.json` (coleção v2.1);
- `agiliza-dev.postman_environment.json` (environment `agiliza-dev`, com `baseUrl` e as credenciais da seção 8.6).

**Fluxo ponta a ponta:**
- Selecione o environment e rode **só** a pasta **Fluxo ponta a ponta** no Collection Runner (no CLI, `-i "Fluxo ponta a ponta"`), com o backend recém-subido.
- Ela não depende das outras pastas: faz login, despacho, entrega, contestação, recibo, pagamento e fechamento do caixa.
- Como fecha o caixa do dia, para rodar de novo é preciso reiniciar o backend (perfil `h2`).

**Pastas avulsas** (Plataforma, Loja, Operação, Entregador):
- Têm um request por endpoint e dependem de variáveis: rode um request por vez, depois da pasta Autenticação.
- Não rode a coleção inteira: ela fecha o caixa antes do fluxo.
- Na pasta Entregador, "Meus recibos" guarda o recibo mais recente em `reciboEntregadorId`, usado por "Meu recibo (detalhe)".

No Insomnia, a importação traz os requests e o environment; os scripts que guardam tokens e ids usam a API `pm.*` do Postman e podem precisar de ajuste manual.

### 9.3 Saúde e falta de memória
`GET /actuator/health` é público, não mostra detalhes e consulta uma tabela: responde 503 `DOWN` com o banco fechado ou com o esquema perdido. Com o banco fora, a API responde 503 no `ApiErrorResponse`.

Sem memória, o H2 se desliga em emergência, e o banco em memória não volta sem reiniciar (`The database has been closed`, código 90098). Para o processo cair na hora, em vez de ficar de pé sem banco:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-XX:+ExitOnOutOfMemoryError"
```

Num container, use uma política de reinício e memória suficiente para o Maven e a aplicação. O banco em memória recomeça vazio, com a carga de demonstração.

### 9.4 Problemas comuns

| Sintoma | Causa e solução |
|---|---|
| Não sobe: `agiliza.jwt.segredo nao configurado` | `JWT_SECRET` ausente no `postgres` ou definida vazia. Defina uma chave Base64 de 32+ bytes, ou apague a linha vazia |
| Não sobe: erro em `agiliza.seguranca.cpf.*` | Chaves do CPF ausentes ou com tamanho errado no `postgres` (Base64: 32 bytes a de cifra, 32 ou mais a de hash) |
| Não sobe: `agiliza.maps.modo-viagem aceita TWO_WHEELER ou DRIVE` | Valor inválido em `AGILIZA_MAPS_MODO_VIAGEM` |
| `docker compose up` recusa | Falta `DB_PASSWORD` no `.env` |
| As variáveis do `.env` "não pegam" | A aplicação não lê o `.env`: exporte-as (seção 8.2) |
| 403 `ACESSO_NEGADO` no navegador | Origem fora de `CORS_ORIGENS`, ou token de outro canal (o do app no painel, ou o contrário) |
| 401 `NAO_AUTENTICADO` | Token ausente, expirado (3 h) ou de refresh no lugar do de acesso: use `POST /api/auth/refresh` ou faça login |
| Entregas indo para revisão `SEM_COORDENADA` | Sem chave do Google, só a entrega com coordenada é posicionada. Informe a coordenada ou configure a chave |
| 409 `DADO_DUPLICADO` em operações simultâneas | A trava da loja esperou mais de 10 s: repita a operação |
| `The database has been closed` (90098) | O H2 ficou sem memória: reinicie (seção 9.3) |
| O fluxo do Postman falha na segunda vez | O caixa do dia ficou fechado: reinicie o backend no perfil `h2` |

## 10. Limitações e decisões PROVISÓRIAS

Decisões tomadas onde o DVP não diz. Cada uma está marcada `PROVISORIO` no código:

| Decisão | Onde |
|---|---|
| Sem chave do Google: distância em linha reta × 1,3 (D009) | `DistanciaEmLinhaReta`, `IntegracaoConfig` |
| Sem chave do Google: o simulador posiciona no centro do bairro, até 300 m (D009) | `SimuladorAdapter` |
| Refresh token de 30 dias e claim `tipo` (fora do DVP 3.1); o refresh antigo não é revogado; sem `canal` no login, vale BACKOFFICE | `ServicoJwt`, `AutenticacaoController`, `LoginRequest` |
| Um fuso para todas as lojas: `America/Sao_Paulo` (T014) | `HorarioLocal` |
| Limites de espera de 10 s (trava da loja, conexão do pool) e corpo até 1 MB (T014) | `application*.yaml`, `LimiteDeCorpoFilter` |
| Reaproveitamento de códigos de erro em 401, 405, 406, 413 e 415 (T004, T014); provedor desconhecido como 400 `DADOS_INVALIDOS` | `CodigoErro`, `RespostaDeErro`, `ProvedorDesconhecidoException` |
| Só o painel da loja assina o WebSocket (T014) | `SubscricaoTenantInterceptor` |
| Acerto do app: sem período, o dia de hoje; o período cabe num único dia (T009) | `EntregadorServiceImpl` |
| Ajuste de repasse só em entrega já finalizada (T014) | `AjusteRepasse` |
| Relatório do dia (D013): `valorPago` soma o repasse das entregas do dia em recibo PAGO (e não o valor dos recibos com data do dia); um repasse `null` deixa `null` o total e o a pagar; `entregadoresQueTrabalharam` conta quem tem entrega finalizada no dia, inclusive cancelada depois do despacho | `RelatorioServiceImpl` |
| Recibos do app (D013): "mais recente" é o dia do caixa, depois a emissão, com o id como desempate | `ReciboRepository` |

Limitações conhecidas:
- **iFood:** a porta `ProvedorDePedidos` está pronta, mas o `IFoodAdapter` não existe (depende de homologação, risco R01). Hoje só o simulador gera pedidos.
- **Migrações nos testes:** a suíte roda no H2 com o esquema gerado pelo Hibernate. A V1 do Flyway só é aplicada no perfil `postgres`, onde o `validate` confere o mapeamento ao subir; dos testes, só a V2 é executada.
- **Uma instância só:** o broker STOMP e os caches de mapas ficam em memória, na instância. Várias instâncias não compartilham eventos nem cache.
- **H2:** é só para demonstração. Os dados somem ao reiniciar.
- **Sem paginação:** as listas (entregas, lotes, recibos, recibos do entregador) vêm inteiras.
- **App:** usa consulta periódica, não WebSocket. Não há tópico do entregador.
- **Datas fora do ISO:** só o relatório do dia exige `AAAA-MM-DD`. Nos outros parâmetros de data (`/api/caixas`, `/api/acertos`, `/api/entregador/acerto`), o Spring também aceita o formato curto do idioma do cliente. Envie sempre ISO.
- **Fora do escopo do MVP** (DVP 1.2.4): otimização automática de rota, pagamento dentro da plataforma, relatórios analíticos e BI (além do resumo do dia da D013), chat interno e avaliação de entregadores.
