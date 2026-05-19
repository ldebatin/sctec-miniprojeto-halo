Você é um arquite de software. Analise o arquivo docs/ideia-inicial.md, faça uma análise técnica e detalhada para desenvolvimento do projeto, para os diagramas utilize o padrão mermaid, durante o processo me faça perguntas para melhorar seu contexto ou tirar dúvidas e monte um documento docs/analise-tecnica.md

--
User answered Claude's questions:
  ⎿  · Qual estrutura de monorepo você prefere para o projeto? → Estrutura simples (pastas backend/ e frontend/)
     · Como os gráficos solicitados pelo WhatsApp (resumo mensal) devem ser gerados? → A tabela pode ser em texto formatado, o gráfico pode ser gerado pelo gemini
     · Como as categorias de gastos devem ser gerenciadas? → Categorias globais padrão + categorias customizadas por usuário
     · Qual é o alvo de deploy planejado? → VPS própria com Docker Compose (incluindo Postgres)
--
User answered Claude's questions:
  ⎿  · Como será a infraestrutura do Evolution GO (envio/recebimento de mensagens)? → Uma única instância/número do Halo para todos os usuários (webhook)
     · Qual estratégia de autenticação para a web (após OTP via WhatsApp)? → JWT (access + refresh) com expiração longa (~30 dias)
     · O sistema deve suportar conta compartilhada (ex.: casal/família somando gastos)? → Não — cada telefone é uma conta independente
     · O Gemini deve interpretar também áudios e imagens (foto de cupom fiscal) ou só texto? → Só texto (MVP)
--


Você é um analista de negócios. Analise os arquivos docs/ideia-inicial.md e docs/analise-tecnica.md e crie um PRD para implementação da aplicação, para os diagramas utilize o padrão mermaid, durante o processo me faça perguntas para melhorar seu contexto ou tirar dúvidas e monte um documento docs/prd.md

--
User answered Claude's questions:
  ⎿  · Qual é o contexto/objetivo principal do projeto Halo? → Mini-projeto acadêmico (SENAI)
     · Quem é o usuário-alvo primário (persona) que o PRD deve focar? → Pessoa em início de organização financeira
     · Quais métricas de sucesso o PRD deve estabelecer para o MVP? → Engajamento (gastos registrados por usuário/semana), Qualidade da IA (% mensagens interpretadas corretamente, taxa de 
     correção manual)
     · Como o usuário deve descobrir e iniciar uso do Halo (onboarding)? → Não é prioridade no MVP — definir depois
--

Você é um PO. Com base no docs/PRD.md criar tarefas, com um contexto fechado (tenha uma entrega), para realiza a implementação da aplicação.

--
User answered Claude's questions:
  ⎿  · Como prefere organizar o arquivo de tarefas? → Arquivo único docs/tasks.md agrupado por release
     · Qual granularidade você quer para cada tarefa? → Média: tarefas de 0,5 a 2 dias (~30-50 tarefas total)
     · O que incluir em cada tarefa além de descrição e critérios de aceitação? → Estimativa em dias, Dependências entre tarefas, Referência aos RFs do PRD, Componente/módulo afetado
--

Agora importe as tasks no GitHub Projects, no kanban Projeto Halo