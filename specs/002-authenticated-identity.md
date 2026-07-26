# Spec 002 — Authenticated identity

**Status:** aprovada (decisões D-01–D-11 fechadas)  
**Entrega:** acesso a um recurso autenticado utilizando o access token emitido pelo Auth-Saas  
**Depende de:** Spec 001 — Password authentication  
**Idioma:** PT-BR

---

## 1. Contexto

A Spec 001 permite autenticar por password e emitir um access token JWT no contexto de um tenant. Esse token, entretanto, ainda não concede acesso a nenhum recurso: o consumidor recebe uma prova de autenticação que não pode ser consumida.

Esta entrega fecha o fluxo mínimo de produto:

`login → access token → Bearer token → recurso autenticado`

Ela prova que um access token válido emitido pela Spec 001 funciona como credencial para acessar um recurso protegido no tenant correto, sem introduzir autorização por roles, permissions ou scopes.

---

## 2. Objetivo

Dado um access token JWT válido emitido pela Spec 001, o sistema deve:

1. aceitar a credencial via `Authorization: Bearer <access-token>`;
2. validar assinatura e expiração do JWT;
3. garantir que o `tid` do token corresponde ao tenant do path `{tenantSlug}` e que esse tenant continua existindo e `ACTIVE`;
4. materializar a identity correspondente ao `sub` para obter o `username`;
5. conceder acesso a `GET /t/{tenantSlug}/v1/me`;
6. retornar a identidade autenticada atual (`sub`, `username`, `tid`);
7. rejeitar credenciais inválidas ou contextos de tenant inelegíveis conforme os cenários desta Spec.

---

## 3. Escopo

Comportamentos desta entrega:

- Proteger `GET /t/{tenantSlug}/v1/me` com autenticação Bearer JWT.
- Extrair e validar o access token via Spring Security Resource Server (assinatura, expiração, estabelecimento de contexto autenticado).
- Exigir correspondência entre o `tid` do token e o tenant identificado por `{tenantSlug}`.
- Exigir que o tenant do path exista e esteja `ACTIVE` no momento do acesso.
- Materializar a identity do `sub` no tenant do contexto para obter `username` (sem revalidar password nem `enabled`).
- Retornar a representação mínima da identidade autenticada atual.
- Aplicar `401 INVALID_TOKEN` aos cenários de credencial Bearer e de identity removida definidos nesta Spec.
- Aplicar `404 TENANT_NOT_FOUND` quando o tenant do path não existe ou não está `ACTIVE`.
- Preservar o contrato de login da Spec 001 sem alteração.
- Aceitar as chaves RSA efêmeras já usadas pela Spec 001 (verificação dentro do próprio Auth-Saas).

---

## 4. Fora do escopo

Explicitamente fora desta entrega:

- refresh token
- logout
- revogação de access token
- roles, permissions, scopes
- RBAC / ABAC
- autenticação por API key
- OIDC / OAuth2
- MFA
- JWKS / publicação de chave pública
- key rotation
- key management de produção
- recursos de negócio além de `/me`
- profile management
- Spring WebFlux
- R2DBC
- mudança da estratégia multi-tenant
- alteração do contrato de login da Spec 001
- invalidação imediata do token quando a identity for apenas desabilitada após a emissão
- cache de tenant ou de identidade

---

## 5. Relação com a Spec 001

Esta Spec **consome** o access token definido pela Spec 001:

| Claim / dado | Uso nesta Spec |
|---|---|
| `sub` | Identifica a identity autenticada; retornado em `/me`; usado para localizar a identity e obter `username` |
| `tid` | Identifica o tenant do token; deve corresponder ao tenant do path |
| `exp` | Token expirado é rejeitado |
| Assinatura RS256 | Validada pelo Resource Server com a chave pública do emissor |

O login (`POST /t/{tenantSlug}/v1/auth/login`) permanece exatamente como na Spec 001.

Chaves efêmeras da Spec 001 continuam aceitas: esta fatia prova consumo do token **dentro** do Auth-Saas, não por consumidores externos.

---

## 6. Regras de negócio

