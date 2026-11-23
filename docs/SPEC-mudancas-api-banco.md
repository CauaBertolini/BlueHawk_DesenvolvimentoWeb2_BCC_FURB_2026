# Spec — mudanças na API e no banco

Resumo das mudanças de estrutura feitas para: aprovação de jogos na Wiki, categorias, capas, matchmaking
("Jogue"), vitrine da Loja, curtidas/ranking e estatísticas de partidas da estante. Cada bloco lista o que mudou no **banco** (migrations Flyway), na **API**
(rotas e contratos) e as **regras** que a API garante.

> Datas trafegam em ISO-8601 UTC (ex.: `2026-12-05T22:00:00Z`) e são gravadas em UTC (colunas `TIMESTAMP`).
> Respostas de rotas autenticadas continuam embrulhadas em `{ data, accessToken }` (`ApiResponseAdvice`).

## Visão geral

| Migration | O que faz |
|---|---|
| `V20261123120000__Add_Boardgame_Approval.sql` | Status de aprovação, autor e motivo de recusa em `board_game` |
| `V20261123150000__Add_Category_And_Cover.sql` | Tabela `category` (+ 6 categorias), categoria e capa em `board_game`, tabela `board_game_cover` |
| `V20261123180000__Create_Open_Match.sql` | Tabelas `open_match` e `open_match_request` (matchmaking) |
| `V20261123200000__Create_Board_Game_Like.sql` | Tabela `board_game_like` e contador `board_game.like_count` (curtidas e ranking) |

Diagrama das tabelas novas e alteradas:

```
category (id, name UNIQUE)
   ▲
   │ category_id (NULL em jogos antigos)
board_game (+ approval_status, submitted_by → user, review_note,
   │        category_id, cover_preset, cover_image_updated_at, like_count)
   │ 1:1 (ON DELETE CASCADE)
   ├── board_game_cover (board_game_id PK, content_type, data BYTEA, updated_at)
   │ N:N com user (ON DELETE CASCADE)
   └── board_game_like (board_game_id, user_id, created_at)  PK (board_game_id, user_id)

user ◄── host_id ── open_match (id, user_board_game_id → user_board_game, city, scheduled_at,
  ▲                            max_players, notes, status, created_at)
  │                     ▲
  └── user_id ── open_match_request (id, open_match_id, message, status, host_reply,
                                     created_at, decided_at)  UNIQUE (open_match_id, user_id)
```

---

## 1. Aprovação de jogos (Wiki)

Usuário comum cadastra um jogo que tem: ele entra **PENDING**, já vai para a estante do autor e só aparece
na Wiki depois que um ADMIN aprova. `Boardgame` continua sendo o catálogo universal; o que muda é o status.

### Banco — `board_game`

| Coluna | Tipo | Observação |
|---|---|---|
| `approval_status` | `VARCHAR(20) NOT NULL` | `PENDING` \| `APPROVED` \| `REJECTED` (CHECK). Jogos existentes ficaram `APPROVED`. Índice próprio. |
| `submitted_by` | `CHAR(36) NULL` → `user(id)` `ON DELETE SET NULL` | Autor do cadastro |
| `review_note` | `VARCHAR(500) NULL` | Motivo da recusa (limpo ao aprovar) |

### API

| Método | Rota | Quem | Regra |
|---|---|---|---|
| POST | `/boardgames` | logado | ADMIN → `APPROVED`. Usuário → `PENDING` + cópia na estante (`userBoardgameId` na resposta). |
| GET | `/boardgames` | público | Só `APPROVED`. `?status=PENDING\|REJECTED` só para ADMIN (403 para os demais). Ordenado por nome. |
| GET | `/boardgames/{id}` | logado | Aprovado, ou o autor, ou ADMIN. Senão **404** (não revela a fila). |
| PUT | `/boardgames/{id}` | ADMIN ou autor | Autor só enquanto não aprovado; editar um `REJECTED` o devolve a `PENDING`. |
| POST | `/boardgames/{id}/approve` | ADMIN | |
| POST | `/boardgames/{id}/reject` | ADMIN | Corpo opcional `{ "reason": "..." }` (até 500) |
| DELETE | `/boardgames/{id}` | ADMIN | |

Resposta de jogo ganhou: `approvalStatus`, `submittedBy`, `reviewNote`, `userBoardgameId` (só no POST de
usuário comum).

