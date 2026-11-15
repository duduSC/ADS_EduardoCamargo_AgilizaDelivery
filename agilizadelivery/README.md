# Agiliza Delivery — backend

Spring Boot 4, Java 21. Regras de negócio no DVP (`docs/DVP_Agiliza_Delivery_latest_version.md`).

## Subir em desenvolvimento

```
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Sem nenhuma variável de ambiente, sobe no perfil `h2` (banco em memória, recriado a cada início) em `http://localhost:8080`, com a **carga de demonstração** ligada.

Para o PostgreSQL: `SPRING_PROFILES_ACTIVE=postgres` com `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `AGILIZA_CPF_CHAVE_CIFRA` e `AGILIZA_CPF_CHAVE_HASH`. A carga de demonstração fica desligada; `AGILIZA_DEMO=true` liga (ela é idempotente).

## Carga de demonstração (D009) — SÓ DESENVOLVIMENTO

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

O que é criado: a loja "Pizzaria Demo" (ATIVO, centro de Passo Fundo/RS, até 5 pedidos por lote, repasse fora de área R$ 20,00); dois entregadores ATIVOS e OFFLINE (CPFs fictícios, gravados cifrados); faixas 0–3 km = R$ 9,00, 3–5 km = R$ 12,00 e 5–8 km = R$ 15,00; o caixa do dia ABERTO; três entregas avulsas AGUARDANDO_DESPACHO (PIX, DINHEIRO e CREDITO).

## Distância sem chave do Google (PROVISÓRIO, D009)

Sem `GOOGLE_MAPS_API_KEY`, a distância loja → cliente é a linha reta (Haversine) × 1,3, com 2 casas. O log avisa ao subir: `GOOGLE_MAPS_API_KEY ausente: distância em linha reta (PROVISÓRIO, D009)`. Uma entrega sem coordenada continua indo para revisão (SEM_COORDENADA). Com a chave definida, vale a Routes API e, em falha, SEM_DISTANCIA (DVP 1.5.5).

## Simulador de pedidos (T011)

O worker de integração (DVP 1.5.6) roda a cada 30 s quando `agiliza.integracao.worker.habilitado` (`AGILIZA_WORKER`) é `true`. Ele vem ligado no perfil `h2` e desligado nos outros.

A carga de demonstração cria a integração `SIMULADOR` da loja demo **inativa** (`merchantId` `demo-simulador`). Para começar a apresentação, o admin da loja a liga:

```
PUT /api/integracoes   (token de loja@agiliza.dev)
{"provedor": "SIMULADOR", "merchantId": "demo-simulador", "ativo": true}
```

Na coleção, o request é Loja → "Ligar o simulador de pedidos". Para desligar, use `"ativo": false`.

A cada ciclo surgem de 0 a 2 pedidos fictícios, com itens, pagamento e coordenada a até 6 km da loja. Cada um vira uma entrega AGUARDANDO_DESPACHO, que é publicada em `/topic/estabelecimento/{id}/entregas` com o tipo `CRIADA`.

O simulador para de gerar quando a loja tem 15 ou mais entregas aguardando despacho. Quando a importação de um pedido falha, ele é repetido até 5 vezes com recuo exponencial. Se todas as tentativas falharem, a falha vai para `GET /api/falhas-integracao` e o ciclo continua.

## Postman / Insomnia

Importe os dois arquivos de `postman/`:

- `agiliza-delivery.postman_collection.json` (coleção v2.1);
- `agiliza-dev.postman_environment.json` (environment `agiliza-dev`, com `baseUrl` e as credenciais acima).

Selecione o environment e rode a pasta **Fluxo ponta a ponta** no Collection Runner. Ela fecha o caixa do dia: para rodar de novo, reinicie o backend (perfil `h2`). As outras pastas (Autenticação, Plataforma, Loja, Operação, Entregador) têm um request por endpoint; rode a pasta Autenticação primeiro para guardar os tokens.

No Insomnia, a importação traz os requests e o environment; os scripts que guardam tokens e ids usam a API `pm.*` do Postman e podem precisar de ajuste manual.