| ID | Regra | Origem |
|---|---|---|
| RN-01 | O recurso autenticado desta fatia é `GET /t/{tenantSlug}/v1/me`. | Decisão D-01 |
| RN-02 | A resposta de sucesso contém exatamente `sub`, `username` e `tid` (sem roles, permissions, scopes ou perfil). | Decisão D-02 |
| RN-03 | A credencial é apresentada como `Authorization: Bearer <access-token>`. | Decisão D-03 |
| RN-04 | Validação Bearer/JWT na borda usa Spring Security Resource Server (extração, assinatura, expiração, contexto autenticado, proteção do endpoint). | Decisão D-04 |
| RN-05 | O `tid` do token deve corresponder ao tenant do `{tenantSlug}`. Token de outro tenant é rejeitado com `401 INVALID_TOKEN`. | Decisão D-05 / D-06 |
| RN-06 | Credencial Bearer ausente, malformada, com assinatura inválida, expirada ou de outro tenant produz `401` com `INVALID_TOKEN`, sem distinção externa entre esses casos. | Decisão D-06 |
| RN-07 | Desabilitar a identity após a emissão do token **não** invalida o token nesta fatia. O token permanece válido até `exp`. O acesso autenticado **não** consulta `enabled` nem password para revalidação. | Decisão D-07 / D-11 |
| RN-08 | O tenant do path deve existir e estar `ACTIVE` no momento do acesso autenticado. Tenant inexistente ou não `ACTIVE` produz `404 TENANT_NOT_FOUND`, indistinguíveis entre si. Essa resposta representa indisponibilidade do contexto de tenant, não invalidade criptográfica do token. | Decisão D-08 / D-10 |
| RN-09 | Chaves RSA efêmeras da Spec 001 permanecem aceitas; publicação de chave pública permanece fora do escopo. | Decisão D-09 |
| RN-10 | O `username` retornado em `/me` é obtido a partir da identity correspondente ao `sub` no tenant do contexto. Essa leitura serve à representação da resposta, não à revalidação de `enabled` ou password. | Decisão D-02 / D-07 / D-11 |
| RN-11 | Login da Spec 001 permanece público (sem Bearer). Somente `/me` exige autenticação nesta fatia. | Decisão D-01 + preservação da Spec 001 |
| RN-12 | Se o JWT for válido e o `tid` corresponder ao tenant `ACTIVE` do path, mas o `sub` não corresponder a uma identity existente nesse tenant, a resposta é `401 INVALID_TOKEN`. | Decisão D-11 |

### Distinções importantes

| Situação | Resultado | Não confundir com |
|---|---|---|
| Token de tenant A no path do tenant B (ambos existentes) | `401 INVALID_TOKEN` | Tenant inexistente/inativo |
| Path aponta para tenant inexistente ou não `ACTIVE` | `404 TENANT_NOT_FOUND` | Cross-tenant / token inválido |
| Identity existe e `enabled = false` após emissão | `200` (token válido até `exp`) | Identity removida |
| Identity removida após emissão (`sub` sem registro) | `401 INVALID_TOKEN` | Identity apenas desabilitada |

---

## 7. Contrato HTTP

### 7.1 Identidade autenticada

`GET /t/{tenantSlug}/v1/me`

Header obrigatório:

```http
Authorization: Bearer <access-token>
```

### 7.2 Sucesso

- Status: `200`
- Corpo:

```json
{
  "sub": "<identity-id>",
  "username": "<username>",
  "tid": "<tenant-id>"
}
```

Valores:

- `sub`: UUID da identity autenticada (mesmo valor do claim `sub` do token);
- `username`: username atual da identity no tenant;
- `tid`: UUID do tenant (mesmo valor do claim `tid` do token e do tenant do path).

### 7.3 Erros de credencial / subject

Modelo mínimo:

```json
{
  "code": "<CODE>"
}
```

| Situação | HTTP | `code` |
|---|---|---|
| Bearer ausente | `401` | `INVALID_TOKEN` |
| Bearer malformado | `401` | `INVALID_TOKEN` |
| Assinatura inválida | `401` | `INVALID_TOKEN` |
| Token expirado | `401` | `INVALID_TOKEN` |
| Token de outro tenant (cross-tenant) | `401` | `INVALID_TOKEN` |
| Identity do `sub` inexistente no tenant | `401` | `INVALID_TOKEN` |

Esses seis casos são **indistinguíveis** entre si na resposta externa.

### 7.4 Erros de tenant no path

