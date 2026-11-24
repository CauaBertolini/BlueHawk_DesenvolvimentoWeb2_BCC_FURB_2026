# Spec — mudanças na API e no banco

Resumo das mudanças de estrutura feitas para: aprovação de jogos na Wiki, categorias, capas, matchmaking
("Jogue"), vitrine da Loja, curtidas/ranking, estatísticas de partidas da estante, perfil do usuário e detalhes do anúncio (estado e entrega). Cada bloco lista o que mudou no **banco** (migrations Flyway), na **API**
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
| `V20261124100000__Create_Match_Chat_And_Notification.sql` | Tabelas `open_match_message` (chat das partidas) e `notification`, com auditoria completa |
| `V20261124150000__Create_Offer_Negotiation.sql` | Tabelas `offer_negotiation` e `offer_negotiation_message` (negociação dos anúncios), com auditoria completa; `notification.negotiation_id` e os tipos `OFFER_MESSAGE`/`OFFER_ACCEPTED` |
| `V20261124180000__Create_User_Profile.sql` | Tabela `user_profile` (1:1 com `user`): extras do perfil, privacidade, alertas e avatar, com auditoria completa |
| `V20261124200000__Add_Offer_Condition_And_Delivery.sql` | Colunas `offer.condition` (estado de conservação) e `offer.delivery_method` (forma de entrega) |

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

open_match ◄── open_match_message (id, sender_id → user, recipient_id → user NULL = mesa, body,
                                   created_at, created_by, updated_at, updated_by)
user ◄── user_id ── notification (id, type, actor_id, open_match_id, board_game_id, thread_user_id,
                                  negotiation_id → offer_negotiation,
                                  count, read_at, created_at, created_by, updated_at, updated_by)

offer ◄── offer_negotiation (id, buyer_id → user, status, agreed_price,
  │                          created_at, created_by, updated_at, updated_by)  UNIQUE (offer_id, buyer_id)
  │                     ▲
  │                     └── offer_negotiation_message (id, sender_id → user, body, proposed_price,
  │                                                    created_at, created_by, updated_at, updated_by)
  ├── + condition, delivery_method (estado de conservação e forma de entrega, NULL nos anúncios antigos)
  └── userboardgame_id → user_board_game (o dono da cópia é o vendedor)

user ◄── 1:1 (ON DELETE CASCADE) ── user_profile (user_id PK, bio, city, experience_level, favorite_genres,
                                    is_public, show_shelf, match_alerts, offer_alerts,
                                    avatar_preset, avatar_image BYTEA, avatar_content_type, avatar_updated_at,
                                    created_at, created_by, updated_at, updated_by)
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
| `status` | `VARCHAR(20)` | `OPEN` \| `CANCELLED` (motivo em `cancel_reason VARCHAR(300) NULL`, seção 18) |
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
| DELETE | `/open-matches/{id}` | host | `{ reason }` (10 a 300 caracteres, obrigatório) → cancela (`CANCELLED`); seção 18 |
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

Nenhuma tabela nova: usa `offer` (com `condition` e `delivery_method`, seção 14), `user_board_game`,
`board_game` e `user`.

### API

`GET /store/offers` (público)

| Parâmetro | Padrão | Observação |
|---|---|---|
| `sort` | `recent` | `recent` (mais novos) \| `price` (mais baratos; empate pelo mais novo) |
| `q` | | Trecho do nome do jogo |
| `category` | | Nome exato da categoria |
| `limit` | `24` | Máximo `60` |

