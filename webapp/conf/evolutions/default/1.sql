# --- !Ups

CREATE TABLE IF NOT EXISTS countries (
  id SERIAL PRIMARY KEY,
  country TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS cities (
  id SERIAL PRIMARY KEY,
  city TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  github_user_id INTEGER UNIQUE NOT NULL,
  github_login TEXT NOT NULL,
  full_name TEXT NOT NULL,
  updated_by_user TIMESTAMP
);

CREATE TABLE IF NOT EXISTS developers (
  id SERIAL PRIMARY KEY,
  user_id INTEGER UNIQUE REFERENCES users NOT NULL,
  show_email BOOLEAN,
  job_seeker BOOLEAN NOT NULL,
  available_for_relocation BOOLEAN,
  programming_experience_months SMALLINT,
  work_experience_months SMALLINT,
  description TEXT DEFAULT '' NOT NULL,
  raw_location TEXT DEFAULT '' NOT NULL,
  country_id INTEGER REFERENCES countries,
  city_id INTEGER REFERENCES cities,
  viewed INTEGER DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS contact_categories (
  id SERIAL PRIMARY KEY,
  category TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS contacts (
  id SERIAL PRIMARY KEY,
  category_id INTEGER REFERENCES contact_categories NOT NULL,
  contact TEXT,
  user_id INTEGER REFERENCES users NOT NULL,
  UNIQUE (category_id, contact)
);

CREATE TABLE IF NOT EXISTS languages (
  id SERIAL PRIMARY KEY,
  language TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS technologies (
  id SERIAL PRIMARY KEY,
  language_id INTEGER REFERENCES languages NOT NULL,
  technology TEXT NOT NULL,
  synonym BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (language_id, technology)
);

CREATE TABLE IF NOT EXISTS technologies_users (
  id SERIAL PRIMARY KEY,
  technology_id INTEGER REFERENCES technologies NOT NULL,
  user_id INTEGER REFERENCES users NOT NULL,
  UNIQUE (technology_id, user_id)
);

CREATE TABLE IF NOT EXISTS technologies_users_settings (
  id SERIAL PRIMARY KEY,
  technologies_users_id INTEGER UNIQUE REFERENCES technologies_users NOT NULL,
  verified BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS repositories (
  id SERIAL PRIMARY KEY,
  raw_id TEXT UNIQUE NOT NULL,
  user_id INTEGER REFERENCES users NOT NULL,
  name TEXT NOT NULL,
  lines_of_code INTEGER NOT NULL,
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

CREATE OR REPLACE VIEW users_ranks_helper_view AS
  SELECT
    user_id,
    ARRAY(
      SELECT CAST(JSON_BUILD_OBJECT('category', grade_categories.category, 'value', AVG(grades.value)) AS TEXT)
      FROM grades
      INNER JOIN grade_categories ON grade_categories.id = grades.category_id
      INNER JOIN repositories ON repositories.id = grades.repository_id AND repositories.user_id = TMP.user_id
      GROUP BY grade_categories.id
    ) AS grades,
    ARRAY_AGG(DISTINCT language) AS languages,
    ARRAY_AGG(DISTINCT technology) AS technologies,
    TO_TSVECTOR(STRING_AGG(language, ' ')) AS languages_ts,
    TO_TSVECTOR(STRING_AGG(technology, ' ')) AS technologies_ts
  FROM (
    SELECT
      developers.user_id AS user_id,
      languages.language AS language,
      technologies.technology AS technology,
      (
        SELECT AVG(grades.value)
        FROM grades
        INNER JOIN repositories ON repositories.id = grades.repository_id AND repositories.user_id = developers.user_id
      ) AS avg_grade,
      (
        SELECT MAX(repositories.updated_by_analyzer)
        FROM repositories
        WHERE repositories.user_id = developers.user_id
      ) AS updated_by_analyzer,
      (
        SELECT SUM(repositories.lines_of_code)
        FROM repositories
        WHERE repositories.user_id = developers.user_id
      ) AS total_lines_of_code
    FROM languages
    INNER JOIN technologies ON technologies.language_id = languages.id
    INNER JOIN technologies_users ON technologies_users.technology_id = technologies.id
    INNER JOIN developers ON developers.user_id = technologies_users.user_id
    INNER JOIN technologies_users_settings ON
      technologies_users_settings.technologies_users_id = technologies_users.id
      AND technologies_users_settings.verified = TRUE
    ORDER BY
      avg_grade ASC,
      updated_by_analyzer ASC,
      total_lines_of_code ASC
  ) AS TMP
  WHERE
    avg_grade IS NOT NULL
    AND updated_by_analyzer IS NOT NULL
    AND total_lines_of_code IS NOT NULL
  GROUP BY user_id;

CREATE MATERIALIZED VIEW IF NOT EXISTS users_ranks_matview AS
  SELECT
    ROW_NUMBER() OVER () AS rank,
    users_ranks_helper_view.user_id AS user_id,
    users.github_login AS github_login,
    users.full_name AS full_name,
    users_ranks_helper_view.grades,
    users_ranks_helper_view.languages,
    users_ranks_helper_view.technologies,
    TO_TSVECTOR(users.github_login) AS github_login_ts,
    users_ranks_helper_view.languages_ts,
    users_ranks_helper_view.technologies_ts
  FROM users_ranks_helper_view
  INNER JOIN users ON users.id = users_ranks_helper_view.user_id;

CREATE INDEX IF NOT EXISTS users_ranks_texts_idx
ON users_ranks_matview
USING GIN (github_login_ts, languages_ts, technologies_ts);

# --- !Downs

DROP TABLE IF EXISTS countries CASCADE;
DROP TABLE IF EXISTS cities CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS developers CASCADE;
DROP TABLE IF EXISTS contact_categories CASCADE;
DROP TABLE IF EXISTS contacts CASCADE;
DROP TABLE IF EXISTS languages CASCADE;
DROP TABLE IF EXISTS technologies CASCADE;
DROP TABLE IF EXISTS technologies_users CASCADE;
DROP TABLE IF EXISTS technologies_users_settings CASCADE;
DROP TABLE IF EXISTS repositories CASCADE;
DROP INDEX IF EXISTS repositories_user_id_idx CASCADE;
DROP TABLE IF EXISTS grade_categories CASCADE;
DROP TABLE IF EXISTS grades CASCADE;
DROP VIEW IF EXISTS users_ranks_helper_view CASCADE;
DROP MATERIALIZED VIEW IF EXISTS users_ranks_matview CASCADE;
DROP INDEX IF EXISTS users_ranks_texts_idx CASCADE;