| Situação | HTTP | `code` |
|---|---|---|
| Tenant inexistente | `404` | `TENANT_NOT_FOUND` |
| Tenant com status ≠ `ACTIVE` | `404` | `TENANT_NOT_FOUND` |

Esses dois casos são **indistinguíveis** entre si. Representam indisponibilidade do contexto de tenant solicitado — não invalidade criptográfica do access token.

---

## 8. Cenários

### 8.1 Token válido

**Dado** um tenant `ACTIVE`  
**E** um access token válido emitido pela Spec 001 para uma identity existente desse tenant  
**Quando** `GET /t/{tenantSlug}/v1/me` com `Authorization: Bearer <token>`  
**Então** a resposta é `200`  
**E** o corpo contém `sub` da identity, `username` atual e `tid` do tenant  
**E** `sub` e `tid` correspondem aos claims do token

### 8.2 Token ausente

**Quando** `GET /t/{tenantSlug}/v1/me` sem header `Authorization`  
**Então** a resposta é `401` com `code` = `INVALID_TOKEN`

### 8.3 Token malformado

**Quando** `GET /t/{tenantSlug}/v1/me` com Bearer que não é um JWT válido  
**Então** a resposta é `401` com `code` = `INVALID_TOKEN`

### 8.4 Assinatura inválida

**Quando** `GET /t/{tenantSlug}/v1/me` com um JWT cujo conteúdo é estruturalmente válido, porém assinado por chave diferente da do emissor  
**Então** a resposta é `401` com `code` = `INVALID_TOKEN`

### 8.5 Token expirado

**Quando** `GET /t/{tenantSlug}/v1/me` com um JWT cujo `exp` está no passado  
**Então** a resposta é `401` com `code` = `INVALID_TOKEN`

### 8.6 Cross-tenant

**Dado** um access token válido emitido para o tenant A  
**E** o tenant B existe e está `ACTIVE`  
**Quando** esse token é usado em `GET /t/{slug-do-tenant-B}/v1/me`  
**Então** a resposta é `401` com `code` = `INVALID_TOKEN`  
**E** a falha é indistinguível das demais falhas de credencial/subject desta Spec  
**E** a resposta **não** é `404 TENANT_NOT_FOUND`

### 8.7 Tenant inexistente

**Dado** um access token estruturalmente válido  
**Quando** `GET /t/{tenantSlug}/v1/me` usa um `{tenantSlug}` que não existe  
**Então** a resposta é `404` com `code` = `TENANT_NOT_FOUND`  
**E** nenhum corpo de identidade autenticada é retornado

### 8.8 Tenant inativo

**Dado** um access token válido emitido quando o tenant estava `ACTIVE`  
**E** o tenant passou a status diferente de `ACTIVE`  
**Quando** `GET /t/{tenantSlug}/v1/me` é chamado com esse token no path do mesmo tenant  
**Então** a resposta é `404` com `code` = `TENANT_NOT_FOUND`  
**E** a falha é indistinguível da de tenant inexistente  
**E** nenhum corpo de identidade autenticada é retornado

A verificação de status do tenant ocorre no momento do acesso; não há cache nem revogação de token.

### 8.9 Identity desabilitada após emissão

**Dado** um access token válido emitido para uma identity habilitada  
**E** a identity continua existindo e é subsequentemente marcada com `enabled = false`  
**Quando** `GET /t/{tenantSlug}/v1/me` é chamado com esse token antes de `exp`  
**Então** o acesso é permitido (`200`)  
**E** a resposta inclui `sub`, `username` e `tid`  
**E** o sistema **não** rejeita o request com base no campo `enabled` da identity  
**E** o sistema **não** revalida password

Este cenário deixa explícito: **não há lookup de `enabled` nem de password para decidir autenticação nesta fatia**. Há leitura da identity apenas para obter `username` (RN-10).

### 8.10 Identity removida após emissão

**Dado** um access token válido emitido para uma identity  
**E** a identity correspondente ao `sub` deixa de existir no tenant  
**Quando** `GET /t/{tenantSlug}/v1/me` é chamado com esse token (tenant `ACTIVE`, `tid` correspondente)  
**Então** a resposta é `401` com `code` = `INVALID_TOKEN`  
**E** a falha é indistinguível das demais falhas de credencial/subject desta Spec  
**E** nenhum mecanismo de revogação foi introduzido — apenas a identity não é mais materializável para obter `username`

