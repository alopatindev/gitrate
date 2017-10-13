INSERT INTO tags (
  id,
  category_id,
  tag,
  keywords,
  weight,
  clicked
) VALUES (
  DEFAULT,
  (SELECT id FROM tag_categories WHERE category_rest_id = '${tag_category_rest_id}'),
  '${tag}',
  '${keywords}',
  DEFAULT,
  DEFAULT
) ON CONFLICT (category_id, tag) DO NOTHING;

INSERT INTO tags_users_settings (
  id,
  tags_users_id,
  verified
) VALUES (
  DEFAULT,
  (
    SELECT id FROM (
      INSERT INTO tags_users (
        id,
        tag_id,
        user_id
      ) VALUES (
        DEFAULT,
        (
          SELECT id
          FROM tags
          INNER JOIN tag_categories ON tag_categories.id = tags.category_id
          WHERE tag = '${tag}' AND tag_categories.category_rest_id = '${tag_category_rest_id}'
        ),
        (SELECT id FROM users WHERE github_user_id = ${github_user_id})
      ) ON CONFLICT (tag_id, user_id) DO UPDATE
      SET github_user_id = ${github_user_id}
      RETURNING id
    )
  ),
  TRUE
) ON CONFLICT DO UPDATE
SET verified = TRUE
