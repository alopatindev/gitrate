SELECT
  warnings.warning,
  languages.language,
  grade_categories.category AS gradeCategory
FROM warnings
INNER JOIN grade_categories ON grade_categories.id = warnings.grade_category_id
INNER JOIN languages ON languages.id = warnings.language_id
