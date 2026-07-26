# Spec 003 — Tenant provisioning

**Status:** aprovada (decisões D-01–D-11 e A-01–A-02 fechadas)  
**Entrega:** provisionar um tenant juntamente com sua primeira identity autenticável em uma única operação de plataforma  
**Depende de:** Spec 001 — Password authentication; Spec 002 — Authenticated identity  
**Idioma:** PT-BR

---

## 1. Contexto

As Specs 001 e 002 estabeleceram o fluxo:

`login → access token → GET /me`

Esse fluxo, porém, ainda depende de tenant e identity plantados por `DevDataSeeder` (ou de manipulação direta do banco). Uma instalação sem dados de produto não consegue chegar ao primeiro login usando apenas capacidades reais do Auth-Saas.

Esta entrega fecha essa lacuna com o menor provisionamento possível: uma operação de plataforma que cria, atomicamente, um tenant `ACTIVE` e sua primeira identity autenticável. Não reconstrói o onboarding completo do MVP (schema-per-tenant, roles, control plane separado, lifecycle `PROVISIONING`/`FAILED`).

Limitação posterior conhecida (fora desta Spec): access tokens ainda não podem ser verificados por consumidores externos (JWKS).

---

## 2. Objetivo

Dada uma instalação sem dados de produto e credenciais de operador de plataforma válidas, o sistema deve:

1. aceitar `POST /platform/v1/tenants` com `slug`, `username` e `password`;
2. autenticar o operador via HTTP Basic com credenciais de plataforma provenientes de configuração externa;
3. criar, atomicamente, um tenant `ACTIVE` e uma identity habilitada com password armazenada como hash Argon2id;
4. retornar apenas identificadores públicos do que foi criado;
5. permitir, em seguida, o fluxo Spec 001 → Spec 002 com esses dados, sem depender do `DevDataSeeder`;
6. rejeitar autenticação de plataforma inválida (`401 PLATFORM_UNAUTHORIZED`), request inválido (`400 INVALID_REQUEST`), slug duplicado (`409 TENANT_EXISTS`) e falhas de criação sem deixar estado parcial.

---

## 3. Escopo

Comportamentos desta entrega:

- Expor `POST /platform/v1/tenants` como operação de plataforma (mesmo processo/aplicação das Specs 001/002; sem segundo serviço ou deployável).
- Proteger a operação com HTTP Basic usando credenciais de plataforma configuradas externamente (não hardcoded, não commitadas, não armazenadas nas tabelas de identities dos tenants).
- Aceitar entrada mínima: `slug`, `username`, `password`.
- Exigir password com no mínimo 12 caracteres; sem regras adicionais de complexidade.
- Persistir password exclusivamente como hash Argon2id via a capacidade já existente no produto.
- Em sucesso: criar tenant com status `ACTIVE` e primeira identity com `enabled = true`, na mesma operação atômica; responder `201` com `tenantId`, `slug`, `identityId`, `username` — sem password, hash ou segredos.
- Em Basic Auth ausente ou inválido: `401 PLATFORM_UNAUTHORIZED`, indistinguíveis; nada criado. Não usar `403`.
- Em violação de entrada: `400 INVALID_REQUEST`; nada criado.
- Em slug já existente: `409 TENANT_EXISTS`.
- Em falha durante a criação: reverter por completo (sem tenant parcial, sem identity órfã).
- Preservar `DevDataSeeder` nos perfis `dev`/`test`, sem torná-lo necessário ao fluxo funcional real.
- Preservar os contratos de login (Spec 001) e `/me` (Spec 002) sem alteração.

---

## 4. Fora do escopo

Explicitamente fora desta entrega:

