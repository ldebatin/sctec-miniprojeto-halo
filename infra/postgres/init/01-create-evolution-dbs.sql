-- Criado automaticamente pelo postgres na primeira subida do volume halo-pgdata.
-- Bancos extras para o Evolution Go (auth + users). O banco principal "halo"
-- já é criado via POSTGRES_DB no docker-compose.

CREATE DATABASE evogo_auth;
CREATE DATABASE evogo_users;
