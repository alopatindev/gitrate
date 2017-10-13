INSERT INTO contacts (
  id,
  category_id,
  contact,
  user_id
) VALUES (
  DEFAULT,
  (SELECT id FROM contact_categories WHERE category = '${contact_category}'),
  '${contact}',
  (SELECT id FROM users WHERE github_user_id = ${github_user_id})
) ON CONFLICT (category_id, contact) DO NOTHING
