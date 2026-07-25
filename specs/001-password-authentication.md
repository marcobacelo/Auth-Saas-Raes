# Spec 001 — Password authentication

**Status:** aprovada (decisões D-01–D-07 fechadas)  
**Entrega:** autenticar um usuário por senha no contexto de um tenant ativo e emitir um access token verificável  
**Idioma:** PT-BR

---

## 1. Contexto

Produtos B2B precisam autenticar usuários de organizações distintas (tenants) e obter uma prova de autenticação utilizável pelas aplicações consumidoras.

Esta entrega foi escolhida como primeira fatia porque materializa o valor central do produto Auth SaaS — autenticação — de forma pequena, verificável e evolutiva, sem exigir a plataforma completa.

O repositório `Auth-Saas` é a fonte de conhecimento do MVP existente. Esta Spec extrai dele apenas o necessário para esta fatia. Não é uma reprodução do MVP nem a especificação do produto inteiro.

---

## 2. Objetivo

Dado um tenant ativo e uma identity com password cadastrada, o sistema deve:

1. receber uma tentativa de autenticação no contexto desse tenant;
2. validar a password;
3. em caso de sucesso, emitir um access token JWT verificável que represente o subject autenticado e o tenant correspondente;
4. em caso de falha, negar a autenticação de forma explícita, conforme os cenários e o contrato HTTP desta Spec.

---

## 3. Escopo

Comportamentos desta entrega:

- Identificar o tenant pelo path `/t/{tenantSlug}/...`.
- Aceitar autenticação via `POST /t/{tenantSlug}/v1/auth/login` com `username` e `password`.
- Validar que o tenant existe e está `ACTIVE`.
- Localizar a identity correspondente ao `username` no escopo desse tenant.
- Validar a `password` contra o hash Argon2id armazenado.
- Emitir um access token JWT assinado e verificável localmente quando a autenticação for válida.
- Garantir que o JWT permita verificar: `sub` (identity), identificador do tenant e `exp`.
- Retornar falhas HTTP explícitas conforme o contrato desta Spec.
- Disponibilizar fixture/seed mínimo somente para desenvolvimento e testes (sem onboarding de produto).

---

## 4. Fora do escopo

Explicitamente fora desta entrega:

- refresh token
- logout / revoke
- Redis (ou qualquer store de refresh)
- autenticação por API key
- OIDC / OAuth2
- MFA (incluindo TOTP)
- endpoint JWKS público
- console / UI
- onboarding completo de tenant
- gestão de usuários (criar, editar, desabilitar via API de produto)
- gestão de tenants (criar, suspender, listar via API de produto)
- roles, scopes e permissions no token ou na API
- identificação de tenant por header ou subdomain
- suporte a múltiplos algoritmos de password hashing
- infraestrutura genérica de seeding além do necessário para esta fatia
- emissão ou rotação de chaves de assinatura como produto
- rate limiting, lockout, audit log, risk scoring

---

## 5. Regras de negócio

| ID | Regra | Origem |
|---|---|---|
| RN-01 | Autenticação por password ocorre sempre no contexto de um tenant. | Comprovada pelo MVP |
| RN-02 | Somente tenant com status `ACTIVE` pode autenticar. Qualquer outro status é inelegível. | Comprovada pelo MVP |
| RN-03 | A identity é localizada pelo `username` dentro do tenant da requisição (sem autenticação cross-tenant). | Comprovada pelo MVP |
| RN-04 | A password nunca é armazenada em texto puro; a validação compara a password informada com o hash Argon2id armazenado. Não há suporte a outros algoritmos nesta entrega. | Comprovada pelo MVP + decisão D-05 |
| RN-05 | Password incorreta, identity inexistente e identity desabilitada produzem a mesma falha externa: `401` com `INVALID_CREDENTIALS`. | Comprovada pelo MVP + decisões D-06 / D-07 (credenciais) |
| RN-06 | Identity com `enabled = false` não autentica, mesmo com password correta. | Comprovada pelo MVP |
| RN-07 | Em autenticação válida, o JWT contém `sub` com o identificador estável da identity. | Comprovada pelo MVP + decisão D-03 |
| RN-08 | Em autenticação válida, o JWT contém identificador do tenant no qual a autenticação ocorreu. | Comprovada pelo MVP + decisão D-03 |
| RN-09 | O JWT contém `exp`. TTL default: `900` segundos, configurável. Token expirado não é válido. | Comprovada pelo MVP + decisão D-04 |
| RN-10 | O tenant da requisição é identificado exclusivamente pelo path `/t/{tenantSlug}/...`. | Decisão D-01 (alinhada ao MVP) |
| RN-11 | Tenant e identity iniciais para desenvolvimento/teste vêm de fixture/seed mínimo; sem onboarding ou gestão de produto nesta entrega. | Decisão D-02 |
| RN-12 | O access token é JWT assinado e verificável localmente. Sem endpoint JWKS nesta entrega. | Decisão D-03 |
| RN-13 | Contrato HTTP de login: `POST /t/{tenantSlug}/v1/auth/login` conforme seção 7. | Decisão D-06 (alinhada ao MVP) |
| RN-14 | Tenant inexistente e tenant inativo (não `ACTIVE`) resultam ambos em `404` com `TENANT_NOT_FOUND`, sem distinção externa. | Decisão D-07 (alinhada ao MVP) |

