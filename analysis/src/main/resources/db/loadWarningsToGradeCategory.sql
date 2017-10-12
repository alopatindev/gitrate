SELECT
  warnings.warning,
  tags.tag,
  grade_categories.category AS gradeCategory
FROM warnings
INNER JOIN grade_categories ON grade_categories.id = warnings.grade_category_id
INNER JOIN tags ON tags.id = warnings.tag_id
