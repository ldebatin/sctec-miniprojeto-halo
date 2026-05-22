-- =============================================================================
-- V6__standardize_palette.sql
--
-- Padroniza a paleta de cores das categorias para garantir que o gráfico de
-- pizza do resumo mensal (RF-15, analise-tecnica §9.3) renderize com cores
-- consistentes entre usuários.
--
-- Sintoma observado: usuários diferentes recebiam gráficos com cores
-- divergentes para as mesmas categorias. Causa: `ExpenseService.createUserCopy()`
-- (analise-tecnica §6.1) faz lazy-copy da cor da categoria global no momento
-- da primeira despesa do usuário — cópias antigas mantêm a cor que a global
-- tinha no momento da cópia, e a paleta original do seed V4 era "placeholder"
-- (Tailwind 500 sem mapeamento semântico documentado).
--
-- Esta migration:
-- 1) Atualiza as 13 globais com a paleta canônica (12 + "Sem categoria").
-- 2) Propaga a nova cor para todas as linhas em `categories` que ainda
--    apontam para uma global (lazy-copies), mantendo customizadas livres
--    (linhas com global_id IS NULL ficam intocadas).
--
-- A paleta canônica (20 cores) também é refletida no frontend em
-- `frontend/src/constants/categories.ts` (mesma lista, mesma ordem).
-- =============================================================================

-- 1) Atualizar cores das 13 globais com mapeamento semântico.
UPDATE categories_global SET color = '#F97316' WHERE name = 'Alimentação';   -- Laranja
UPDATE categories_global SET color = '#22C55E' WHERE name = 'Mercado';       -- Verde
UPDATE categories_global SET color = '#3B82F6' WHERE name = 'Transporte';    -- Azul
UPDATE categories_global SET color = '#EC4899' WHERE name = 'Lazer';         -- Rosa
UPDATE categories_global SET color = '#EF4444' WHERE name = 'Saúde';         -- Vermelho
UPDATE categories_global SET color = '#A855F7' WHERE name = 'Moradia';       -- Roxo
UPDATE categories_global SET color = '#06B6D4' WHERE name = 'Educação';      -- Ciano
UPDATE categories_global SET color = '#D946EF' WHERE name = 'Vestuário';     -- Magenta
UPDATE categories_global SET color = '#B5A1E5' WHERE name = 'Serviços';      -- Lavanda
UPDATE categories_global SET color = '#14B8A6' WHERE name = 'Investimento';  -- Verde-azulado
UPDATE categories_global SET color = '#84CC16' WHERE name = 'Renda';         -- Limão
UPDATE categories_global SET color = '#E6D5B8' WHERE name = 'Outros';        -- Bege
UPDATE categories_global SET color = '#6B7280' WHERE name = 'Sem categoria'; -- Cinza

-- 2) Sincronizar cores das cópias por usuário com o pai global. Cobre o
--    bug-fonte (lazy-copies divergentes). Customizadas puras (global_id IS NULL)
--    ficam fora deste UPDATE — usuário continua dono da cor que escolheu.
UPDATE categories c
   SET color = cg.color,
       updated_at = NOW()
  FROM categories_global cg
 WHERE c.global_id = cg.id
   AND c.color IS DISTINCT FROM cg.color;