---

## 6. Contrato HTTP

### 6.1 Login

`POST /t/{tenantSlug}/v1/auth/login`

Request:

```json
{
  "username": "...",
  "password": "..."
}
```

### 6.2 Sucesso

Autenticação válida:

- Status: `200`
- Corpo inclui o access token JWT (sem refresh token).
- Campo do token alinhado ao MVP: `accessToken`.

Exemplo mínimo:

```json
{
  "accessToken": "<jwt>"
}
```

### 6.3 Erros

Modelo mínimo de erro (somente o necessário para verificar o contrato):

```json
{
  "code": "<CODE>"
}
```

| Situação | HTTP | `code` |
|---|---|---|
| Password inválida | `401` | `INVALID_CREDENTIALS` |
| Identity inexistente | `401` | `INVALID_CREDENTIALS` |
| Identity desabilitada | `401` | `INVALID_CREDENTIALS` |
| Tenant inexistente | `404` | `TENANT_NOT_FOUND` |
| Tenant inativo (status ≠ `ACTIVE`) | `404` | `TENANT_NOT_FOUND` |

Os três casos de credencial são indistinguíveis entre si. Os dois casos de tenant são indistinguíveis entre si.

---

## 7. Cenários

### 7.1 Autenticação válida

**Dado** um tenant ativo (disponível via fixture/seed)  
**E** uma identity existente nesse tenant, habilitada, com password Argon2id cadastrada  
**Quando** `POST /t/{tenantSlug}/v1/auth/login` com `username` e `password` corretos  
**Então** a resposta é `200`  
**E** o corpo contém `accessToken` (JWT)  
**E** o JWT verifica `sub` da identity autenticada  
**E** o JWT verifica o identificador do tenant correspondente  
**E** o JWT possui `exp` no futuro, coerente com o TTL configurado (default 900s)  
**E** a resposta não contém refresh token

### 7.2 Password inválida

**Dado** um tenant ativo  
**E** uma identity existente e habilitada nesse tenant  
**Quando** o login é feito com a `password` incorreta  
**Então** a resposta é `401` com `code` = `INVALID_CREDENTIALS`  
**E** nenhum access token é emitido

### 7.3 Identity inexistente

**Dado** um tenant ativo  
**Quando** o login é feito com um `username` que não existe nesse tenant  
**Então** a resposta é `401` com `code` = `INVALID_CREDENTIALS`  
**E** a falha é indistinguível da de password inválida  
**E** nenhum access token é emitido

### 7.4 Tenant inexistente

**Quando** o login é feito com um `tenantSlug` que não existe  
**Então** a resposta é `404` com `code` = `TENANT_NOT_FOUND`  
**E** nenhum access token é emitido

### 7.5 Tenant inativo

**Dado** um tenant existente cujo status não é `ACTIVE`  
**Quando** o login é feito no path desse tenant  
**Então** a resposta é `404` com `code` = `TENANT_NOT_FOUND`  
**E** a falha é indistinguível da de tenant inexistente  
**E** nenhum access token é emitido

### 7.6 Identity desabilitada

**Dado** um tenant ativo  
**E** uma identity existente com `enabled = false` e password correta  
**Quando** o login é feito com essas credenciais  
**Então** a resposta é `401` com `code` = `INVALID_CREDENTIALS`  
**E** a falha é indistinguível da de password inválida  
**E** nenhum access token é emitido