- segundo serviço / control plane / deployável separado
- schema-per-tenant
- Flyway dinâmico por tenant
- status `PROVISIONING` / `FAILED` ou state machine de onboarding
- `displayName` ou outros metadados de tenant
- roles, permissions, scopes
- significado de “admin” na primeira identity
- criação de identities adicionais
- CRUD de tenant
- CRUD de identity
- convites
- reset / change password
- painel administrativo
- platform users persistidos
- RBAC / ABAC
- OAuth2 administrativo
- API keys
- billing / planos / organizações complexas
- refresh token / logout / revogação
- JWKS / publicação de chave pública
- persistência / rotação / key management de chaves RSA
- remoção do `DevDataSeeder`
- alteração dos contratos das Specs 001 e 002
- códigos de erro específicos de campo (`PASSWORD_TOO_SHORT`, `INVALID_SLUG`, `USERNAME_REQUIRED` e equivalentes)

---

## 5. Relação com as Specs 001 e 002

| Capacidade | Papel nesta Spec |
|---|---|
| Spec 001 — login | Consome o tenant `ACTIVE` e a identity habilitada criados pelo provisionamento; contrato HTTP de login **não muda** |
| Spec 002 — `/me` | Consome o access token emitido após esse login; contrato de `/me` **não muda** |
| Hash Argon2id existente | Único armazenamento permitido da password inicial |
| Modelo `tenants` / `identities` existente | Persistência mínima; sem novos conceitos de roles ou schema-per-tenant |
| `DevDataSeeder` | Pode permanecer em `dev`/`test`; o fluxo real de produto deixa de depender dele |

`PLATFORM_UNAUTHORIZED` pertence **exclusivamente** à fronteira de autenticação da operação de plataforma. Não substitui:

- `INVALID_CREDENTIALS` (Spec 001 — autenticação de identities);
- `INVALID_TOKEN` (Spec 002 — autenticação Bearer).

Fluxo funcional resultante:

```text
instalação sem dados de produto
  → POST /platform/v1/tenants  (Basic Auth de plataforma)
  → POST /t/{slug}/v1/auth/login
  → GET /t/{slug}/v1/me
```

---

## 6. Regras de negócio