Resposta: `[{ id, price, description, condition, deliveryMethod, createdAt, sellerId, sellerName, isMine, game { id, name, description, category,
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

## 9. Chat das partidas

Cada partida do Jogue tem a **mesa** (chat do host com os aceitos) e **conversas diretas**, sempre entre o host e
quem pediu para entrar. Não há tempo real: o front atualiza a conversa aberta a cada 5 s.

### Banco — `open_match_message`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | CHAR(36) PK | |
| `open_match_id` | CHAR(36) → `open_match` | ON DELETE CASCADE |
| `sender_id` | CHAR(36) → `user` | ON DELETE CASCADE |
| `recipient_id` | CHAR(36) NULL → `user` | NULL = mesa; preenchido = conversa direta |
| `body` | VARCHAR(1000) | |
| `created_at`, `created_by` | TIMESTAMP, CHAR(36) NULL → `user` | auditoria (`created_by` = remetente) |
| `updated_at`, `updated_by` | TIMESTAMP NULL, CHAR(36) NULL → `user` | auditoria (reservado para edição) |

Índices: `(open_match_id, recipient_id, created_at)` e `(open_match_id, sender_id, recipient_id)`.

### Regras

- **Mesa**: só o host e os aceitos leem e escrevem.
- **Conversa direta**: o host conversa com qualquer pessoa que pediu para entrar. Para escrever, o pedido precisa
  estar pendente ou aceito (recusado ou desistente: só leitura). Jogadores aceitos também conversam entre si
  (seção 16).
- Partida cancelada, ou realizada há mais de 7 dias: só leitura (`409` ao enviar).
- Ler uma conversa marca como lidas as notificações de mensagem dela.

### API

| Método | Rota | Descrição |
|---|---|---|
| GET | `/open-matches/{id}/chats` | `{ canUseTableChat, tableUnread, members[], threads[] }`: membros da mesa e conversas (com não lidas e última mensagem) |
| GET | `/open-matches/{id}/messages?with=&after=` | Mensagens da mesa (sem `with`) ou da conversa com `with`, em ordem cronológica (até 200; `after` = só as novas) |
| POST | `/open-matches/{id}/messages` | `{ body, recipientId }` (`recipientId` nulo = mesa) → `201` com a mensagem |

## 10. Notificações

Sininho da topbar. As notificações nascem dentro dos casos de uso que já existiam, na mesma transação.

### Banco — `notification`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | CHAR(36) PK | |
| `user_id` | CHAR(36) → `user` | destinatário, ON DELETE CASCADE |
| `type` | VARCHAR(40) | CHECK com os tipos abaixo |
| `actor_id` | CHAR(36) NULL → `user` | quem causou (na mensagem agrupada, o último remetente); ON DELETE SET NULL |
| `open_match_id` | CHAR(36) NULL → `open_match` | ON DELETE CASCADE |
| `board_game_id` | CHAR(36) NULL → `board_game` | ON DELETE CASCADE |
| `thread_user_id` | CHAR(36) NULL → `user` | só em `MATCH_MESSAGE`: a outra pessoa da conversa direta (NULL = mesa) |
| `count` | INT | mensagens agrupadas numa notificação não lida |
| `read_at` | TIMESTAMP NULL | |
| `created_at`, `created_by` | TIMESTAMP, CHAR(36) NULL → `user` | auditoria (`created_by` = ator) |
| `updated_at`, `updated_by` | TIMESTAMP NULL, CHAR(36) NULL → `user` | auditoria: última mensagem agrupada |

Índice: `(user_id, read_at, created_at DESC)`.

### Tipos e quando nascem

| Tipo | Para quem | Quando |
|---|---|---|
| `JOIN_REQUEST_RECEIVED` | host | alguém pede para entrar |
| `JOIN_REQUEST_WITHDRAWN` | host | quem pediu desiste |
| `JOIN_REQUEST_ACCEPTED` / `JOIN_REQUEST_REJECTED` | quem pediu | o host decide |
| `MATCH_CANCELLED` | pendentes e aceitos | o host cancela |
| `MATCH_MESSAGE` | mesa (menos quem enviou) ou o destinatário da conversa direta | nova mensagem; enquanto não lida, as seguintes da mesma conversa somam em `count` |
| `BOARDGAME_APPROVED` / `BOARDGAME_REJECTED` | quem cadastrou o jogo | um admin revisa |
| `OFFER_MESSAGE` | a outra parte da negociação | nova mensagem na negociação; agrupada como `MATCH_MESSAGE` (seção 11) |
| `OFFER_ACCEPTED` | o comprador | o vendedor aceita a proposta (seção 11) |
| `DIRECT_MESSAGE` | a outra pessoa da conversa direta | nova mensagem direta; agrupada como `MATCH_MESSAGE` (seção 17) |

Ninguém é notificado do próprio ato. O texto é montado no front a partir do tipo e dos vínculos.

### API

| Método | Rota | Descrição |
|---|---|---|
| GET | `/notifications?page=&size=` | Da atividade mais recente para a mais antiga (`size` até 50) |
| GET | `/notifications/unread-count` | `{ count }`: notificações não lidas (linhas, não a soma de `count`) |
| POST | `/notifications/{id}/read` | Marca uma como lida (`404` se não for do usuário) |
| POST | `/notifications/read-all` | Marca todas → `{ count }` |

Cada notificação traz `{ id, type, read, createdAt, count, actor, match, boardgame, threadUserId, negotiation,
directConversationId }`.
`match` = `{ id, gameName, scheduledAt, cancelReason }` (`cancelReason` só na partida cancelada; seção 18).
`negotiation` = `{ id, gameName, offerId }` em `OFFER_MESSAGE`/`OFFER_ACCEPTED` (null nos demais tipos).
`directConversationId` só em `DIRECT_MESSAGE` (seção 17).

## 11. Negociação dos anúncios (Loja)

Comprador e vendedor (dono da cópia anunciada, `user_board_game.user_id`) conversam sobre um anúncio, com
propostas de preço opcionais. O vendedor aceita uma proposta do comprador e o anúncio é vendido. Sem tempo real
(o front atualiza a conversa aberta periodicamente).

### Banco — `offer_negotiation`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | CHAR(36) PK | |
| `offer_id` | CHAR(36) → `offer` | ON DELETE CASCADE |
| `buyer_id` | CHAR(36) → `user` | ON DELETE CASCADE; índice próprio |
| `status` | VARCHAR(20) | `OPEN` \| `ACCEPTED` \| `CLOSED` (CHECK), padrão `OPEN` |
| `agreed_price` | DECIMAL(10,2) NULL | preço da proposta aceita |
| `created_at`, `created_by` | TIMESTAMP, CHAR(36) NULL → `user` | auditoria (`created_by` = comprador) |
| `updated_at`, `updated_by` | TIMESTAMP NULL, CHAR(36) NULL → `user` | auditoria: aceite/encerramento |
| | `UNIQUE (offer_id, buyer_id)` | uma negociação por comprador por anúncio |

### Banco — `offer_negotiation_message`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | CHAR(36) PK | |
| `negotiation_id` | CHAR(36) → `offer_negotiation` | ON DELETE CASCADE |
| `sender_id` | CHAR(36) → `user` | ON DELETE CASCADE |
| `body` | VARCHAR(1000) | |
| `proposed_price` | DECIMAL(10,2) NULL | proposta de preço (CHECK > 0) |
| `created_at`, `created_by` | TIMESTAMP, CHAR(36) NULL → `user` | auditoria (`created_by` = remetente) |
| `updated_at`, `updated_by` | TIMESTAMP NULL, CHAR(36) NULL → `user` | auditoria (reservado para edição) |

Índice: `(negotiation_id, created_at)`.

`notification` ganhou `negotiation_id CHAR(36) NULL → offer_negotiation` (ON DELETE CASCADE, indexada) e o
CHECK de `type` passou a aceitar `OFFER_MESSAGE` e `OFFER_ACCEPTED`.

### Regras

- Ninguém negocia o próprio anúncio (`403`). Começar exige anúncio `ACTIVE` (`409`).
- Uma negociação por comprador por anúncio: começar de novo reaproveita a mesma (`409` se já `ACCEPTED`/`CLOSED`).
- Só comprador e vendedor leem e escrevem (`403`). Ler é permitido em qualquer status.
- Escrever: negociação `OPEN` ou `ACCEPTED` (`409` se `CLOSED`). Depois do aceite (`ACCEPTED`) as mensagens servem
  para combinar a entrega: proposta de preço nesse status → `409`.
- `body`: 1 a 1000 caracteres depois do trim (`400`). `proposedPrice` opcional: > 0 e ≤ 99999999.99 (`400`;
  arredondado para centavos).
- Aceitar: só o vendedor (`403`); a mensagem precisa ser da negociação (`404`), ter `proposedPrice` e ter sido
  enviada pelo comprador; negociação `OPEN` e anúncio `ACTIVE` (`409` nos demais casos). Efeito, na mesma
  transação: negociação → `ACCEPTED` com `agreed_price`; anúncio → `SOLD`; as outras negociações `OPEN` do anúncio
  → `CLOSED`; o comprador recebe `OFFER_ACCEPTED`.
- Cada mensagem notifica a outra parte com `OFFER_MESSAGE` (ator = remetente), agrupada enquanto não lida como em
  `MATCH_MESSAGE`. Ler as mensagens marca como lidas as `OFFER_MESSAGE` da negociação de quem está lendo.

### API

Negociação: `{ id, status, agreedPrice, isSeller, offer { id, price, status, game { id, name, coverPreset,
coverImageUrl } }, buyer { id, name }, seller { id, name } }`.
Mensagem: `{ id, senderId, senderName, body, proposedPrice, createdAt, mine }`.

| Método | Rota | Descrição |
|---|---|---|
| POST | `/offers/{offerId}/negotiations` | `{ body, proposedPrice? }` → `201` com a negociação (cria ou reaproveita a do comprador e envia a mensagem) |
| GET | `/negotiations/{id}` | A negociação |
| GET | `/negotiations/{id}/messages?after=` | Em ordem cronológica (até 200; `after` = só as estritamente posteriores) |
| POST | `/negotiations/{id}/messages` | `{ body, proposedPrice? }` → `201` com a mensagem |
| POST | `/negotiations/{id}/accept` | `{ messageId }` → a negociação aceita |

## 12. Caixa de conversas

Junta numa lista só os chats das partidas do Jogue e as negociações da Loja.

### Banco

Nenhuma mudança além da seção 11: usa `open_match*`, `offer_negotiation*` e `notification`.

### API

| Método | Rota | Descrição |
|---|---|---|
| GET | `/chats` | Conversas de quem está logado, da atividade mais recente para a mais antiga (até 60) |
| GET | `/chats/unread-count` | `{ count }`: notificações `MATCH_MESSAGE` + `OFFER_MESSAGE` + `DIRECT_MESSAGE` não lidas (linhas) |

Item: `{ key, kind, title, subtitle, matchId, withUserId, negotiationId, directConversationId, status, unread,
lastActivityAt, lastMessage { body, createdAt, senderName, mine } | null }`. Os itens `DIRECT` (mensagens diretas
entre usuários) estão na seção 17.

| `kind` | `key` | `subtitle` | Quando entra |
|---|---|---|---|
| `MATCH_TABLE` | `match:{matchId}:table` | `Mesa · {cidade}` | host ou pedido aceito |
| `MATCH_DIRECT` | `match:{matchId}:{outroUsuário}` | nome da outra pessoa | host: uma por pessoa que pediu e já tem mensagem; quem pediu: a conversa com o host (mesmo vazia se o pedido está pendente ou aceito) e as conversas com outros jogadores que já têm mensagem (seção 16) |
| `DIRECT` | `dm:{directConversationId}` | `Mensagem direta` | participante; vazia, só para quem a abriu (seção 17) |
| `NEGOTIATION` | `neg:{negotiationId}` | `Comprando de {vendedor}` / `Vendendo para {comprador}` | comprador ou vendedor |

- Partidas: as que o usuário hospeda ou em que tem pedido (qualquer status), até 30 dias depois da data marcada.
- `title` = nome do jogo. `status` = status da partida (`OPEN`/`CANCELLED`) ou da negociação.
- `withUserId` = a outra pessoa da conversa (abre o perfil público): em `MATCH_DIRECT`, o outro participante; em
  `NEGOTIATION`, o comprador para quem vende e o vendedor para quem compra; `null` em `MATCH_TABLE`.
- `lastActivityAt` = última mensagem; sem mensagens, a criação da negociação / do pedido / da partida.
- `unread` = soma de `count` das notificações não lidas da conversa (`MATCH_MESSAGE` da partida com o mesmo
  `thread_user_id` — nulo na mesa — ou `OFFER_MESSAGE` da negociação).

## 13. Perfil do usuário e perfil público

Extras do perfil (bio, cidade, nível, gêneros), preferências (perfil público, estante visível, alertas) e o avatar
saíram do `localStorage` do navegador e passaram para a API. Outros usuários abrem o perfil público a partir dos
anúncios da Loja (`sellerId`), dos chats das partidas e da caixa de conversas (`withUserId`).

### Banco — `user_profile` (1:1 com `user`)

| Coluna | Tipo | Observação |
|---|---|---|
| `user_id` | CHAR(36) PK → `user` | ON DELETE CASCADE |
| `bio` | VARCHAR(500) NULL | |
| `city` | VARCHAR(100) NULL | |
| `experience_level` | VARCHAR(20) NULL | `INICIANTE` \| `INTERMEDIARIO` \| `VETERANO` (CHECK) |
| `favorite_genres` | VARCHAR(200) NULL | texto livre |
| `is_public` | BOOLEAN NOT NULL DEFAULT TRUE | outros veem bio/estatísticas/estante |
| `show_shelf` | BOOLEAN NOT NULL DEFAULT TRUE | estante visível no perfil público |
| `match_alerts` | BOOLEAN NOT NULL DEFAULT TRUE | preferência de alerta (partidas) |
| `offer_alerts` | BOOLEAN NOT NULL DEFAULT FALSE | preferência de alerta (anúncios) |
| `allow_direct_messages` | BOOLEAN NOT NULL DEFAULT TRUE | aceita mensagens diretas (seção 17) |
| `avatar_preset` | VARCHAR(40) NULL | arte do site escolhida (id livre `^[a-z0-9-]+$`) |
| `avatar_image` | BYTEA NULL | foto enviada (não é carregada ao ler o perfil) |
| `avatar_content_type` | VARCHAR(50) NULL | `image/png` \| `image/jpeg` \| `image/webp` |
| `avatar_updated_at` | TIMESTAMP NULL | quando a foto foi enviada (`NULL` = sem foto); versiona a URL |
| `created_at`, `created_by` | TIMESTAMP, CHAR(36) NULL → `user` | auditoria (ON DELETE SET NULL) |
| `updated_at`, `updated_by` | TIMESTAMP NULL, CHAR(36) NULL → `user` | auditoria (ON DELETE SET NULL) |

Usuário sem linha = padrões (público, estante visível, `match_alerts` ligado, sem extras e sem avatar). A linha só
é criada na primeira gravação (PUT do perfil ou envio da foto).

### Regras

- Textos com trim; vazio → `null`; acima do tamanho da coluna → `400`. `experienceLevel` fora dos 3 valores → `400`.
- `avatarPreset`: até 40 caracteres, `^[a-z0-9-]+$` (`400`). Escolher uma arte apaga a foto enviada; enviar uma
  foto apaga a arte. `DELETE /users/me/avatar` apaga as duas.
- Foto: `multipart/form-data`, campo `file`; JPEG/PNG/WebP **detectado pelos bytes** (`415`), até 3 MB (`413`),
  vazio → `400` (mesmo padrão da capa). O limite de multipart do Spring subiu para 3 MB (arquivo) / 4 MB
  (requisição); a capa continua limitada a 2 MB no caso de uso.
- Perfil público: exige login (`401`); usuário inexistente → `404`. O dono sempre vê tudo. Para os outros:
  perfil privado → só `id`, `name`, `role`, avatar e `isPublic: false` (`profile`, `stats` e `shelf` nulos);
  perfil público → `profile` e `stats`, e `shelf` só com `show_shelf` ligado.
- `memberSince` = `user.created_at`. `stats.games` = cópias da estante (jogos aprovados e os cadastrados pelo
  próprio usuário, como a estante mostra); `stats.forSale` = anúncios `ACTIVE`; `stats.matchesPlayed` = partidas
  do Jogue já realizadas (mesma regra da seção 8, somando todos os jogos).

### API

Perfil próprio: `{ bio, city, experienceLevel, favoriteGenres, isPublic, showShelf, matchAlerts, offerAlerts,
allowDirectMessages, avatarPreset, avatarUrl }` (`avatarUrl` = `/users/{id}/avatar?v={epochMilli}` com foto, senão `null`).

| Método | Rota | Quem | Descrição |
|---|---|---|---|
| GET | `/users/me/profile` | logado | O perfil (padrões sem linha) |
| PUT | `/users/me/profile` | logado | Mesmos campos, menos `avatarUrl`. Textos `null` limpam; booleanos `null` mantêm o valor atual |
| POST | `/users/me/avatar` | logado | Foto (`file`) → o perfil |
| DELETE | `/users/me/avatar` | logado | Remove foto e arte → o perfil |
| GET | `/users/{id}/avatar` | **público** | Bytes da foto com `Cache-Control: public, max-age=1 ano` e `nosniff`; `404` sem foto |
| GET | `/users/{id}/public-profile` | logado | Perfil público (abaixo) |

```json
{ "id", "name", "role": "ADMIN|USER", "avatarPreset": null, "avatarUrl": null, "isPublic": true, "isMe": false,
  "profile": null | { "bio", "city", "experienceLevel", "favoriteGenres", "memberSince" },
  "stats": null | { "games": 7, "forSale": 2, "matchesPlayed": 3 },
  "shelf": null | [{ "boardgameId", "name", "category", "coverPreset", "coverImageUrl", "offerId", "price" }],
  "acceptsDirectMessages": true }
