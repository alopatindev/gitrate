-- psql --username postgres

-- CREATE ROLE gitrate NOSUPERUSER CREATEDB NOCREATEROLE INHERIT LOGIN;
--DROP DATABASE gitrate;
--CREATE DATABASE gitrate OWNER gitrate;

-- psql --username gitrate --dbname gitrate --file=conf/Schema.sql

-- psql --username postgres --dbname gitrate
-- CREATE EXTENSION pg_trgm;

DROP OWNED BY gitrate;

SET enable_seqscan TO off;

CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  github_user_id INTEGER UNIQUE NOT NULL,
  github_login TEXT NOT NULL,
  full_name TEXT NOT NULL,
  developer BOOLEAN DEFAULT TRUE NOT NULL,
  updated_by_user TIMESTAMP,
  viewed INTEGER DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS developers (
  id SERIAL PRIMARY KEY,
  user_id INTEGER UNIQUE REFERENCES users NOT NULL,
  show_email BOOLEAN,
  job_seeker BOOLEAN NOT NULL,
  available_for_relocation BOOLEAN,
  programming_experience_months SMALLINT,
  work_experience_months SMALLINT,
  description TEXT
);

CREATE TABLE IF NOT EXISTS contact_categories (
  id SERIAL PRIMARY KEY,
  category TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS contacts (
  id SERIAL PRIMARY KEY,
  category_id INTEGER REFERENCES contact_categories NOT NULL,
  contact TEXT,
  user_id INTEGER REFERENCES users NOT NULL
);

CREATE TABLE IF NOT EXISTS tag_categories (
  id SERIAL PRIMARY KEY,
  category_rest_id TEXT UNIQUE NOT NULL, -- languages, ...
  category TEXT UNIQUE NOT NULL -- Programming Language, Technology, Developer Level, Location, Company, Position/Occupation
);

CREATE TABLE IF NOT EXISTS tags (
  id SERIAL PRIMARY KEY,
  category_id INTEGER REFERENCES tag_categories NOT NULL,
  tag TEXT NOT NULL,
  keywords TEXT,
  weight SMALLINT DEFAULT 0 NOT NULL,
  clicked INTEGER DEFAULT 0 NOT NULL,
  UNIQUE (category_id, tag)
);

CREATE INDEX IF NOT EXISTS tags_category_id_idx ON tags (category_id);
-- CREATE INDEX IF NOT EXISTS tags_keywords_idx ON tags USING gin (keywords gin_trgm_ops);

CREATE TABLE IF NOT EXISTS tags_users (
  id SERIAL PRIMARY KEY,
  tag_id INTEGER REFERENCES tags NOT NULL,
  user_id INTEGER REFERENCES users NOT NULL,
  UNIQUE (tag_id, user_id)
);

CREATE TABLE IF NOT EXISTS tags_users_settings (
  id SERIAL PRIMARY KEY,
  tags_users_id INTEGER UNIQUE REFERENCES tags_users NOT NULL,
  verified BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS repositories (
  id SERIAL PRIMARY KEY,
  raw_id TEXT UNIQUE NOT NULL,
  user_id INTEGER REFERENCES users NOT NULL,
  name TEXT NOT NULL,
  updated_by_analyzer TIMESTAMP DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS repositories_user_id_idx ON repositories (user_id);

CREATE TABLE IF NOT EXISTS grade_categories (
  id SERIAL PRIMARY KEY,
  category TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS grades (
  id SERIAL PRIMARY KEY,
  category_id INTEGER REFERENCES grade_categories NOT NULL,
  value FLOAT NOT NULL,
  repository_id INTEGER REFERENCES repositories NOT NULL,
  UNIQUE (category_id, repository_id)
);

--CREATE INDEX IF NOT EXISTS grades_repository_id_idx ON grades (repository_id);
--CREATE INDEX IF NOT EXISTS grades_repository_id_idx ON grades USING gin (repository_id gin_trgm_ops);

-- TODO: make everything nullable (and join non-null stuff)?
CREATE TABLE IF NOT EXISTS github_search_queries (
  id SERIAL PRIMARY KEY,
  language TEXT NOT NULL,
  filename TEXT NOT NULL,
  min_repo_size_kib INT NOT NULL,
  max_repo_size_kib INT NOT NULL,
  min_stars INT NOT NULL,
  max_stars INT NOT NULL,
  pattern TEXT NOT NULL,
  enabled BOOLEAN NOT NULL
);

-- testing data

INSERT INTO github_search_queries (
  id,
  language,
  filename,
  min_repo_size_kib,
  max_repo_size_kib,
  min_stars,
  max_stars,
  pattern,
  enabled
) VALUES (
  DEFAULT,
  'JavaScript',
  '.eslintrc.*',
  10,
  2048,
  0,
  100,
  '',
  TRUE
);

INSERT INTO github_search_queries (
  id,
  language,
  filename,
  min_repo_size_kib,
  max_repo_size_kib,
  min_stars,
  max_stars,
  pattern,
  enabled
) VALUES (
  DEFAULT,
  'JavaScript',
  '.travis.yml',
  10,
  2048,
  0,
  100,
  '',
  TRUE
);

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
  1,
  'usertest',
  'full name',
  DEFAULT,
  DEFAULT,
  DEFAULT
);

INSERT INTO repositories (
  id,
  raw_id,
  user_id,
  name,
  updated_by_analyzer
) VALUES (
  DEFAULT,
  'asdf',
  (SELECT id FROM users WHERE github_user_id = 1),
  'test_repo',
  DEFAULT
);

INSERT INTO repositories (
  id,
  raw_id,
  user_id,
  name,
  updated_by_analyzer
) VALUES (
  DEFAULT,
  'asdf',
  (SELECT id FROM users WHERE github_user_id = 1),
  'test_repo',
  DEFAULT
) ON CONFLICT (raw_id) DO UPDATE
SET updated_by_analyzer = DEFAULT;

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

INSERT INTO contact_categories (
  id,
  category
) VALUES (
  DEFAULT,
  'Email'
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

INSERT INTO tag_categories (
  id,
  category_rest_id,
  category
) VALUES (
  DEFAULT,
  'languages',
  'Programming Language'
);

INSERT INTO tags (
  id,
  category_id,
  tag,
  keywords,
  weight,
  clicked
) VALUES (
  DEFAULT,
  (SELECT id FROM tag_categories WHERE category_rest_id = 'languages'),
  'JavaScript',
  'js;javascript',
  DEFAULT,
  DEFAULT
);

INSERT INTO tags_users (
  id,
  tag_id,
  user_id
) VALUES (
  DEFAULT,
  (
    SELECT tags.id
    FROM tags
    INNER JOIN tag_categories ON tag_categories.id = tags.category_id
    WHERE tags.tag = 'JavaScript'
  ),
  (SELECT id FROM users WHERE github_user_id = 1)
);

INSERT INTO tags_users_settings (
  id,
  tags_users_id,
  verified
) VALUES (
  DEFAULT,
  1, -- TODO: RETURNING
  TRUE
);

INSERT INTO grade_categories (
  id,
  category
) VALUES (
  DEFAULT,
  'Maintainable'
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

CREATE OR REPLACE VIEW users_to_grades AS
  SELECT
    repositories.user_id,
    grades.value
  FROM grades
  INNER JOIN repositories ON repositories.id = grades.repository_id;

CREATE OR REPLACE VIEW users_to_grade_details AS
  SELECT
    repositories.user_id,
    JSON_BUILD_OBJECT('category', grade_categories.category, 'value', grades.value) as grade_details
  FROM grades
  INNER JOIN grade_categories ON grade_categories.id = grades.category_id
  INNER JOIN repositories ON repositories.id = grades.repository_id;

CREATE OR REPLACE VIEW users_to_tag_details AS
  SELECT
    tags_users.user_id,
    tag_categories.category_rest_id AS tag_category,
    tags.tag,
    JSON_BUILD_OBJECT('tag', tags.tag, 'verified', tags_users_settings.verified) as tag_details
  FROM tags
  JOIN tag_categories ON tag_categories.id = tags.category_id
  JOIN tags_users ON tags_users.tag_id = tags.id
  JOIN tags_users_settings ON tags_users_settings.id = tags_users.id
  ORDER BY
    tags_users_settings.verified DESC,
    tags.weight DESC,
    tags.clicked DESC;

CREATE OR REPLACE VIEW main_page AS
  SELECT
    users.id,
    users.github_login,
    users.full_name,
    developers.available_for_relocation,
    developers.job_seeker,
    ARRAY(
      SELECT tag_details
      FROM users_to_tag_details
      WHERE
        users_to_tag_details.tag_category = 'technologies'
        AND users_to_tag_details.user_id = users.id
      LIMIT 5
    ) AS technologies,
    ARRAY(
      SELECT tag_details
      FROM users_to_tag_details
      WHERE
        users_to_tag_details.tag_category = 'languages'
        AND users_to_tag_details.user_id = users.id
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
      WHERE repositories.user_id = users.id
    ) AS updated_by_analyzer
  FROM users
  INNER JOIN developers ON developers.user_id = users.id
  WHERE users.developer = TRUE
  ORDER BY
    avg_grade DESC,
    updated_by_analyzer DESC
  LIMIT 20
  OFFSET 0;

SELECT * FROM main_page;

-- search page
SELECT
  users.id,
  users.github_login,
  users.full_name,
  developers.available_for_relocation,
  developers.job_seeker,
  ARRAY(
    SELECT tag_details
    FROM users_to_tag_details
    WHERE
      users_to_tag_details.tag_category = 'technologies'
      AND users_to_tag_details.user_id = users.id
      AND users_to_tag_details.tag IN ('Apache Spark')
    LIMIT 5
  ) AS matched_technologies,
  ARRAY(
    SELECT tag_details
    FROM users_to_tag_details
    WHERE
      users_to_tag_details.tag_category = 'languages'
      AND users_to_tag_details.user_id = users.id
      AND users_to_tag_details.tag IN ('JavaScript')
    LIMIT 5
  ) AS matched_languages,
  ARRAY(
    SELECT tag_details
    FROM users_to_tag_details
    WHERE
      users_to_tag_details.tag_category = 'technologies'
      AND users_to_tag_details.user_id = users.id
    LIMIT 5
  ) AS technologies,
  ARRAY(
    SELECT tag_details
    FROM users_to_tag_details
    WHERE
      users_to_tag_details.tag_category = 'languages'
      AND users_to_tag_details.user_id = users.id
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
  ) AS updated_by_analyzer
FROM users
INNER JOIN developers ON developers.user_id = users.id
WHERE users.developer = TRUE
ORDER BY
  avg_grade DESC,
  updated_by_analyzer DESC
LIMIT 20
OFFSET 0;

-- tags autocomplete
SELECT JSON_BUILD_OBJECT('category', tag_categories.category_rest_id, 'tag', tags.tag) as tags
FROM tags
JOIN tag_categories ON tag_categories.id = tags.category_id
WHERE tags.keywords ILIKE '%js%' -- FIXME: index
ORDER BY
 tags.weight DESC,
 tags.clicked DESC
LIMIT 5;

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
    SELECT tag_details
    FROM users_to_tag_details
    WHERE
      users_to_tag_details.tag_category = 'technologies'
      AND users_to_tag_details.user_id = users.id
  ) AS technologies,
  ARRAY(
    SELECT tag_details
    FROM users_to_tag_details
    WHERE
      users_to_tag_details.tag_category = 'languages'
      AND users_to_tag_details.user_id = users.id
  ) AS languages
FROM users
JOIN developers ON developers.user_id = users.id
WHERE users.id = 1
LIMIT 1;

-- delete contact
DELETE FROM contacts
WHERE
  contacts.user_id = 1
  AND contacts.category_id = (SELECT id FROM contact_categories WHERE category = 'Email');
