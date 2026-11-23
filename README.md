# Agiliza Delivery

Plataforma de logística de entregas para restaurantes com frota própria. Ela importa os pedidos automaticamente, permite o despacho visual no mapa, acompanha os entregadores em tempo real e calcula o repasse por faixa de distância, com recibo e fechamento de caixa. É multi-tenant: uma instalação atende vários restaurantes, com os dados de cada um isolados.

## Repositórios

| Parte | Repositório | Tecnologias |
|---|---|---|
| **Backend (API)**: este repositório | [duduSC/ADS_EduardoCamargo_AgilizaDelivery](https://github.com/duduSC/ADS_EduardoCamargo_AgilizaDelivery) | Java 21, Spring Boot 4, Spring Security + JWT, WebSocket/STOMP, JPA, Flyway, PostgreSQL 16 / H2 |
| **Backoffice Web** (operador e administradores) | [duduSC/agiliza_frontend](https://github.com/duduSC/agiliza_frontend) | React 18, TypeScript, Vite, TanStack Query, Zod, Leaflet + OpenStreetMap |
| **App do entregador** (Android) | [duduSC/agiliza_mobile](https://github.com/duduSC/agiliza_mobile) | Flutter 3.44 (Dart 3.12), Riverpod, go_router |

O Backoffice e o app consomem a mesma API REST deste backend.

## Documentação

- **Documento de Visão do Produto (DVP):** [`agilizadelivery/docs/DVP_Agiliza_Delivery_latest_version.md`](agilizadelivery/docs/DVP_Agiliza_Delivery_latest_version.md). Requisitos, casos de uso, arquitetura, modelo de dados e regras de precificação.
- **Como subir o backend**, com a carga de demonstração, o simulador de pedidos e a coleção Postman: [`agilizadelivery/README.md`](agilizadelivery/README.md).
- **Variáveis de ambiente:** [`agilizadelivery/.env.example`](agilizadelivery/.env.example).

## Arquitetura (resumo)

O backend segue a arquitetura em camadas do Spring Boot: Controller (REST), Service (regras de negócio), Repository (Spring Data JPA) e Entity. Nenhuma camada inferior acessa uma superior (DVP, RNF05).

Ao redor dessas camadas ficam:
- a segurança (JWT por perfil e por canal, e filtro por estabelecimento);
- o tempo real (STOMP);
- o worker de integração com provedores de pedidos, atrás da interface `ProvedorDePedidos` (Simulador hoje, iFood depois);
- o cálculo de distância pela Google Routes API, com reserva em linha reta.