```

`acceptsDirectMessages` vem sempre (mesmo no perfil privado); `true` para quem não tem linha em `user_profile`.

Cada item da `shelf` traz o anúncio `ACTIVE` da cópia (`offerId`/`price`), ou `null`.

Mudanças relacionadas: `GET /store/offers` ganhou `sellerId` (seção 6) e, em `GET /chats`, os itens
`NEGOTIATION` passaram a trazer `withUserId` (seção 12).

## 14. Detalhes do anúncio: estado de conservação e entrega

Ao anunciar um jogo, o vendedor informa o estado de conservação da cópia, como ela será entregue (uma forma só) e
uma descrição (agora obrigatória na criação). Anúncios antigos continuam válidos: sem estado e sem forma de entrega.

### Banco

Duas colunas novas em `offer`, ambas `NULL` nos anúncios antigos:

| Coluna | Tipo | Observação |
|---|---|---|
| `condition` | `VARCHAR(30) NULL` | `NOVO_LACRADO` \| `COMO_NOVO` \| `MUITO_BOM` \| `BOM` \| `COM_MARCAS_DE_USO` (CHECK) |
| `delivery_method` | `VARCHAR(20) NULL` | `A_COMBINAR` (à combinar) \| `RETIRADA` (o comprador cuida da coleta) \| `ENVIO` (o vendedor envia até o comprador) (CHECK) |

No backend são enums do domínio (`domain/entity/offer/OfferCondition` e `domain/entity/offer/DeliveryMethod`),
mapeados com `@Enumerated(EnumType.STRING)`: o banco guarda o nome do enum.

### Regras

- Criar (`POST /offers`):
  - `condition` obrigatório, um dos 5 valores, sem diferenciar maiúsculas (gravado em maiúsculas) → senão `400`.
  - `deliveryMethod` obrigatório, um dos 3 valores, sem diferenciar maiúsculas (gravado em maiúsculas) → senão `400`.
  - `description` obrigatória: de 10 a 500 caracteres depois do trim (é gravada aparada) → senão `400`.
  - O resto continua igual (`userBoardgameId`, jogo aprovado, `price`, `status`).
- Editar (`PUT` e `PATCH /offers/{id}`): `condition` e `deliveryMethod` são opcionais. Quando vêm, seguem as
  regras da criação (`400`) e substituem os atuais; ausentes ou `null`, mantêm os atuais. A descrição **não** passa
  a ser obrigatória na edição (anúncios antigos).
- Nas respostas, os dois vêm pelo nome do enum, ou `null` nos anúncios antigos.

### API

| Método | Rota | Corpo |
|---|---|---|
| POST | `/offers` | `{ userBoardgameId, price, status?, description, condition, deliveryMethod }` → `201` |
| PUT | `/offers/{id}` | `{ userBoardgameId, price, status, description?, condition?, deliveryMethod? }` |
| PATCH | `/offers/{id}` | `{ price?, status?, description?, condition?, deliveryMethod? }` |

Toda resposta de anúncio em `/offers` (criar, ler, listar, PUT, PATCH) é
`{ id, userBoardgameId, price, status, description, condition, deliveryMethod, createdAt, updatedAt }`.
`GET /store/offers` e `GET /store/offers/{id}` ganharam `condition` e `deliveryMethod` logo depois de
`description` (seção 6).

## 15. Segurança (rotas públicas)

Liberadas sem token (`SecurityConfig`), todas `GET`:
`/boardgames`, `/categories`, `/boardgames/*/cover`, `/users/*/avatar`, `/open-matches`, `/open-matches/*`,
`/store/offers`.
Todas as demais continuam exigindo token; as permissões finas (autor, host, ADMIN) ficam nos casos de uso.

Como o usuário chega aos casos de uso:

- `CurrentUser` (`application/dto/auth`): o usuário da requisição atual, `{ userId, admin }`.
  Visitantes recebem `CurrentUser.anonymous()` (`userId` nulo).
- Os controllers declaram um parâmetro `CurrentUser currentUser`, preenchido pelo
  `CurrentUserArgumentResolver` (registrado em `CurrentUserWebConfig`) a partir do token. Assim fica visível na
  assinatura quais rotas dependem de quem está logado, e a camada de aplicação não conhece o Spring Security.
- `Roles.ADMIN_ONLY` é a expressão de `@PreAuthorize` para rotas só de ADMIN.

`/open-matches/stats/played`, `/open-matches/{id}/chats` e `/open-matches/{id}/messages` **não** são públicas
(têm dois segmentos depois de `/open-matches`). `/notifications/**`, `/offers/*/negotiations`,
`/negotiations/**`, `/chats/**` e `/direct-conversations/**` exigem token. Do perfil, só a foto
(`/users/*/avatar`) é pública: `/users/me/**` e `/users/*/public-profile` exigem token.

## 16. Conversa direta entre jogadores da mesa

Além da conversa host ↔ quem pediu (seção 9), dois jogadores **aceitos** na mesma partida podem conversar em
particular. Usa a mesma tabela `open_match_message` (`recipient_id` = o outro jogador); **sem mudança no banco**.

### Regras

- Escrever exige os dois com pedido `ACCEPTED` (`403` caso contrário: pendente ↔ jogador, recusado, desistente,
  quem não tem pedido na partida). Partida cancelada ou realizada há mais de 7 dias: `409`, como na mesa.
- Ler: os dois aceitos, **ou** já existe histórico entre eles (um saiu da mesa): o histórico segue legível. Sem
  histórico e sem os dois aceitos → `403`.
- Notificação: `MATCH_MESSAGE` com `thread_user_id` = remetente, igual à conversa com o host (agrupada enquanto não
  lida; ler a conversa marca como lida).

### API (mesmas rotas da seção 9)

- `POST /open-matches/{id}/messages` com `{ body, recipientId: <outro jogador> }` → `201`.
- `GET /open-matches/{id}/messages?with=<outro jogador>&after=` → mensagens.
- `GET /open-matches/{id}/chats` para um jogador: `threads` = a do host (`requestStatus: "HOST"`) + uma por outro
  jogador aceito (`requestStatus: "ACCEPTED"`) com `unread` e `lastMessage`; jogadores que saíram da mesa só
  aparecem se houver histórico (com o status atual do pedido). Ordem: última mensagem, da mais recente para a mais
  antiga; as vazias no fim, com o host na frente. A visão do host não mudou.
- `GET /chats`: as conversas entre jogadores entram como `MATCH_DIRECT` quando já têm mensagem.

## 17. Mensagens diretas entre usuários

Conversa particular entre dois usuários do site, sem vínculo com partida ou anúncio (ex.: a partir do perfil
público). Uma conversa por par. Sem tempo real (o front atualiza a conversa aberta periodicamente).

### Banco — `direct_conversation`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | CHAR(36) PK | |
| `user_a_id` | CHAR(36) → `user` | ON DELETE CASCADE; o menor id do par |
| `user_b_id` | CHAR(36) → `user` | ON DELETE CASCADE; índice próprio |
| `last_message_at` | TIMESTAMP NULL | última mensagem (`NULL` = vazia) |
| `created_at`, `created_by` | TIMESTAMP, CHAR(36) NULL → `user` | auditoria (`created_by` = quem abriu); ON DELETE SET NULL |
| `updated_at`, `updated_by` | TIMESTAMP NULL, CHAR(36) NULL → `user` | auditoria: última mensagem; ON DELETE SET NULL |
| | `UNIQUE (user_a_id, user_b_id)`, `CHECK (user_a_id <> user_b_id)`, `CHECK (user_a_id < user_b_id)` | par gravado em ordem |

### Banco — `direct_message`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | CHAR(36) PK | |
| `conversation_id` | CHAR(36) → `direct_conversation` | ON DELETE CASCADE |
| `sender_id` | CHAR(36) → `user` | ON DELETE CASCADE |
| `body` | VARCHAR(1000) | |
| `created_at`, `created_by` | TIMESTAMP, CHAR(36) NULL → `user` | auditoria (`created_by` = remetente) |
| `updated_at`, `updated_by` | TIMESTAMP NULL, CHAR(36) NULL → `user` | auditoria (reservado para edição) |

Índice: `(conversation_id, created_at)`.

Também: `user_profile.allow_direct_messages BOOLEAN NOT NULL DEFAULT TRUE`; `notification` ganhou
`direct_conversation_id CHAR(36) NULL → direct_conversation` (ON DELETE CASCADE, indexada) e o CHECK de `type`
passou a aceitar `DIRECT_MESSAGE`. Migration `V20261124220000__Create_Direct_Conversation.sql`.

### Regras

- Abrir (get-or-create): `userId` inválido ou o próprio usuário → `400`; usuário inexistente → `404`; se a conversa
  já existe, devolve a mesma (`200`, em qualquer ordem do par); senão, se a outra pessoa desligou
  `allowDirectMessages` → `403`; senão cria (`201`).
- Só os dois participantes leem e escrevem (`403`); conversa inexistente → `404`.
- Enviar: `body` 1 a 1000 caracteres depois do trim (`400`); `403` se a outra pessoa não aceita mensagens diretas
  (o histórico continua legível; `canSend: false`).
- Cada mensagem cria/agrupa uma `DIRECT_MESSAGE` não lida para a outra pessoa (ator = remetente), como em
  `MATCH_MESSAGE`. Ler as mensagens marca como lidas as `DIRECT_MESSAGE` da conversa de quem está lendo.

### API

Conversa: `{ id, otherUser { id, name }, canSend, lastMessage { body, senderId, createdAt } | null, unread,
createdAt, lastMessageAt }`.
Mensagem: `{ id, senderId, senderName, body, createdAt, mine }`.

| Método | Rota | Descrição |
|---|---|---|
| POST | `/direct-conversations` | `{ userId }` → `200` (já existia) ou `201` (criada) com a conversa |
| GET | `/direct-conversations/{id}` | A conversa |
| GET | `/direct-conversations/{id}/messages?after=` | Em ordem cronológica (até 200; `after` = só as estritamente posteriores) |
| POST | `/direct-conversations/{id}/messages` | `{ body }` → `201` com a mensagem |

Caixa de conversas (`GET /chats`): item `kind: "DIRECT"`, `key: "dm:{id}"`, `title` = nome da outra pessoa,
`subtitle: "Mensagem direta"`, `withUserId` = a outra pessoa, `directConversationId`, `matchId`/`negotiationId`
nulos, `status` = `OPEN` (dá para escrever) ou `CLOSED` (a outra pessoa não aceita), `unread` = soma de `count` das
`DIRECT_MESSAGE` não lidas da conversa, `lastActivityAt` = última mensagem ou a criação. Conversa vazia só aparece
para quem a abriu. `GET /chats/unread-count` conta as `DIRECT_MESSAGE`.

Perfil: `allowDirectMessages` em `GET/PUT /users/me/profile` (`null` no PUT mantém) e `acceptsDirectMessages` em
`GET /users/{id}/public-profile` (seção 13).

## 18. Motivo do cancelamento da partida

O host informa por que cancelou; o motivo aparece para os jogadores.

### Banco

`open_match.cancel_reason VARCHAR(300) NULL` (migration `V20261124230000__Add_Open_Match_Cancel_Reason.sql`).
Partidas abertas e as canceladas antes da mudança ficam com `NULL`.

### Regras

- `DELETE /open-matches/{id}` exige o corpo `{ "reason": "..." }`: com trim, de 10 a 300 caracteres (`400`; corpo
  ausente também é `400`). Continua só do host (`403`) e notificando `MATCH_CANCELLED` aos pedidos pendentes e
  aceitos.
- Cancelar de novo uma partida já cancelada devolve a partida sem trocar o motivo e sem notificar outra vez (o
  motivo ainda é validado).

### API

- Partida (`OpenMatchResponse`, em todas as rotas de `/open-matches`): novo campo `cancelReason` (`null` se não
  cancelada).
- Notificações: `match` = `{ id, gameName, scheduledAt, cancelReason }` (`cancelReason` preenchido quando a partida
  está cancelada — útil em `MATCH_CANCELLED`).

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
