-- =============================================================================
-- V4__seed_global_categories.sql
--
-- Seed mínimo das 12 categorias globais previstas no PRD §RF-06, antecipando
-- a T-033 (que vai refinar ícones, cores e keywords). Inserts idempotentes
-- via ON CONFLICT (name) DO NOTHING — quando T-033 rodar e a linha já existir,
-- vira no-op. Ícones e cores são placeholders genéricos (Heroicons + paleta
-- Tailwind 500) — T-033 polirá.
--
-- Necessário pra T-018 (smoke test E2E): sem esses globais, o resolver da
-- T-014 cairia em "Sem categoria" para todo gasto e o critério de ≥80% de
-- categorização correta seria impossível.
-- =============================================================================

INSERT INTO categories_global (id, name, icon, color, keywords) VALUES
    ('00000000-0000-0000-0000-000000000010', 'Alimentação', 'fork-knife',      '#F59E0B', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-000000000011', 'Mercado',     'shopping-cart',   '#10B981', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-000000000012', 'Transporte',  'car',             '#3B82F6', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-000000000013', 'Lazer',       'sparkles',        '#EC4899', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-000000000014', 'Saúde',       'heart',           '#EF4444', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-000000000015', 'Moradia',     'home',            '#8B5CF6', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-000000000016', 'Educação',    'academic-cap',    '#0EA5E9', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-000000000017', 'Vestuário',   'shirt',           '#F472B6', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-000000000018', 'Serviços',    'wrench',          '#6B7280', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-000000000019', 'Investimento','trending-up',     '#14B8A6', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-00000000001a', 'Renda',       'banknotes',       '#22C55E', ARRAY[]::TEXT[]),
    ('00000000-0000-0000-0000-00000000001b', 'Outros',      'ellipsis-horizontal', '#94A3B8', ARRAY[]::TEXT[])
ON CONFLICT (name) DO NOTHING;