| ID | Regra | Origem |
|---|---|---|
| RN-01 | O provisionamento é operação exclusiva de um **operador da plataforma**. Não introduz usuários de plataforma persistidos, roles nem RBAC. | Decisão D-01 |
| RN-02 | A operação é autenticada por **HTTP Basic** com credenciais de plataforma fornecidas por configuração externa da aplicação. | Decisão D-02 |
| RN-03 | Credenciais de plataforma não são hardcoded, não são commitadas e não pertencem às tabelas de identities dos tenants. | Decisão D-02 |
| RN-04 | Endpoint: `POST /platform/v1/tenants`. Não fica sob `/t/{tenantSlug}/...`. Isso não implica criar outro serviço nesta fatia. | Decisão D-03 |
| RN-05 | Entrada mínima: `slug`, `username`, `password`. Sem `displayName`. | Decisão D-04 |
| RN-06 | Password inicial: comprimento mínimo **12** caracteres; sem regras adicionais de complexidade nesta fatia. | Decisão D-05 |
| RN-07 | Password é armazenada exclusivamente como hash Argon2id. Nunca persistida nem logada em texto puro. | Decisão D-05 |
| RN-08 | Em sucesso, o tenant existe com status `ACTIVE`. Sem `PROVISIONING`, `FAILED` ou state machine. | Decisão D-06 |
| RN-09 | A identity criada é apenas a **primeira identity autenticável** do tenant; nasce `enabled = true`. Sem significado de admin/roles. | Decisão D-07 |
| RN-10 | Resposta de sucesso: HTTP `201` com somente `tenantId`, `slug`, `identityId`, `username`. Nunca password, password hash, credencial Basic da plataforma ou outro segredo. | Decisão D-08 |
| RN-11 | Slug de tenant já existente → `409` com `code` = `TENANT_EXISTS`. Não há contrato `IDENTITY_EXISTS` nesta fatia. | Decisão D-09 |
| RN-12 | Username duplicado não é cenário funcional esperado desta operação (tenant novo + primeira identity). A constraint `(tenant_id, username)` permanece como invariável de persistência. | Decisão D-09 |
| RN-13 | `DevDataSeeder` pode permanecer em `dev`/`test`. O fluxo provisionamento → login → `/me` deve ser demonstrável sem depender dos dados do seeder. O seeder não é removido nesta fatia. | Decisão D-10 |
| RN-14 | Criação de tenant e primeira identity é **atômica**. Sucesso = ambos existem. Qualquer falha durante o provisionamento reverte tudo: sem tenant parcial, sem identity órfã. Sem estados intermediários. | Decisão D-11 |
| RN-15 | Basic Auth ausente e Basic Auth inválido são externamente indistinguíveis: ambos `401` com `code` = `PLATFORM_UNAUTHORIZED`. Não usar `403`. Nenhuma tentativa não autenticada cria tenant ou identity. | Decisão A-01 |
| RN-16 | `PLATFORM_UNAUTHORIZED` não substitui `INVALID_CREDENTIALS` (login de identities) nem `INVALID_TOKEN` (Bearer). | Decisão A-01 |
| RN-17 | Violações de entrada retornam `400` com `code` = `INVALID_REQUEST`, indistinguíveis entre si. Inclui pelo menos: `slug` ausente/vazio; `slug` inválido; `username` ausente/vazio; `password` ausente/vazia; `password` com menos de 12 caracteres. Sem códigos específicos por campo. Requests inválidos não deixam tenant nem identity persistidos. | Decisão A-02 |
| RN-18 | Autenticação de operador de plataforma **não** concede papel, membership ou autoridade dentro de tenants. | Segurança / D-01 |
| RN-19 | O `slug` provisionado deve ser utilizável pelo path `/t/{tenantSlug}/...` das Specs 001/002 (mesmas regras de formato/normalização já usadas pelo domínio de tenant do produto). Slug que não satisfaz essas regras é `400 INVALID_REQUEST`. | Consistência com Specs 001/002 + A-02 |

### Distinções importantes

| Situação | HTTP | `code` | Efeito colateral |
|---|---|---|---|
| Sucesso | `201` | — (corpo de criação) | Tenant `ACTIVE` + identity habilitada criados |
| Entrada inválida | `400` | `INVALID_REQUEST` | Nada persistido |
| Basic ausente ou inválido | `401` | `PLATFORM_UNAUTHORIZED` | Nada persistido |
| Slug já existente | `409` | `TENANT_EXISTS` | Nada novo persistido; tenant existente inalterado |
| Falha interna durante criação | falha da operação | — | Reversão completa (RN-14) |

### Segurança (explícito)

- Sem Basic Auth de plataforma válido → `401 PLATFORM_UNAUTHORIZED`; nada é criado.
- Basic Auth inválido → `401 PLATFORM_UNAUTHORIZED`; nada é criado.
- Credenciais de plataforma são independentes das identities dos tenants.
- Password inicial nunca aparece na resposta.
- Password hash nunca aparece na resposta.
- Password é armazenada via Argon2id.
- Autenticação administrativa desta fatia não cria sistema administrativo maior nem papel dentro de tenants.

---

## 7. Contrato HTTP

### 7.1 Provisionamento

`POST /platform/v1/tenants`

Autenticação:

```http
Authorization: Basic <base64(platform-username:platform-password)>
```

Request:

```json
{
  "slug": "<tenant-slug>",
  "username": "<first-identity-username>",
  "password": "<initial-password>"
}
```

### 7.2 Sucesso

- Status: `201 Created`
- Corpo:

```json
{
  "tenantId": "<uuid>",
  "slug": "<tenant-slug>",
  "identityId": "<uuid>",
  "username": "<first-identity-username>"
}
```

Valores:

