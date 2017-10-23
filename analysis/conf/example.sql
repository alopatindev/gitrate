INSERT INTO users (
  id,
  github_user_id,
  github_login,
  full_name,
  updated_by_user,
  viewed
) VALUES (DEFAULT, 1, 'usertest', 'full name', DEFAULT, DEFAULT);

INSERT INTO repositories (
  id,
  raw_id,
  user_id,
  name,
  lines_of_code,
  updated_by_analyzer
) VALUES (
  DEFAULT,
  'asdf',
  (SELECT id FROM users WHERE github_user_id = 1),
  'test_repo',
  1000,
  DEFAULT
);

INSERT INTO repositories (
  id,
  raw_id,
  user_id,
  name,
  lines_of_code,
  updated_by_analyzer
) VALUES (
  DEFAULT,
  'asdf',
  (SELECT id FROM users WHERE github_user_id = 1),
  'test_repo',
  1000,
  DEFAULT
) ON CONFLICT (raw_id) DO UPDATE
SET
  updated_by_analyzer = DEFAULT,
  lines_of_code = 1000;

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
  (SELECT id FROM users WHERE github_user_id = 1),
  DEFAULT,
  TRUE,
  DEFAULT,
  DEFAULT,
  DEFAULT,
  DEFAULT
);

INSERT INTO contacts (
  id,
  category_id,
  contact,
  user_id
) VALUES (
  DEFAULT,
  (SELECT id FROM contact_categories WHERE category = 'Email'),
  'mail@domain.com',
  (SELECT id FROM users WHERE github_user_id = 1)
);

INSERT INTO technologies (
  id,
  language_id,
  technology
) VALUES (
  DEFAULT,
  (SELECT id FROM languages WHERE language = 'JavaScript'),
  'eslint'
);

INSERT INTO technologies_users (
  id,
  technology_id,
  user_id
) VALUES (
  DEFAULT,
  (
    SELECT technologies.id
    FROM technologies
    INNER JOIN languages ON languages.id = technologies.language_id
    WHERE technologies.technology = 'eslint' AND languages.language = 'JavaScript'
  ),
  (SELECT id FROM users WHERE github_user_id = 1)
);

INSERT INTO technologies_users_settings (
  id,
  technologies_users_id,
  verified
) VALUES (
  DEFAULT,
  1,
  TRUE
);

INSERT INTO grades (
  id,
  category_id,
  value,
  repository_id
) VALUES (
  DEFAULT,
  (SELECT id FROM grade_categories WHERE category = 'Maintainable'),
  0.8,
  (SELECT id FROM repositories WHERE raw_id = 'asdf')
) ON CONFLICT (category_id, repository_id) DO UPDATE
SET value = 0.8;

INSERT INTO grades (
  id,
  category_id,
  value,
  repository_id
) VALUES (
  DEFAULT,
  (SELECT id FROM grade_categories WHERE category = 'Maintainable'),
  0.5,
  (SELECT id FROM repositories WHERE raw_id = 'asdf')
) ON CONFLICT (category_id, repository_id) DO UPDATE
SET value = 0.5;

--SELECT
--  repositories.id,
--  name,
--  updated_by_analyzer,
--  users.github_user_id
--FROM repositories
--INNER JOIN users ON users.id = repositories.user_id;

SELECT * FROM main_page;

-- search page
SELECT
  users.id,
  users.github_login,
  users.full_name,
  developers.available_for_relocation,
  developers.job_seeker,
  ARRAY(
    SELECT technology_details
    FROM users_to_technology_details
    WHERE
      users_to_technology_details.user_id = users.id
      AND users_to_technology_details.technology IN ('eslint')
    LIMIT 5
  ) AS matched_technologies,
  ARRAY(
    SELECT language_details
    FROM users_to_language_details
    WHERE
      users_to_language_details.user_id = users.id
      AND users_to_language_details.language IN ('JavaScript')
    LIMIT 5
  ) AS matched_languages,
  ARRAY(
    SELECT technology_details
    FROM users_to_technology_details
    WHERE users_to_technology_details.user_id = users.id
    LIMIT 5
  ) AS technologies,
  ARRAY(
    SELECT language_details
    FROM users_to_language_details
    WHERE users_to_language_details.user_id = users.id
    LIMIT 5
  ) AS languages,
  ARRAY(
    SELECT grade_details
    FROM users_to_grade_details
    WHERE users_to_grade_details.user_id = users.id
  ) AS grades,
  (
    SELECT AVG(users_to_grades.value)
    FROM users_to_grades
    WHERE users_to_grades.user_id = users.id
  ) AS avg_grade,
  (
    SELECT MAX(repositories.updated_by_analyzer)
    FROM repositories
    WHERE user_id = users.id
  ) AS updated_by_analyzer,
  (
    SELECT SUM(repositories.lines_of_code)
    FROM repositories
    WHERE user_id = users.id
  ) AS total_lines_of_code
FROM users
INNER JOIN developers ON developers.user_id = users.id
ORDER BY
  avg_grade DESC,
  updated_by_analyzer DESC,
  total_lines_of_code DESC -- TODO: filtering instead of ordering?
LIMIT 20
OFFSET 0;

-- TODO: tags autocomplete; use synonyms
--SELECT JSON_BUILD_OBJECT('category', tag_categories.category_rest_id, 'tag', tags.tag) as tags
--FROM tags
--JOIN tag_categories ON tag_categories.id = tags.category_id
--WHERE tags.keywords ILIKE '%js%' -- FIXME: index
--LIMIT 5;

-- user page
SELECT
  users.id,
  users.github_login,
  users.full_name,
  developers.job_seeker,
  developers.available_for_relocation,
  developers.programming_experience_months,
  developers.work_experience_months,
  developers.description,
  ARRAY(
    SELECT JSON_BUILD_OBJECT('category', contact_categories.category, 'contact', contacts.contact)
    FROM contacts
    JOIN contact_categories ON contact_categories.id = contacts.category_id
    WHERE contacts.user_id = users.id
  ) AS contacts,
  GREATEST(users.updated_by_user, (
    SELECT MAX(repositories.updated_by_analyzer)
    FROM repositories
    WHERE user_id = users.id
  )) AS updated,
  ARRAY(
    SELECT grade_details
    FROM users_to_grade_details
    WHERE users_to_grade_details.user_id = users.id
  ) AS grades,
  (
    SELECT AVG(users_to_grades.value)
    FROM users_to_grades
    WHERE users_to_grades.user_id = users.id
  ) AS avg_grade,
  ARRAY(
    SELECT technology_details
    FROM users_to_technology_details
    WHERE users_to_technology_details.user_id = users.id
  ) AS technologies,
  ARRAY(
    SELECT language_details
    FROM users_to_language_details
    WHERE users_to_language_details.user_id = users.id
  ) AS languages
FROM users
INNER JOIN developers ON developers.user_id = users.id
WHERE users.id = 1
LIMIT 1;

-- delete contact
DELETE FROM contacts
WHERE
  contacts.user_id = 1
  AND contacts.category_id = (SELECT id FROM contact_categories WHERE category = 'Email');
