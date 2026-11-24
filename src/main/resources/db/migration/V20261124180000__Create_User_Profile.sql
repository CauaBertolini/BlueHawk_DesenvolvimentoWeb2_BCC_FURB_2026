-- Perfil do usuário (1:1 com "user"): extras do perfil, preferências de privacidade/alertas e a foto (avatar).
-- Antes isso ficava só no localStorage do navegador. Usuário sem linha aqui = padrões (perfil público, estante
-- visível, sem extras e sem avatar). Auditoria completa: created_at/created_by e updated_at/updated_by.

CREATE TABLE user_profile (
    user_id             CHAR(36)     NOT NULL,
    bio                 VARCHAR(500) NULL,
    city                VARCHAR(100) NULL,
    experience_level    VARCHAR(20)  NULL,
    -- Texto livre (ex.: "Estratégia, Party")
    favorite_genres     VARCHAR(200) NULL,
    -- Privacidade: perfil público (outros veem bio/estatísticas) e estante visível no perfil
    is_public           BOOLEAN      NOT NULL DEFAULT TRUE,
    show_shelf          BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Alertas: partidas novas perto e anúncios dos jogos curtidos
    match_alerts        BOOLEAN      NOT NULL DEFAULT TRUE,
    offer_alerts        BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Avatar: uma das artes do site (avatar_preset) OU uma foto enviada (avatar_image); nunca os dois
    avatar_preset       VARCHAR(40)  NULL,
    avatar_image        BYTEA        NULL,
    avatar_content_type VARCHAR(50)  NULL,
    avatar_updated_at   TIMESTAMP    NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          CHAR(36)     NULL,
    updated_at          TIMESTAMP    NULL,
    updated_by          CHAR(36)     NULL,
    CONSTRAINT user_profile_pk PRIMARY KEY (user_id),
    CONSTRAINT user_profile_experience_level_chk
        CHECK (experience_level IS NULL OR experience_level IN ('INICIANTE', 'INTERMEDIARIO', 'VETERANO')),
    CONSTRAINT user_profile_user FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT user_profile_created_by FOREIGN KEY (created_by) REFERENCES "user" (id) ON DELETE SET NULL,
    CONSTRAINT user_profile_updated_by FOREIGN KEY (updated_by) REFERENCES "user" (id) ON DELETE SET NULL
);
