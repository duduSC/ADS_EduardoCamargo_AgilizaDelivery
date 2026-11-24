# Agiliza Delivery

> Plataforma de logística de entregas para restaurantes com frota própria de entregadores.
> Projeto acadêmico de Eduardo dos Santos de Camargo (2026). Os requisitos completos estão no [Documento de Visão do Produto](agilizadelivery/docs/DVP_Agiliza_Delivery_latest_version.md).

O Agiliza Delivery é o "sistema nervoso central" da entrega própria de um restaurante:
- recebe os pedidos automaticamente;
- mostra-os num mapa para o operador montar e despachar os lotes;
- acompanha os entregadores em tempo real;
- calcula quanto cada entregador deve receber por entrega, de acordo com a distância percorrida, e fecha o acerto do dia com recibo e caixa.

---

## Sumário

1. [O problema](#1-o-problema)
2. [A solução em três partes](#2-a-solução-em-três-partes)
3. [Repositórios](#3-repositórios)
4. [Arquitetura geral](#4-arquitetura-geral)
5. [Arquitetura de cada parte e por que foi feita assim](#5-arquitetura-de-cada-parte-e-por-que-foi-feita-assim)
6. [Funcionalidades](#6-funcionalidades)
7. [Regras de negócio principais](#7-regras-de-negócio-principais)
8. [Segurança e LGPD](#8-segurança-e-lgpd)
9. [Como rodar o projeto inteiro](#9-como-rodar-o-projeto-inteiro)
10. [Qualidade e testes](#10-qualidade-e-testes)
11. [Como o projeto foi construído](#11-como-o-projeto-foi-construído)
12. [Limitações conhecidas e próximos passos](#12-limitações-conhecidas-e-próximos-passos)
13. [Documentação](#13-documentação)

---

## 1. O problema

Restaurantes com entregadores próprios costumam operar "às cegas":

- **Não sabem onde está cada entregador** durante o turno.
- **Digitam de novo** os pedidos que chegam por aplicativos como o iFood.
- **Agrupam as entregas no olho**, sem visualizar no mapa onde ficam os clientes.
- **Fazem o acerto com os motoboys numa planilha**, com a quilometragem estimada à mão. O resultado é demorado e gera divergências de pagamento.

**Metas do produto** (Documento de Visão, seção 1.2.3):

| Objetivo | Hoje | Meta |
|---|---|---|
| Eliminar a redigitação de pedidos | 0% importados | ≥ 95% importados automaticamente |
| Acelerar o despacho (tempo do pedido na estufa) | ~8 min | < 2 min |
| Visibilidade da frota | 0% do turno | ≥ 95% do turno com posição conhecida |
| Acerto financeiro auditável | ~30 min por entregador | < 2 min |
| Divergências de pagamento | recorrentes | 0 contestações por turno |

## 2. A solução em três partes

| Parte | Quem usa | O que faz |
|---|---|---|
| **Backend (API)** | os dois clientes abaixo | Guarda os dados e aplica **todas** as regras de negócio. Importa os pedidos, calcula a distância e o repasse, controla os status, gera recibos e fecha o caixa. Avisa o painel em tempo real |
| **Backoffice Web** | Operador de logística e administradores | Despacho visual no mapa, monitoramento da frota, revisão de pendências, acerto financeiro, caixa e relatório do dia |
| **App do entregador** (Android) | Entregador | Recebe o lote, vê os detalhes de cada entrega, navega pelo Google Maps ou Waze, fala com o cliente pelo WhatsApp, marca Entregue ou Falha e acompanha quanto vai receber |

**Perfis de acesso:**
- **Administrador Geral:** dono da plataforma.
- **Administrador do Restaurante:** gestor da loja.
- **Operador de Logística.**
- **Entregador.**

O sistema é **multi-tenant**: uma única instalação atende vários restaurantes, e cada um só enxerga os próprios dados.

## 3. Repositórios

| Parte | Repositório | Tecnologias principais |
|---|---|---|
| **Backend (API)**, este repositório | [duduSC/ADS_EduardoCamargo_AgilizaDelivery](https://github.com/duduSC/ADS_EduardoCamargo_AgilizaDelivery) | Java 21, Spring Boot 4 (Web, Data JPA, Security, WebSocket, Scheduler), JWT, Flyway, PostgreSQL 16 / H2 |
| **Backoffice Web** | [duduSC/agiliza_frontend](https://github.com/duduSC/agiliza_frontend) | React 18, TypeScript (strict), Vite, React Router, TanStack Query, Zustand, Zod, Tailwind + shadcn/ui, Leaflet + OpenStreetMap, STOMP |
| **App do entregador** | [duduSC/agiliza_mobile](https://github.com/duduSC/agiliza_mobile) | Flutter 3.44 / Dart 3.12, Riverpod, go_router, flutter_secure_storage, geolocator, url_launcher |

Cada repositório tem o próprio README com os detalhes da sua parte. O do backend fica em [`agilizadelivery/README.md`](agilizadelivery/README.md).

## 4. Arquitetura geral

```
     ┌──────────────────────────────┐          ┌──────────────────────────────┐
     │  Backoffice Web (React SPA)  │          │  App do entregador (Flutter) │
     │  operador / administradores  │          │  Android 8.0+                │
     └───────┬───────────────┬──────┘          └───────────────┬──────────────┘
   REST+JWT  │               │ WebSocket (STOMP)               │ REST+JWT
   (canal    │               │ eventos de entregas e da frota  │ (canal APP)
   BACKOFFICE)               │                                 │
             ▼               ▼                                 ▼
     ┌────────────────────────────────────────────────────────────────────┐
     │                    BACKEND — API REST (Spring Boot)                │
     │                                                                    │
     │   Controller  →  Service  →  Repository  →  Entity                 │
     │   (HTTP)         (regras)    (dados)        (modelo)               │
     │                                                                    │
     │   + Segurança: JWT, perfis, canal, filtro por estabelecimento      │
     │   + Tempo real: STOMP em /ws                                       │
     │   + Worker de integração: busca pedidos a cada 30 s                │
     └─────────┬────────────────────────┬──────────────────────┬──────────┘
               │ JPA / Flyway           │ HTTP                 │ Adapter
               ▼                        ▼                      ▼
     ┌──────────────────┐   ┌────────────────────────┐   ┌─────────────────────┐
     │ PostgreSQL 16    │   │ Google Maps Platform   │   │ Provedor de pedidos │
     │ (H2 na demo)     │   │ Routes (moto) e        │   │ Simulador hoje;     │
     └──────────────────┘   │ Geocoding              │   │ iFood previsto      │
                            └────────────────────────┘   └─────────────────────┘
```

**Princípios que valem para o sistema inteiro:**

1. **Uma API, dois clientes.** O Backoffice e o app consomem a mesma API REST (DVP 1.5.1).
2. **A regra de negócio mora só no backend.** Os clientes **não calculam dinheiro**: exibem os valores que o servidor envia. Valor ainda indefinido aparece como "—", nunca como R$ 0,00.
3. **Canais separados.** O token emitido para o app (canal `APP`) não abre o painel, e o do painel (canal `BACKOFFICE`) não serve no app.
4. **Tempo real no painel.** O backend publica eventos em tópicos STOMP por loja (`/topic/estabelecimento/{id}/entregas` e `/frota`). O painel recarrega os dados quando um evento chega e, se a conexão cair, consulta de novo a cada 15 s.
5. **O app consulta periodicamente:** o lote ativo a cada 10 s, e envia a posição GPS a cada 20 s. Foi uma decisão de escopo para o prazo; veja a seção 12.

## 5. Arquitetura de cada parte e por que foi feita assim

### 5.1 Backend: arquitetura em camadas

O backend segue a arquitetura em camadas do Spring Boot. É um requisito do projeto (RNF05: "nenhuma camada inferior acessa uma camada superior").

| Camada | Pacote | Responsabilidade |
|---|---|---|
| **Controller** | `controller/` | Recebe a requisição HTTP, valida a entrada, identifica o usuário e a loja pelo token e chama o serviço. Não contém regra de negócio |
| **Service** | `service/` | Regras de negócio: máquinas de estado, cálculo do repasse, montagem de lotes, recibos, caixa, isolamento entre lojas |
| **Repository** | `repository/` | Acesso aos dados com Spring Data JPA, sempre filtrado pelo estabelecimento |
| **Entity** | `entity/` | O modelo de domínio do DVP (Entrega, Lote, Recibo, Caixa, Motoboy...) mapeado para as tabelas |

Módulos de apoio: `dto/` (contratos da API, separados das entidades), `exception/` (erro padronizado), `seguranca/` (JWT, BCrypt, cifra do CPF), `websocket/` (tempo real), `integracao/` (Google Maps e provedores de pedidos), `tempo/` (horário de São Paulo) e `config/`.

**Exemplo: o operador despacha um lote** (`POST /api/lotes`)
1. A **segurança** valida o token, o perfil e o canal.
2. O **Controller** valida o JSON e pega a loja do token.
3. O **Service**:
   - impede que dois despachos simultâneos usem a mesma entrega;
   - confere que o entregador está ONLINE e ATIVO, que cada entrega está aguardando despacho e sem pendência, e que o limite de pedidos por lote não foi passado;
   - cria o lote e atualiza os status.
4. O **Repository** grava tudo numa única transação.
5. Depois de gravar, o **tempo real** avisa o painel.
6. Qualquer regra violada vira uma resposta 422 com um código claro, como `MOTOBOY_INDISPONIVEL` ou `LIMITE_LOTE_EXCEDIDO`.

**Por que essa divisão compensou neste projeto (exemplos reais):**
- **Trocar o banco sem tocar na regra:** o mesmo código roda no H2 em memória (demonstração) e no PostgreSQL 16 (produção, com migrações Flyway).
- **Trocar o cálculo de distância sem tocar na regra:** a distância vem de uma interface (`ServicoDeRotas`). Com a chave do Google, usa a Routes API com **rota de moto** (`TWO_WHEELER`), e a de carro como reserva quando o Google não tem rota de moto para o trecho. Sem a chave, usa uma estimativa em linha reta × 1,3. O cálculo do repasse no Service é o mesmo nos três casos.
- **Trocar a origem dos pedidos sem tocar na regra:** a integração fica atrás da interface `ProvedorDePedidos` (padrão Adapter). Hoje roda o Simulador; o iFood entra no mesmo lugar.
- **Corrigir em um só lugar:** um bug de fuso horário (o servidor em UTC fazia o "hoje" virar amanhã depois das 21h) foi corrigido centralizando "agora" e "hoje" em `tempo/HorarioLocal`.
- **Testar cada camada isolada:** o backend tem mais de 800 testes automatizados.

### 5.2 Backoffice Web: módulos por funcionalidade

```
src/
├── app/          rotas, layout, guardas por perfil, ligação do cliente HTTP
├── shared/
│   ├── api/        client.ts (o único ponto de HTTP), endpoints, erros, schemas Zod
│   ├── realtime/   cliente STOMP (tempo real)
│   ├── dominio/    enums, permissões por perfil, máquina de estados
│   ├── formatacao/ moeda ("—" para nulo), km, data/hora (America/Sao_Paulo)
│   └── ui/         componentes visuais
└── features/     inicio, auth, despacho, monitoramento, acerto, caixa, relatorio...
```

**Por que assim:**
- **Cada funcionalidade é um módulo independente,** com `api/`, `componentes/` e `paginas/`. Um módulo não mexe nos internos de outro.
- **Toda resposta da API passa por um schema Zod** antes de chegar à tela. Se o formato mudar, aparece um erro tratado, nunca uma tela quebrada.
- **Dados do servidor ficam no TanStack Query** (cache e recarga); o estado local fica no Zustand (sessão em memória, filtros, seleção no mapa).
- **O MSW simula o backend nos testes** e permitiu construir as telas antes de os endpoints existirem.

### 5.3 App do entregador: núcleo e funcionalidades

```
lib/
├── core/       api (cliente HTTP, refresh do token), sessão segura, localização (GPS),
│               deep links (Maps/Waze/WhatsApp), domínio, formatação, UI comum
└── features/   auth, inicio, lote, acerto: cada uma com data/ (repositório),
                estado/ (Riverpod) e ui/ (telas)
```

**Por que assim:** cada repositório tem duas implementações, uma **real** (API) e uma **fake** (em memória). O app foi construído e testado antes de os endpoints do entregador existirem no backend; depois, bastou trocar a implementação.

## 6. Funcionalidades

### 6.1 Backoffice Web

| Tela | O que faz |
|---|---|
| **Login** | E-mail e senha, com mensagem de erro genérica. O entregador é recusado, porque o lugar dele é o app |
| **Início** | Nome, perfil e loja, mais atalhos para as telas daquele perfil |
| **Operação** (Despacho e Monitoramento numa tela só) | Mapa claro ocupando a tela, com a loja, os **pedidos numerados** e os **entregadores (ícone de moto) se movendo em tempo real**. Uma lista lateral repete os mesmos números. Filtros **Aguardando despacho**, **Saiu para entrega** e **Todos**, e botão para reenquadrar o mapa. Mostra o tempo do pedido na estufa, a distância, o repasse, a forma de pagamento e o troco |
| **Despachar** (na Operação) | Marcar o pedido como pronto, selecionar vários no mapa ou na lista e **despachar** para um entregador online, até o limite de pedidos por lote da loja. Também permite devolver um lote que ainda não saiu e cadastrar uma entrega avulsa |
| **Revisão** (na Operação) | Entregas pendentes de análise. **Analisar contestação** do entregador: manter o valor ou ajustar, sempre com motivo. **Reposicionar pino** de entrega sem localização ou sem distância, e o backend recalcula o km e o repasse |
| **Monitoramento** | A frota no mapa em tempo real, com alerta de posição desatualizada (mais de 3 minutos sem sinal) |
| **Acerto** | Extrato por entregador e período (distância, faixa e valor de cada entrega), ajuste de repasse com motivo, **gerar recibo** e **marcar como pago** |
| **Caixa** | Abrir e fechar o caixa do dia. Fechado, o dia fica imutável |
| **Relatório do dia** | Escolhida a data: entregadores que trabalharam, entregas por entregador, total de entregas (entregues, falhas, canceladas) e valores de repasse (total, pago e a pagar). Todos os totais vêm do servidor |

Os filtros e a seleção de cada tela ficam guardados ao trocar de aba: por exemplo, o entregador e o período escolhidos no Acerto continuam lá ao voltar.

### 6.2 App do entregador

| Tela | O que faz |
|---|---|
| **Login** | Só para entregadores. A sessão fica guardada no armazenamento seguro do Android |
| **Início** | **Ficar online/offline**, com permissão de localização, e aviso de "Novo lote" com vibração |
| **Lote** | Entregas na ordem do despacho, com cliente, bairro, valor, pagamento e troco. **Iniciar percurso** |
| **Entrega** | Endereço completo, itens, observações, distância e repasse. Botões **Navegar** (Google Maps/Waze), **WhatsApp** com mensagem pronta e **Entregue** / **Falha** (com motivo), com confirmação em dois toques |
| **Acerto** | Valor parcial do turno, com o caixa aberto e opção de **contestar** uma entrega. Com o caixa fechado, valor final |

Com o app aberto e o entregador online, a posição GPS é enviada a cada 20 s e aparece no mapa do painel.

### 6.3 Backend

- Autenticação JWT com refresh, perfis e canais.
- Regras e endpoints de entregas, lotes, entregadores, recibos, caixa, faixas de preço, usuários, restaurantes e integração.
- Worker que importa pedidos a cada 30 s, com idempotência, cópia do JSON original e repetição com recuo exponencial em falhas.
- Distância por **rota de moto** na Google Routes API (com a de carro como reserva) e repasse por faixa.
- **Relatório do dia** (`GET /api/relatorios/dia`), com todos os totais calculados no servidor.
- **Recibos do entregador** (`GET /api/entregador/recibos`): situação, valor e entregas de cada recibo, já prontos para o app.
- Tempo real por WebSocket/STOMP.
- Carga de demonstração e **simulador de pedidos com endereços reais de Passo Fundo/RS**. Com a chave do Google, o endereço é geocodificado e o km sai da rota de moto até ele.

Detalhes, endpoints e instruções em [`agilizadelivery/README.md`](agilizadelivery/README.md).

## 7. Regras de negócio principais

- **Distância pelo percurso de moto**, não em linha reta: a rota viária entre a loja e o endereço do cliente (DVP 1.5.5). Sem rota de moto para o trecho, vale a de carro.
- **Repasse por entrega, nunca por lote**, pela faixa de distância da loja (por exemplo: 0–3 km = R$ 9; 3–5 km = R$ 12). Acima da última faixa, aplica-se um valor "fora de área".
- **O valor congela quando a entrega termina.** Mudar a tabela depois não afeta entregas finalizadas.
- **Falha paga integral**, porque o entregador foi e voltou. Cancelamento antes de sair paga zero; durante o percurso, integral.
- **Máquina de estados da entrega:** AGUARDANDO_DESPACHO → DESPACHADA → EM_ROTA → ENTREGUE, FALHA ou CANCELADA. Transições inválidas são recusadas pelo backend.
- **Revisão pendente** só por três motivos: sem coordenada, sem distância (falha do Google) e contestação do entregador. Entrega em revisão não entra em lote nem em recibo.
- **Caixa fechado é imutável:** recibos, pagamentos, ajustes e contestações daquele dia são recusados.
- **Ajuste manual** só em entrega fora de recibo, com o caixa aberto e motivo obrigatório. Todo ajuste fica registrado.

## 8. Segurança e LGPD

- **Senhas:** hash BCrypt.
- **CPF dos entregadores:** cifrado no banco (AES-GCM) e nunca devolvido pela API.
- **Tokens:** JWT de acesso de 3 h com refresh. No Backoffice o token fica só em memória; no app, no armazenamento seguro do Android.
- **Isolamento entre lojas:** a loja vem sempre do token, e toda consulta é filtrada por ela, inclusive no tempo real. Há testes de tentativa de acesso cruzado.
- **Erros:** sempre no mesmo formato (`ApiErrorResponse`), sem detalhes internos.
- **LGPD (RNF07):** guarda-se só a última posição do entregador e a coordenada de cada mudança de status, sem trilha contínua. A base legal é a execução do contrato de trabalho.

## 9. Como rodar o projeto inteiro

**Pré-requisitos:**
- Docker Desktop;
- Node.js 20 ou superior com npm (Backoffice; testado com o Node 24);
- celular Android 8.0+ (app);
- opcional: Postman e uma chave da Google Maps Platform (Routes e Geocoding).

**1. Backend**, neste repositório. O perfil `h2` sobe com dados de demonstração:

```bash
cd agilizadelivery
cp .env.example .env    # preencha as variáveis; veja os comentários no arquivo
docker run --rm -it --name agiliza-backend -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=h2 -v "${PWD}:/app" -v agiliza_m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn spring-boot:run
```

O backend está no ar quando `http://localhost:8080/actuator/health` responde `{"status":"UP"}`. Para ligar o simulador de pedidos, veja o [README do backend](agilizadelivery/README.md).

**2. Backoffice**, no repositório [agiliza_frontend](https://github.com/duduSC/agiliza_frontend):

```bash
npm install
cp .env.example .env    # depois troque VITE_USE_MOCKS para false (com true, o front usa dados simulados)
npm run dev             # http://localhost:5173
```

O backend só aceita o Backoffice na origem `http://localhost:5173` (CORS). Use essa porta.

**3. App**, no repositório [agiliza_mobile](https://github.com/duduSC/agiliza_mobile). Gere o APK de debug pelo wrapper Docker, sem instalar o Flutter, e instale no celular. Na tela de login, a **engrenagem** define o endereço do backend (`http://IP-do-PC:8080`). Instruções completas no README do app.

**Usuários de demonstração** (só desenvolvimento; senha `agiliza123`):

| Perfil | E-mail |
|---|---|
| Operador | `operador@agiliza.dev` |
| Administrador da loja | `loja@agiliza.dev` |
| Administrador geral | `admin@agiliza.dev` |
| Entregadores (app) | `entregador1@agiliza.dev`, `entregador2@agiliza.dev` |

## 10. Qualidade e testes

| Parte | Testes automatizados |
|---|---|
| Backend | 800+ testes (JUnit/Spring), executados com a JVM em UTC para garantir o fuso correto. Coleção Postman com um fluxo ponta a ponta de 16 requisições |
| Backoffice | 400+ testes (Vitest + Testing Library + MSW) e testes de ponta a ponta (Playwright) |
| App | ~100 testes (flutter_test) e análise estática sem avisos |

O backend e o Backoffice passaram por **revisões completas**, com inventário de endpoints e telas e achados classificados por severidade. Toda correção de comportamento entrou com um teste que falhava antes, e cada revisão terminou com uma rodada "adversarial" tentando quebrar o sistema. Exemplos do que elas pegaram:
- o servidor em UTC fazia o "hoje" virar amanhã depois das 21h;
- o tempo real entregava eventos de outras lojas;
- dois despachos simultâneos podiam usar a mesma entrega;
- o duplo clique em "pagar" enviava duas requisições.

O app teve cada função verificada com análise estática e testes automatizados, e o APK foi conferido contra o código.

## 11. Como o projeto foi construído

- **Fonte da verdade:** o [Documento de Visão do Produto (DVP)](agilizadelivery/docs/DVP_Agiliza_Delivery_latest_version.md), com requisitos, casos de uso, modelo de dados e regras.
- **Desenvolvimento com agentes de IA (Claude),** um para cada parte. Cada agente segue uma *Constituição* (regras que não pode quebrar) e um manual, e trabalha por **tarefas** escritas, que terminam com relatório, testes e merge.
- **Um agente Orquestrador** escreve as tarefas, confere os relatórios, media as necessidades entre as partes e registra as decisões de produto.
- **Git:** uma branch por tarefa; merge na `develop` só com os testes passando; a `main` é atualizada pelo autor.

## 12. Limitações conhecidas e próximos passos

- **App:**
  - GPS só com o app aberto (sem serviço em segundo plano);
  - sem fila offline;
  - consulta periódica do lote, sem notificação push;
  - sem alerta de cancelamento durante o percurso.

  Ficaram fora por prazo e estão previstos no DVP.
- **Recibos no app:** a API já entrega os recibos do entregador, mas a tela no app fica para a próxima versão. Hoje o entregador acompanha o acerto do dia.
- **Backoffice:**
  - as telas de cadastro da loja (entregadores, faixas de preço, parâmetros e integração) e de restaurantes ainda são esboços;
  - cancelar recibo e reabrir caixa não têm botão.

  Todas essas funções já existem na API.
- **Sessão do Backoffice:** recarregar a página pede login de novo. É intencional: o token fica só na memória, por segurança.
- **Integração com o iFood:** arquitetura pronta (interface `ProvedorDePedidos`), dependendo da homologação. Hoje o Simulador gera os pedidos.
- **Sem chave do Google:** a distância é estimada em linha reta × 1,3, e o simulador posiciona cada pedido no centro do bairro.
- **Fora do escopo do MVP** (DVP 1.2.4): otimização automática de rota, pagamento dentro da plataforma, iOS, chat interno e avaliação de entregadores.

## 13. Documentação

| Documento | Onde |
|---|---|
| Documento de Visão do Produto (DVP) | [`agilizadelivery/docs/DVP_Agiliza_Delivery_latest_version.md`](agilizadelivery/docs/DVP_Agiliza_Delivery_latest_version.md) |
| Backend: arquitetura, endpoints e como rodar | [`agilizadelivery/README.md`](agilizadelivery/README.md) |
| Variáveis de ambiente | [`agilizadelivery/.env.example`](agilizadelivery/.env.example) |
| Coleção Postman | [`agilizadelivery/postman/`](agilizadelivery/postman/) |
| Backoffice | [duduSC/agiliza_frontend](https://github.com/duduSC/agiliza_frontend) |
| App do entregador | [duduSC/agiliza_mobile](https://github.com/duduSC/agiliza_mobile) |
