-- DROP KEYSPACE IF EXISTS gitrate;


CREATE KEYSPACE IF NOT EXISTS gitrate
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};


USE gitrate;


CREATE TABLE IF NOT EXISTS users (
    id TIMEUUID,

    github_id INT,
    github_login TEXT,

    full_name TEXT,
    description TEXT,
    location TEXT,
    company TEXT,

    position TEXT,
    developer_level TEXT, // null/unknown, not_developer, junior, middle, senior

    contacts FROZEN<MAP<TEXT, TEXT>>,
    show_email BOOLEAN,

    job_seeker BOOLEAN,
    available_for_relocation BOOLEAN,

    verified_languages FROZEN<LIST<TEXT>>,
    verified_technologies FROZEN<LIST<TEXT>>,
    languages FROZEN<LIST<TEXT>>,
    technologies FROZEN<LIST<TEXT>>,

    total_grade FLOAT,
    grades FROZEN<MAP<TEXT, FLOAT>>,

    working_experience_years INT,
    programming_experience_years INT,

    updated_by_user TIMESTAMP,
    updated_by_analyzer TIMESTAMP,

    PRIMARY KEY ((id), total_grade, updated_by_analyzer, updated_by_user, verified_languages, verified_technologies, languages, technologies, working_experience_years, programming_experience_years, developer_level)
)
WITH CLUSTERING ORDER BY (
    total_grade DESC,
    updated_by_analyzer DESC,
    updated_by_user DESC,
    verified_languages ASC,
    verified_technologies ASC,
    languages ASC,
    technologies ASC,
    working_experience_years DESC,
    programming_experience_years DESC,
    developer_level DESC
);


CREATE TABLE IF NOT EXISTS repositories (
    github_repo_id TEXT,
    user_id TIMEUUID,
    name TEXT,
    languages FROZEN<LIST<TEXT>>,
    technologies FROZEN<LIST<TEXT>>,
    grades FROZEN<MAP<TEXT, FLOAT>>,
    state TEXT,
    updated_by_analyzer TIMESTAMP,
    PRIMARY KEY ((github_repo_id), user_id, state)
);


CREATE TABLE IF NOT EXISTS github_search_queries (
    partition INT,
    id TIMEUUID,
    language TEXT,
    filename TEXT,
    min_repo_size_kib INT,
    max_repo_size_kib INT,
    min_stars INT,
    max_stars INT,
    pattern TEXT,
    enabled BOOLEAN,
    PRIMARY KEY ((partition), enabled, id)
);


CREATE TABLE IF NOT EXISTS technologies_aliases (
    alias TEXT,
    technology TEXT,
    PRIMARY KEY ((alias), technology)
);


CREATE TABLE IF NOT EXISTS users_statistics (
    user_id TIMEUUID,
    views COUNTER,
    PRIMARY KEY (user_id)
);


CREATE TABLE IF NOT EXISTS languages_statistics (
    id TIMEUUID,
    name TEXT,
    clicks COUNTER,
    PRIMARY KEY ((id), name)
);


CREATE TABLE IF NOT EXISTS technologies_statistics (
    id TIMEUUID,
    name TEXT,
    clicks COUNTER,
    PRIMARY KEY ((id), name)
);


CREATE TABLE IF NOT EXISTS locations_statistics (
    id TIMEUUID,
    name TEXT,
    clicks COUNTER,
    PRIMARY KEY ((id), name)
);
