CREATE TABLE users
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         varchar(255) unique not null,
    password_hash varchar(60)         not null,
    created_at    timestamp           not null
);

CREATE TABLE refresh_tokens
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      not null references users (id),
    token_hash varchar(64) not null unique,
    expires_at timestamp   not null,
    revoked    boolean     not null default false,
    created_at timestamp   not null
);
