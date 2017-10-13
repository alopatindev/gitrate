INSERT INTO developers (
  id,
  user_id,
  show_email,
  job_seeker,
  available_for_relocation,
  programming_experience_months,
  work_experience_months,
  description
) VALUES (
  DEFAULT,
  (
    SELECT id FROM (
      INSERT INTO users (
        id,
        github_user_id,
        github_login,
        full_name,
        developer,
        updated_by_user,
        viewed
      ) VALUES (
        DEFAULT,
        ${github_user_id},
        '${github_login}',
        '${full_name}',
        TRUE,
        DEFAULT,
        DEFAULT
      ) ON CONFLICT (github_user_id) DO UPDATE
      SET
        github_login = '${github_login}',
        full_name = '${full_name}',
        developer = TRUE
      RETURNING id
    )
  ),
  DEFAULT,
  ${job_seeker},
  DEFAULT,
  DEFAULT,
  DEFAULT,
  '${description}'
) ON CONFLICT (user_id) DO NOTHING