- `tenantId`: UUID do tenant criado (`ACTIVE`);
- `slug`: slug do tenant (forma utilizável no path das Specs 001/002);
- `identityId`: UUID da primeira identity (`enabled = true`);
- `username`: username dessa identity.

O corpo **não** contém password, password hash nem credenciais de plataforma.

### 7.3 Erros

Modelo mínimo de erro (alinhado às Specs 001/002):

```json
{
  "code": "<CODE>"
}
```

| Situação | HTTP | `code` | Persistência |
|---|---|---|---|
| `slug` ausente ou vazio | `400` | `INVALID_REQUEST` | Nada criado |
| `slug` inválido | `400` | `INVALID_REQUEST` | Nada criado |
| `username` ausente ou vazio | `400` | `INVALID_REQUEST` | Nada criado |
| `password` ausente ou vazia | `400` | `INVALID_REQUEST` | Nada criado |
| `password` com menos de 12 caracteres | `400` | `INVALID_REQUEST` | Nada criado |
| Basic Auth ausente | `401` | `PLATFORM_UNAUTHORIZED` | Nada criado |
| Basic Auth inválido | `401` | `PLATFORM_UNAUTHORIZED` | Nada criado |
| Slug de tenant já existente | `409` | `TENANT_EXISTS` | Nada novo criado |

Notas de consistência:

- Os cinco casos de `INVALID_REQUEST` são **indistinguíveis** entre si na resposta externa. Não existem códigos por campo.
- Basic ausente e Basic inválido são **indistinguíveis**. Não usar `403`.
- `409 TENANT_EXISTS` permanece separado de validação de entrada e de autenticação de plataforma.
- Em todos os erros desta tabela, e em falha atômica durante a criação (RN-14), **não** permanece tenant parcial nem identity órfã.

---

## 8. Cenários

### 8.1 Provisionamento válido

**Dado** uma instalação sem o tenant alvo  
**E** credenciais de plataforma válidas configuradas  
**Quando** `POST /platform/v1/tenants` com Basic Auth válido e corpo `{slug, username, password}` válidos (password ≥ 12)  
**Então** a resposta é `201`  
**E** o corpo contém somente `tenantId`, `slug`, `identityId`, `username`  
**E** o tenant existe com status `ACTIVE`  
**E** a identity existe, está habilitada e possui password hash Argon2id (não plaintext)

### 8.2 Login após provisionamento

**Dado** o cenário 8.1 concluído com sucesso  
**Quando** `POST /t/{slug}/v1/auth/login` com o mesmo `username` e a mesma `password` fornecidos no provisionamento  
**Então** a autenticação sucede conforme a Spec 001  
**E** um `accessToken` é emitido

### 8.3 `/me` após login do tenant provisionado

**Dado** o cenário 8.2  
**Quando** `GET /t/{slug}/v1/me` com `Authorization: Bearer <accessToken>`  
**Então** a resposta é `200` conforme a Spec 002  
**E** `sub` = `identityId` provisionado, `username` e `tid` = `tenantId` provisionado

### 8.4 Sem Basic Auth

**Quando** `POST /platform/v1/tenants` sem autenticação de plataforma  
**Então** a resposta é `401` com `code` = `PLATFORM_UNAUTHORIZED`  
**E** a resposta **não** é `403`  
**E** nenhum tenant nem identity são criados

### 8.5 Basic Auth inválido

**Quando** `POST /platform/v1/tenants` com credenciais Basic que não correspondem às configuradas  
**Então** a resposta é `401` com `code` = `PLATFORM_UNAUTHORIZED`  
**E** a falha é indistinguível da de Basic ausente  
**E** a resposta **não** é `403`  
**E** nenhum tenant nem identity são criados

### 8.6 Slug duplicado

**Dado** um tenant cujo `slug` já existe  
**E** credenciais de plataforma válidas  
**Quando** `POST /platform/v1/tenants` tenta provisionar o mesmo `slug`  
**Então** a resposta é `409` com `code` = `TENANT_EXISTS`  
**E** nenhuma identity adicional é criada por essa tentativa  
**E** o tenant existente permanece inalterado quanto aos dados desta operação

