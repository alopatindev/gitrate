-- psql --username postgres

-- CREATE ROLE gitrate NOSUPERUSER CREATEDB NOCREATEROLE INHERIT LOGIN;
--DROP DATABASE gitrate;
--CREATE DATABASE gitrate OWNER gitrate;

-- psql --username gitrate --dbname gitrate --file=conf/schema.sql --file=conf/data.sql

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
  raw_id TEXT UNIQUE NOT NULL, -- TODO: index
  user_id INTEGER REFERENCES users NOT NULL,
  name TEXT NOT NULL,
  lines_of_code INTEGER NOT NULL, -- TODO: index
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

CREATE TABLE IF NOT EXISTS warnings (
  id SERIAL PRIMARY KEY,
  warning TEXT NOT NULL, -- TODO: index?
  grade_category_id INTEGER REFERENCES grade_categories NOT NULL,
  tag_id INTEGER REFERENCES tags NOT NULL,
  UNIQUE (warning, grade_category_id, tag_id)
);

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
    ) AS updated_by_analyzer,
    (
      SELECT SUM(repositories.lines_of_code)
      FROM repositories
      WHERE user_id = users.id
    ) AS total_lines_of_code
  FROM users
  INNER JOIN developers ON developers.user_id = users.id
  WHERE users.developer = TRUE
  ORDER BY
    avg_grade DESC,
    updated_by_analyzer DESC,
    total_lines_of_code DESC -- TODO: filtering instead of ordering?
  LIMIT 20
  OFFSET 0;