Regras em outras rotas:
- `POST /user-boardgames`: só na **própria** estante (ADMIN em qualquer uma) e só jogo visível para o dono
  (aprovado ou cadastrado por ele).
- `POST /offers`: só jogo **aprovado** (409 caso contrário).

## 2. Papel do usuário e token

Não mudou o banco. Mudou a forma de ler o papel:

- `UserRoleJwtAuthenticationConverter`: a cada requisição o papel (`SCOPE_USER`/`SCOPE_ADMIN`) é lido
  **do banco**, não do claim do token. Usuário removido → 401.
- `JwtRotationFilter`/`JwtTokenService.rotateToken(jwt, role)`: o token renovado grava o papel atual no claim
  `scope`. Um ADMIN rebaixado perde o acesso na hora.
- `TokenBlacklistService`: o token antigo continua aceito por uma janela curta depois de renovado
  (`security.jwt.rotation-grace-seconds`, padrão **30**). Sem isso, requisições em paralelo ou canceladas pelo
  navegador derrubavam a sessão (o servidor renovava o token e o cliente nunca recebia o novo).

## 3. Categorias

### Banco

- Nova tabela `category (id CHAR(36) PK, name VARCHAR(60) UNIQUE)`, com ids fixos para:
  Estratégia, Cooperativo, Família, Party, Cartas, Abstrato.
- `board_game.category_id CHAR(36) NULL` → `category(id)`. Jogos antigos podem ficar sem; **novos cadastros e
  edições exigem** categoria (validado na aplicação).

### API

- `GET /categories` (público) → `[{ id, name }]`, em ordem alfabética.
- `POST/PUT /boardgames` recebem `categoryId` (obrigatório, precisa existir → 400).
- Respostas de jogo trazem `categoryId` e `category` (nome).

## 4. Capa do jogo

A capa é **uma das artes do site** ou **uma foto enviada**. Sem nenhuma das duas, o front gera a arte
automática a partir do id.

### Banco

| Onde | Coluna | Observação |
|---|---|---|
| `board_game` | `cover_preset VARCHAR(20) NULL` | Uma das chaves: `waves-1`, `waves-2`, `beam-1`, `beam-2`, `peaks-1`, `peaks-2`, `valley`, `board-1`, `board-2` |
| `board_game` | `cover_image_updated_at TIMESTAMP NULL` | Quando a foto foi enviada (`NULL` = sem foto). Evita join nas listagens e versiona a URL. |
| `board_game_cover` | `board_game_id PK/FK`, `content_type`, `data BYTEA`, `updated_at` | A foto, em tabela à parte para as listagens não carregarem os bytes. `ON DELETE CASCADE`. |

### API

| Método | Rota | Quem | Regra |
|---|---|---|---|
| POST | `/boardgames/{id}/cover` | ADMIN ou autor (mesma regra do PUT) | `multipart/form-data`, campo `file`. JPEG/PNG/WebP **detectado pelos bytes**, até 2 MB (413/415). Limpa `cover_preset`. |
| DELETE | `/boardgames/{id}/cover` | idem | Volta para a arte escolhida/automática |
| GET | `/boardgames/{id}/cover` | público | Imagem com `Cache-Control: public, max-age=1 ano` e `nosniff` |

- `POST/PUT /boardgames` recebem `coverPreset` (opcional; chave inválida → 400).
- Respostas de jogo trazem `coverPreset` e `coverImageUrl` (`/boardgames/{id}/cover?v=<epoch ms>`).
- Propriedades: `spring.servlet.multipart.max-file-size=2MB`, `max-request-size=3MB`.
- O front reduz a foto no navegador (até 1200×680, JPEG) antes de enviar.
- Observação: a foto é pública pela URL (um `<img>` não envia token). A URL só é conhecida por quem já vê o jogo.

## 5. Matchmaking ("Jogue")

Um host anuncia uma partida com um jogo da estante dele (cidade, data/hora, vagas). Outros usuários pedem
para entrar com uma **mensagem**, e o host aceita ou recusa (estilo carona).

Tabelas próprias, separadas de `match`/`match_user`, que continuam sendo o **registro de partidas jogadas**
(estatísticas da estante). Assim, anunciar uma partida não conta como jogo jogado.

### Banco

