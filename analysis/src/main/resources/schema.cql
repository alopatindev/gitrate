-- DROP KEYSPACE IF EXISTS hiregooddevs;


CREATE KEYSPACE IF NOT EXISTS hiregooddevs
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};


USE hiregooddevs;


CREATE TABLE IF NOT EXISTS users (
    id timeuuid,

    github_id text,
    github_login text,
    github_avatar_url text,

    full_name text,
    description text,
    location text,

    position text,
    developer_level text, // null/unknown, not_developer, junior, middle, senior

    contacts map<text, text>,
    show_email boolean,

    job_seeker boolean,
    available_for_relocation boolean,

    verified_languages frozen<set<text>>,
    verified_technologies frozen<set<text>>,
    languages frozen<set<text>>,
    technologies frozen<set<text>>,

    total_grade float,
    grades map<text, float>,

    working_experience_years int,
    programming_experience_years int,

    updated_by_user timestamp,
    updated_by_analyzer timestamp,

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
    github_repo_id text,
    user_id int,
    name text,
    languages set<text>,
    technologies set<text>,
    grades map<text, float>,
    state text,
    updated_by_analyzer timestamp,
    PRIMARY KEY ((github_repo_id), user_id, state)
);


CREATE TABLE IF NOT EXISTS github_search_queries (
    query text,
    enabled boolean,
    PRIMARY KEY ((query), enabled)
);


CREATE TABLE IF NOT EXISTS technologies_aliases (
    name text,
    aliases frozen<set<text>>,
    PRIMARY KEY ((name), aliases)
);


CREATE TABLE IF NOT EXISTS users_statistics (
    user_id timeuuid,
    views counter,
    PRIMARY KEY (user_id)
);


CREATE TABLE IF NOT EXISTS languages_statistics (
    id timeuuid,
    name text,
    clicks counter,
    PRIMARY KEY ((id), name)
);


CREATE TABLE IF NOT EXISTS technologies_statistics (
    id timeuuid,
    name text,
    clicks counter,
    PRIMARY KEY ((id), name)
);


CREATE TABLE IF NOT EXISTS locations_statistics (
    id timeuuid,
    name text,
    clicks counter,
    PRIMARY KEY ((id), name)
);
