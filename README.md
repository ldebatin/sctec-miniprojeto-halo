# Halo

Halo é um controle de gastos pessoais que usa o **WhatsApp** como interface primária de registro e uma **web mobile-first** para consulta e edição. O usuário escreve algo como `Mercado 87,30` no WhatsApp e a IA categoriza e arquiva o lançamento automaticamente; quando quiser entender seus gastos, consulta um dashboard web ou pede um resumo direto no chat.

Este repositório é um monorepo organizado em quatro pastas: `backend/` (API Spring Boot + Java 21), `frontend/` (React + Vite + Tailwind), `infra/` (Docker Compose para Postgres e Evolution GO) e `docs/` (especificação do produto e decisões técnicas). Projeto acadêmico — mini-projeto SENAI.

## Documentação

- [PRD — o quê e por quê](docs/prd.md)
- [Análise técnica — como](docs/analise-tecnica.md)
- [Ideia inicial](docs/ideia-inicial.md)
- [Backlog de tasks](docs/tasks.md)

## Setup (resumo)

Cada subprojeto tem seu próprio README com instruções específicas. Em alto nível:

```bash
# 1. Subir dependências (Postgres + Evolution GO)
cd infra && docker compose up -d

# 2. Backend (Spring Boot)
cd backend && mvn spring-boot:run

# 3. Frontend (Vite)
cd frontend && npm install && npm run dev
```

Variáveis de ambiente são documentadas em `.env.example` dentro de cada subprojeto.
