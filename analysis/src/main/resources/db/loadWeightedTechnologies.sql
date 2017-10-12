SELECT tag
FROM tags
INNER JOIN tag_categories ON tag_categories.id = tags.category_id
WHERE
  tag_categories.category_rest_id = 'technologies'
  AND tags.weight > 0