---

## 9. Critérios de aceitação

A entrega estará concluída quando for possível demonstrar, de forma automatizada ou reproduzível, que:

1. **CA-01** — Cenário 8.1: token válido + tenant correto e `ACTIVE` + identity existente → `200` com `sub`, `username` e `tid` corretos.
2. **CA-02** — Cenário 8.2: sem Bearer → `401 INVALID_TOKEN`.
3. **CA-03** — Cenário 8.3: Bearer malformado → `401 INVALID_TOKEN`.
4. **CA-04** — Cenário 8.4: assinatura inválida → `401 INVALID_TOKEN`.
5. **CA-05** — Cenário 8.5: token expirado → `401 INVALID_TOKEN`.
6. **CA-06** — Cenário 8.6: cross-tenant → `401 INVALID_TOKEN`, indistinguível dos demais erros de credencial/subject; não é `404 TENANT_NOT_FOUND`.
7. **CA-07** — Cenário 8.7: tenant inexistente → `404 TENANT_NOT_FOUND`.
8. **CA-08** — Cenário 8.8: tenant inativo → `404 TENANT_NOT_FOUND`, indistinguível de CA-07.
9. **CA-09** — Cenário 8.9: identity existente e desabilitada após emissão → `200` até `exp`; sem rejeição por `enabled` ou password.
10. **CA-10** — Cenário 8.10: identity removida após emissão → `401 INVALID_TOKEN`.
11. **CA-11** — `POST /t/{tenantSlug}/v1/auth/login` permanece acessível sem Bearer e preserva o contrato da Spec 001.
12. **CA-12** — Nenhum comportamento listado em **Fora do escopo** foi entregue como parte desta fatia.

---

## 10. Decisões tomadas (D-01–D-11)

| ID | Decisão |
|---|---|
| **D-01** | Recurso: `GET /t/{tenantSlug}/v1/me` — identidade autenticada atual. |
| **D-02** | Resposta mínima: `sub`, `username`, `tid`. Sem roles/permissions/scopes/perfil. |
| **D-03** | Credencial: `Authorization: Bearer <access-token>`. |
| **D-04** | Validação na borda: Spring Security Resource Server (mínimo necessário à Spec). |
| **D-05** | Cross-tenant rejeitado: `tid` do token deve corresponder ao tenant do path. |
| **D-06** | Erros de credencial Bearer (ausente, malformado, assinatura inválida, expirado, outro tenant): `401 INVALID_TOKEN`, indistinguíveis. |
| **D-07** | Identity desabilitada após emissão não invalida o token até `exp`; sem revalidação de `enabled` no acesso. |
| **D-08** | Tenant deve existir e estar `ACTIVE` no momento do acesso autenticado. |
| **D-09** | Chaves efêmeras da Spec 001 permanecem aceitas; sem JWKS/publicação de chave nesta fatia. |
| **D-10** | Tenant inexistente ou não `ACTIVE` em `/me`: `404 TENANT_NOT_FOUND`, indistinguíveis; representa indisponibilidade do contexto de tenant, não invalidade criptográfica do token. Cross-tenant permanece `401 INVALID_TOKEN`. |
| **D-11** | Identity removida após emissão (`sub` sem identity no tenant): `401 INVALID_TOKEN`. Identity apenas desabilitada continua válida até `exp`. Sem revalidar password/`enabled`. Sem mecanismo de revogação. |

Não há decisões em aberto que bloqueiem o início do planejamento técnico / implementação desta fatia.

---

## Revisão RAES (checklist desta Spec)

- **Value First:** fecha o ciclo autenticar → acessar com o menor recurso possível (`/me`).
- **Domain First:** foco em identidade autenticada e fronteira de tenant.
- **Spec-Driven Development:** contrato, CA e decisões fechadas antes da implementação.
- **Incremental Delivery:** uma fatia vertical pequena sobre a Spec 001.
- **YAGNI:** sem refresh, roles, JWKS, revogação ou recursos de negócio.
- **AI Never Guesses:** D-10 e D-11 foram fechadas por decisão humana; nenhuma lacuna bloqueante permanece inventada.
)
