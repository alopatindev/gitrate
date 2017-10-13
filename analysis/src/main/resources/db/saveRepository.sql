INSERT INTO repositories (
  id,
  raw_id,
  user_id,
  name,
  lines_of_code,
  updated_by_analyzer
) VALUES (
  DEFAULT,
  '${raw_repository_id}',
  (SELECT id FROM users WHERE github_user_id = ${github_user_id}),
  '${repository_name}',
  ${lines_of_code},
  DEFAULT
) ON CONFLICT (raw_id) DO UPDATE
SET
  updated_by_analyzer = DEFAULT,
  lines_of_code = ${lines_of_code}