`open_match`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | `CHAR(36)` PK | |
| `host_id` | `CHAR(36)` → `user` `ON DELETE CASCADE` | |
| `user_board_game_id` | `CHAR(36)` → `user_board_game` `ON DELETE CASCADE` | Cópia do jogo na estante do host |
| `city` | `VARCHAR(100)` | Índice em `LOWER(city)` |
| `scheduled_at` | `TIMESTAMP` (UTC) | Índice `(status, scheduled_at)` |
| `max_players` | `INT` (CHECK ≥ 2) | Total na mesa, **contando o host** |
| `notes` | `VARCHAR(500) NULL` | |
| `status` | `VARCHAR(20)` | `OPEN` \| `CANCELLED` |
| `created_at` | `TIMESTAMP` | |

`open_match_request`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | `CHAR(36)` PK | |
| `open_match_id` | → `open_match` `ON DELETE CASCADE` | |
| `user_id` | → `user` `ON DELETE CASCADE` | Índice próprio |
| `message` | `VARCHAR(500)` | Obrigatória |
| `status` | `VARCHAR(20)` | `PENDING` \| `ACCEPTED` \| `REJECTED` \| `CANCELLED` |
| `host_reply` | `VARCHAR(500) NULL` | Resposta opcional do host |
| `created_at`, `decided_at` | `TIMESTAMP` | |
| | `UNIQUE (open_match_id, user_id)` | Um pedido por pessoa por partida; um pedido cancelado é reaproveitado no reenvio |

### API

| Método | Rota | Quem | Regra |
|---|---|---|---|
| GET | `/open-matches?city=&boardgameId=` | público | Só `OPEN` e futuras, da mais próxima; `city` é trecho, sem diferenciar maiúsculas |
| GET | `/open-matches?mine=true` | logado | Futuras que o usuário hospeda ou em que tem pedido ativo (inclui canceladas pelo host) |
| GET | `/open-matches/{id}` | público | |
| POST | `/open-matches` | logado | `{ userBoardgameId, city, scheduledAt, maxPlayers?, notes? }` |
| DELETE | `/open-matches/{id}` | host | Cancela (`CANCELLED`) |
| POST | `/open-matches/{id}/requests` | logado | `{ message }` |
| DELETE | `/open-matches/{id}/requests/mine` | quem pediu | Desiste (pendente ou aceito) |
| GET | `/open-matches/{id}/requests` | host | Pedidos com nome, mensagem e status |
| POST | `/open-match-requests/{requestId}/accept` | host | `{ reply? }` |
| POST | `/open-match-requests/{requestId}/reject` | host | `{ reply? }` |

Resposta de partida: `id, status, city, scheduledAt, maxPlayers, players (host + aceitos), seatsLeft,
pendingRequests (só para o host), notes, host { id, name }, game { id, name, category, coverPreset,
coverImageUrl, playerNumber, playTime }, isHost, myRequest { id, status, message, hostReply } | null`.

Regras:
- Hospedar: a cópia precisa ser do host (403); o jogo precisa estar **aprovado** (409) e ser para 2+ jogadores;
  `scheduledAt` no futuro e em até 180 dias; `maxPlayers` entre 2 e o máximo do jogo (padrão: o máximo).
- Pedir: não pode ser o host; a partida precisa estar aberta, futura e com vaga (409); mensagem obrigatória;
  um pedido ativo por pessoa (409); quem foi **recusado** não pode pedir de novo (409); quem **cancelou** pode.
- Aceitar: só pedidos pendentes, com vaga e partida aberta (409).
- Limitação conhecida: dois aceites simultâneos na última vaga podem passar (sem lock); aceitável no volume atual.

## 6. Loja (vitrine)

### Banco

Nenhuma mudança: usa `offer`, `user_board_game`, `board_game` e `user`.

### API

`GET /store/offers` (público)

| Parâmetro | Padrão | Observação |
|---|---|---|
| `sort` | `recent` | `recent` (mais novos) \| `price` (mais baratos; empate pelo mais novo) |
| `q` | | Trecho do nome do jogo |
| `category` | | Nome exato da categoria |
| `limit` | `24` | Máximo `60` |

Resposta: `[{ id, price, description, createdAt, sellerName, isMine, game { id, name, description, category,
coverPreset, coverImageUrl, playerNumber, playTime, releaseYear } }]`.

Regras: só anúncios `ACTIVE` de jogos **aprovados**. A filtragem por nome/categoria é feita em memória sobre
os 300 anúncios ativos mais relevantes para a ordenação (suficiente no volume atual; com mais volume, mover
para a query).

## 7. Curtidas e ranking "Mais curtidos"

