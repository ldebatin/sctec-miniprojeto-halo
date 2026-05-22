-- =============================================================================
-- V5__refine_global_categories.sql
--
-- Refinamento das categorias globais semeadas em V3 (Sem categoria) e V4
-- (12 globais): popula `keywords` com sinônimos pt-BR e ajusta `icon` para
-- nomes válidos da biblioteca `lucide-react` usada pelo frontend.
--
-- Não inserta nada — só faz UPDATE. As linhas já existem garantidas pelas
-- V3/V4. As keywords vão ser consumidas pelo classificador (T-013/T-043);
-- até lá ficam como metadado.
--
-- T-033 — RF-06.
-- =============================================================================

-- Sem categoria (V3): manter colação na lista única em V4 e padronizar
-- ícone com o pacote lucide-react (question-circle → circle-help).
UPDATE categories_global SET icon = 'circle-help'
 WHERE name = 'Sem categoria';

-- Demais 12 globais (V4): refinar icons (lucide-react) + keywords pt-BR.
UPDATE categories_global SET icon = 'utensils', keywords = ARRAY[
    'restaurante', 'lanche', 'comida', 'almoço', 'jantar',
    'pizza', 'hamburguer', 'delivery', 'ifood', 'rappi'
] WHERE name = 'Alimentação';

UPDATE categories_global SET icon = 'shopping-cart', keywords = ARRAY[
    'mercado', 'supermercado', 'atacadão', 'feira',
    'hortifruti', 'sacolão', 'extra', 'carrefour', 'assaí'
] WHERE name = 'Mercado';

UPDATE categories_global SET icon = 'car', keywords = ARRAY[
    'uber', '99', 'táxi', 'gasolina', 'combustível',
    'metrô', 'ônibus', 'passagem', 'pedágio', 'estacionamento'
] WHERE name = 'Transporte';

UPDATE categories_global SET icon = 'sparkles', keywords = ARRAY[
    'cinema', 'show', 'bar', 'balada', 'parque',
    'viagem', 'netflix', 'spotify', 'jogo', 'streaming'
] WHERE name = 'Lazer';

UPDATE categories_global SET icon = 'heart', keywords = ARRAY[
    'farmácia', 'remédio', 'consulta', 'médico', 'dentista',
    'plano de saúde', 'exame', 'drogasil', 'pague menos'
] WHERE name = 'Saúde';

UPDATE categories_global SET icon = 'house', keywords = ARRAY[
    'aluguel', 'condomínio', 'iptu', 'luz', 'água',
    'gás', 'internet', 'telefone', 'manutenção'
] WHERE name = 'Moradia';

UPDATE categories_global SET icon = 'graduation-cap', keywords = ARRAY[
    'curso', 'faculdade', 'mensalidade', 'livro',
    'apostila', 'escola', 'aula', 'graduação'
] WHERE name = 'Educação';

UPDATE categories_global SET icon = 'shirt', keywords = ARRAY[
    'roupa', 'sapato', 'tênis', 'camisa', 'calça',
    'loja', 'renner', 'zara', 'c&a'
] WHERE name = 'Vestuário';

UPDATE categories_global SET icon = 'wrench', keywords = ARRAY[
    'cabeleireiro', 'barbeiro', 'manicure', 'lavanderia',
    'oficina', 'assinatura', 'limpeza'
] WHERE name = 'Serviços';

UPDATE categories_global SET icon = 'trending-up', keywords = ARRAY[
    'ação', 'fundo', 'tesouro', 'cdb', 'aporte',
    'corretora', 'xp', 'nubank'
] WHERE name = 'Investimento';

UPDATE categories_global SET icon = 'banknote', keywords = ARRAY[
    'salário', 'freela', 'pix recebido', 'venda', 'reembolso'
] WHERE name = 'Renda';

-- "Outros" é o catch-all explícito do usuário — sem keywords (qualquer
-- sinônimo aqui colidiria com as outras 11 categorias). Apenas troca o
-- ícone Heroicons (`ellipsis-horizontal`) pelo lucide (`ellipsis`).
UPDATE categories_global SET icon = 'ellipsis'
 WHERE name = 'Outros';
