INSERT INTO grades (
  id,
  category_id,
  value,
  repository_id
) VALUES (
  DEFAULT,
  (SELECT id FROM grade_categories WHERE category = '${grade_category}'),
  ${value},
  (SELECT id FROM repositories WHERE raw_id = '${raw_repository_id}')
) ON CONFLICT (category_id, repository_id) DO UPDATE
SET value = ${value};
