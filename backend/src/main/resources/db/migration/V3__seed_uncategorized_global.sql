-- =============================================================================
-- V3__seed_uncategorized_global.sql
--
-- Seed mínimo do categories_global usado como fallback pela T-014 quando o
-- category_hint do parser não bate com nenhuma categoria do usuário nem com
-- as globais reais. O seed completo das categorias (Alimentação, Mercado,
-- Transporte, etc., RF-06) entra na T-033 — esta migration só garante que
-- a linha "Sem categoria" exista para o ExpenseService cair de pé.
--
-- O UUID é fixo e gerado a partir de namespace v5 para ser previsível em
-- tests e fixtures (sem depender de gen_random_uuid()).
-- =============================================================================

INSERT INTO categories_global (id, name, icon, color, keywords)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Sem categoria',
    'question-circle',
    '#9CA3AF',
    ARRAY[]::TEXT[]
)
ON CONFLICT (name) DO NOTHING;
