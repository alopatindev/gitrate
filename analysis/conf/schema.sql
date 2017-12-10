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

CREATE TABLE IF NOT EXISTS warnings (
  id SERIAL PRIMARY KEY,
  warning TEXT NOT NULL,
  grade_category_id INTEGER REFERENCES grade_categories NOT NULL,
  language_id INTEGER REFERENCES languages NOT NULL,
  UNIQUE (warning, grade_category_id, language_id)
);

CREATE TABLE IF NOT EXISTS github_search_queries (
  id SERIAL PRIMARY KEY,
  language_id INTEGER REFERENCES languages NOT NULL,
  filename TEXT NOT NULL,
  min_repo_size_kib INT NOT NULL,
  max_repo_size_kib INT NOT NULL,
  min_stars INT NOT NULL,
  max_stars INT NOT NULL,
  pattern TEXT NOT NULL,
  enabled BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS github_receiver_state (
  id SERIAL PRIMARY KEY,
  key TEXT UNIQUE NOT NULL,
  value TEXT NOT NULL
);