### 8.7 Password menor que 12 caracteres

**Dado** credenciais de plataforma válidas  
**Quando** `POST /platform/v1/tenants` com `password` de comprimento &lt; 12  
**Então** a resposta é `400` com `code` = `INVALID_REQUEST`  
**E** nenhum tenant nem identity são criados

### 8.8 Demais violações de entrada

**Dado** credenciais de plataforma válidas  
**Quando** `POST /platform/v1/tenants` apresenta qualquer uma das violações: `slug` ausente/vazio; `slug` inválido; `username` ausente/vazio; `password` ausente/vazia  
**Então** a resposta é `400` com `code` = `INVALID_REQUEST`  
**E** a falha é indistinguível da de password &lt; 12 (cenário 8.7)  
**E** nenhum tenant nem identity são criados  
**E** não há códigos específicos por campo

### 8.9 Atomicidade em falha

**Dado** credenciais de plataforma válidas e dados de entrada válidos  
**Quando** ocorre falha durante a criação da identity (após qualquer escrita intermediária de tenant, se houver)  
**Então** a operação falha  
**E** não permanece tenant parcialmente provisionado  
**E** não permanece identity órfã  
**E** uma nova tentativa com o mesmo `slug` não encontra o tenant “fantasma” da tentativa anterior (o slug continua disponível ou o estado é o anterior à tentativa falha)

### 8.10 Independência do DevDataSeeder

**Dado** uma instalação em que o fluxo é exercitado **sem** consumir os dados plantados pelo `DevDataSeeder` (por exemplo, seeder desabilitado ou banco sem os fixtures do seeder)  
**Quando** os cenários 8.1 → 8.2 → 8.3 são executados  
**Então** o fluxo completo sucede  
**E** o `DevDataSeeder` pode continuar existindo nos perfis `dev`/`test` sem ser requisito desse caminho

### 8.11 Credenciais de plataforma ≠ identity de tenant

**Dado** credenciais de plataforma válidas distintas de qualquer identity de tenant  
**Quando** o provisionamento sucede e o login do tenant usa `username`/`password` do corpo (não as credenciais Basic)  
**Então** o login do tenant não depende das credenciais Basic  
**E** as credenciais Basic não autenticam como identity sob `/t/{slug}/...`  
**E** `PLATFORM_UNAUTHORIZED` não é usado no contrato de login (`INVALID_CREDENTIALS`) nem no de `/me` (`INVALID_TOKEN`)

---

## 9. Critérios de aceitação

A entrega estará concluída quando for possível demonstrar, de forma automatizada ou reproduzível, que:

1. **CA-01** — Cenário 8.1: Basic válido + dados válidos → `201` com somente `tenantId`, `slug`, `identityId`, `username`; tenant `ACTIVE`; identity habilitada.
2. **CA-02** — Password persistida exclusivamente como Argon2id; plaintext nunca na resposta nem no armazenamento consultável da identity.
3. **CA-03** — Cenário 8.2: login Spec 001 com a password fornecida no provisionamento sucede e emite access token.
4. **CA-04** — Cenário 8.3: esse token acessa `/me` conforme Spec 002 com `sub`/`username`/`tid` coerentes.
5. **CA-05** — Cenário 8.4: sem Basic Auth → `401 PLATFORM_UNAUTHORIZED`; não é `403`; nada criado.
6. **CA-06** — Cenário 8.5: Basic Auth inválido → `401 PLATFORM_UNAUTHORIZED`, indistinguível de CA-05; não é `403`; nada criado.
7. **CA-07** — Cenário 8.6: slug duplicado → `409 TENANT_EXISTS`; nada novo criado.
8. **CA-08** — Cenário 8.7: password &lt; 12 → `400 INVALID_REQUEST`; nada criado.
9. **CA-09** — Cenário 8.8: demais violações de entrada → `400 INVALID_REQUEST`, indistinguíveis entre si e de CA-08; sem códigos por campo; nada criado.
10. **CA-10** — Cenário 8.9: falha durante criação da identity → tenant também não permanece criado (atomicidade).
11. **CA-11** — Cenário 8.10: fluxo provisionamento → login → `/me` demonstrável sem depender do `DevDataSeeder`.
12. **CA-12** — Cenário 8.11: credenciais de plataforma independentes das identities dos tenants; Basic não concede papel dentro do tenant; `PLATFORM_UNAUTHORIZED` não substitui `INVALID_CREDENTIALS` nem `INVALID_TOKEN`.
13. **CA-13** — Contratos das Specs 001 e 002 permanecem inalterados.
14. **CA-14** — Nenhum comportamento listado em **Fora do escopo** foi entregue como parte desta fatia.
15. **CA-15** — `DevDataSeeder` não foi removido (pode permanecer em `dev`/`test`).