### Banco

- Nova tabela `board_game_like (board_game_id, user_id, created_at)`, PK composta (uma curtida por pessoa por
  jogo), FKs com `ON DELETE CASCADE` e índice por `user_id`.
- `board_game.like_count INT NOT NULL DEFAULT 0` (CHECK ≥ 0), contador desnormalizado com índice
  `(approval_status, like_count DESC)` para o ranking não contar curtidas a cada consulta.
- O contador só muda junto com `board_game_like`, na mesma transação (`INSERT ... ON CONFLICT DO NOTHING` /
  `DELETE` + `UPDATE like_count = like_count ± 1`). No JPA a coluna é somente leitura, então salvar um jogo
  (aprovar, editar) nunca sobrescreve o contador.

### API

| Método | Rota | Quem | Regra |
|---|---|---|---|
| POST | `/boardgames/{id}/like` | logado | Idempotente (curtir de novo não conta duas vezes). Só jogos aprovados (404 nos demais). |
| DELETE | `/boardgames/{id}/like` | logado | Idempotente |
| GET | `/boardgames?sort=likes` | público | Ranking: mais curtidos primeiro, empate pelo nome. `sort=name` (padrão) mantém a ordem alfabética. |

- As duas rotas de curtida devolvem `{ boardgameId, likeCount, likedByMe }`.
- Respostas de jogo ganharam `likeCount` e `likedByMe` (sempre `false` para visitantes). Na listagem,
  `likedByMe` vem de uma consulta só para a página inteira.
- O ranking é público e agregado: não expõe quem curtiu.

## 8. Partidas jogadas (estatísticas da estante)

O registro manual de partidas pela estante foi removido do front (`POST /matches` deixou de ser usado). As
"partidas jogadas" passam a vir **só** do Jogue.

### Banco

Nenhuma mudança: a contagem usa `open_match`, `open_match_request` e `user_board_game`.

### API

`GET /open-matches/stats/played` (logado; 401 sem token) → `[{ boardgameId, plays }]`.

Conta, por jogo, as partidas do Jogue **já realizadas** (`scheduled_at` no passado e `status = OPEN`, ou seja,
não canceladas) em que o usuário foi o **host** ou teve o pedido **aceito**. A estante soma partidas e horas
(duração do jogo × partidas) a partir disso.

## 9. Segurança (rotas públicas)

Liberadas sem token (`SecurityConfig`), todas `GET`:
`/boardgames`, `/categories`, `/boardgames/*/cover`, `/open-matches`, `/open-matches/*`, `/store/offers`.
Todas as demais continuam exigindo token; as permissões finas (autor, host, ADMIN) ficam nos casos de uso.

Como o usuário chega aos casos de uso:

- `CurrentUser` (`application/dto/auth`): o usuário da requisição atual, `{ userId, admin }`.
  Visitantes recebem `CurrentUser.anonymous()` (`userId` nulo).
- Os controllers declaram um parâmetro `CurrentUser currentUser`, preenchido pelo
  `CurrentUserArgumentResolver` (registrado em `CurrentUserWebConfig`) a partir do token. Assim fica visível na
  assinatura quais rotas dependem de quem está logado, e a camada de aplicação não conhece o Spring Security.
- `Roles.ADMIN_ONLY` é a expressão de `@PreAuthorize` para rotas só de ADMIN.

`/open-matches/stats/played` **não** é pública (tem dois segmentos depois de `/open-matches`).

## Pendências conhecidas

- As tabelas `match` e `match_user` (registro manual antigo) seguem no banco e na API, sem uso pelo front.
  Podem ser removidas numa migration futura, depois de decidir o que fazer com os registros existentes.
- Os Termos de uso e a Política de privacidade (front, rotas `/termos` e `/privacidade`) têm campos a preencher:
  responsável pelo serviço e e-mail do encarregado de dados. Excluir a conta hoje é feito a pedido
  (`DELETE /users/{id}` falha se o usuário tem jogos na estante, por FK).

- `PUT /user-boardgames` ainda permite trocar o jogo de uma cópia sem checar aprovação/dono.
- `DELETE /boardgames/{id}` de um jogo que está em alguma estante falha por FK (500).
- Rotas antigas de `match`/`match_user` seguem abertas a qualquer usuário logado (sem checar dono).
- Não há tela/rota para promover um usuário a ADMIN (hoje é `UPDATE "user" SET role = 1` no banco).