---

## 8. Access Token

Formato: **JWT assinado**, verificável localmente (sem JWKS nesta entrega).

Claims mínimos obrigatórios:

| Claim / dado | Descrição |
|---|---|
| `sub` | Identificador estável da identity autenticada |
| tenant | Identificador do tenant da autenticação (claim de tenant no JWT; nome concreto alinhado na implementação desde que verificável) |
| `exp` | Instantâneo de expiração |

TTL:

- Default: `900` segundos
- Deve ser configurável

Explicitamente fora do token nesta entrega:

- roles / scope / amr / username e demais claims não exigidos
- refresh token
- publicação de JWKS

Algoritmo de assinatura e gestão de chaves: definição técnica na implementação, desde que o JWT seja verificável localmente e o contrato mínimo acima seja cumprido, sem ampliar o escopo.

---

## 9. Critérios de aceitação

A entrega estará concluída quando for possível demonstrar, de forma automatizada ou reproduzível, que:

1. **CA-01** — Cenário 7.1: `200` com `accessToken` JWT; verificação local confirma `sub`, tenant e `exp` futura; sem refresh token.
2. **CA-02** — Cenário 7.2: `401` + `INVALID_CREDENTIALS`; sem token.
3. **CA-03** — Cenário 7.3: `401` + `INVALID_CREDENTIALS`; resposta indistinguível de CA-02; sem token.
4. **CA-04** — Cenário 7.4: `404` + `TENANT_NOT_FOUND`; sem token.
5. **CA-05** — Cenário 7.5: `404` + `TENANT_NOT_FOUND`; resposta indistinguível de CA-04; sem token.
6. **CA-06** — Cenário 7.6: `401` + `INVALID_CREDENTIALS`; resposta indistinguível de CA-02; sem token.
7. **CA-07** — JWT com `exp` no passado falha na verificação local de validade.
8. **CA-08** — TTL default de 900s é respeitado na emissão; valor configurável pode ser alterado e observado no `exp`.
9. **CA-09** — Passwords usadas na fixture/seed e na validação utilizam Argon2id (sem outro algoritmo).
10. **CA-10** — Tenant e identity de teste existem apenas via fixture/seed; não há API de onboarding/gestão nesta entrega.
11. **CA-11** — Nenhum comportamento listado em **Fora do escopo** foi entregue como parte desta fatia.

---

## 10. Decisões tomadas (D-01–D-07)

Registro conciso das decisões humanas que fecharam a Spec:

| ID | Decisão |
|---|---|
| **D-01** | Tenant identificado por path `/t/{tenantSlug}/...`. Sem header/subdomain nesta entrega. |
| **D-02** | Fixture/seed só para desenvolvimento e testes; sem onboarding/gestão; sem infraestrutura genérica de seeding além do necessário. |
| **D-03** | JWT assinado verificável localmente; claims mínimos `sub`, identificador do tenant, `exp`; sem JWKS; sem roles/scopes extras. Algoritmo/chaves na implementação técnica. |
| **D-04** | TTL default `900` segundos, configurável. |
| **D-05** | Hash Argon2id apenas; sem multi-algoritmo. |
| **D-06** | `POST /t/{tenantSlug}/v1/auth/login` com `{username,password}`; sucesso com `accessToken` (sem refresh); `401 INVALID_CREDENTIALS` / `404 TENANT_NOT_FOUND` conforme tabela da seção 6. |
| **D-07** | Tenant inexistente e inativo: ambos `404 TENANT_NOT_FOUND` (sem distinção externa). |

Não há decisões em aberto que bloqueiem o início do planejamento técnico / implementação desta fatia. Detalhes de stack, persistência, algoritmo JWT/chaves e organização de módulos ficam para o planejamento técnico da implementação, desde que respeitem esta Spec.

---

## Revisão RAES (checklist desta Spec)

- **Value First:** autenticação por password + access token JWT.
- **Domain First:** regras centradas em tenant, identity, password e token.
- **Spec-Driven Development:** o que precisa ser verdadeiro está definido; D-01–D-07 fechadas por decisão humana.
- **Incremental Delivery:** uma fatia vertical pequena.
- **YAGNI:** fora de escopo explícito para o restante do Auth SaaS.
- **AI Never Guesses:** decisões de produto/contrato foram humanas; não inventadas pelo agente.
)