---

## 10. Decisões tomadas (D-01–D-11, A-01–A-02)

| ID | Decisão |
|---|---|
| **D-01** | Autoridade: operador da plataforma. Sem platform users, roles ou RBAC. |
| **D-02** | Autenticação: HTTP Basic com credenciais de plataforma via configuração externa. Sem hardcode/commit; fora das identities de tenant. Sem API keys / OAuth2 admin. |
| **D-03** | Contrato: `POST /platform/v1/tenants` (plataforma). Mesmo processo; sem segundo deployável. |
| **D-04** | Entrada: `slug`, `username`, `password`. Sem `displayName`. |
| **D-05** | Password mínima: 12 caracteres; sem complexidade extra; armazenamento Argon2id; nunca plaintext em persistência/log. |
| **D-06** | Sucesso → tenant `ACTIVE`. Sem `PROVISIONING` / `FAILED` / state machine. |
| **D-07** | Identity = primeira autenticável; `enabled = true`; sem significado admin/roles. |
| **D-08** | Resposta: `tenantId`, `slug`, `identityId`, `username`. Status HTTP de criação: `201`. Sem password/hash/segredos. |
| **D-09** | Slug duplicado: `409 TENANT_EXISTS`. Sem contrato `IDENTITY_EXISTS` nesta fatia. |
| **D-10** | `DevDataSeeder` permanece em `dev`/`test`; fluxo real não depende dele; seeder não é removido. |
| **D-11** | Operação atômica tenant + primeira identity; falha reverte tudo; sem estados intermediários. |
| **A-01** | Basic ausente e Basic inválido: ambos `401 PLATFORM_UNAUTHORIZED`, indistinguíveis; sem `403`; nada criado. Código exclusivo da fronteira de plataforma; não substitui `INVALID_CREDENTIALS` nem `INVALID_TOKEN`. |
| **A-02** | Violações de entrada: `400 INVALID_REQUEST` (slug ausente/vazio/inválido; username ausente/vazio; password ausente/vazia/&lt;12), indistinguíveis; sem códigos por campo; nada persistido. Duplicidade permanece `409 TENANT_EXISTS`. |

Não há decisões em aberto que bloqueiem o início do planejamento técnico / implementação desta fatia.

---

## Revisão RAES (checklist desta Spec)

- **Value First:** habilita o primeiro login real a partir de instalação vazia.
- **Domain First:** tenant + primeira identity autenticável; sem papéis administrativos de produto.
- **Spec-Driven Development:** contrato, CA e decisões humanas fechadas antes da implementação.
- **Incremental Delivery:** uma operação vertical mínima sobre Specs 001/002.
- **YAGNI:** sem onboarding MVP, roles, JWKS, CRUD, segundo serviço.
- **AI Never Guesses:** A-01 e A-02 fechadas por decisão humana; nenhuma lacuna bloqueante permanece inventada.
