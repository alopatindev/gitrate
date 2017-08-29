DROP KEYSPACE IF EXISTS hiregooddevs;


CREATE KEYSPACE IF NOT EXISTS hiregooddevs
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};


USE hiregooddevs;


CREATE TABLE IF NOT EXISTS users (
    id uuid,

    github_id text,
    github_login text,
    github_avatar_url text,

    full_name text,
    description text,
    location text,

    position text,
    developer_level int, -- FIXME: enum? (null/unknown, not_developer, junior, middle, senior)

    contacts map<text, text>,
    show_email boolean,

    job_seeker boolean,
    available_for_relocation boolean,

    verified_languages frozen<set<text>>,
    verified_technologies frozen<set<text>>,
    languages frozen<set<text>>,
    technologies frozen<set<text>>,

    total_grade double,
    grades map<text, double>,

    working_experience_years int,
    programming_experience_years int,

    updated_by_user timestamp,
    updated_by_analyzer timestamp,
    views counter,

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


CREATE TABLE IF NOT EXISTS tags (
    id uuid,
    name text,
    category text,
    clicked counter,
    PRIMARY KEY ((id), name)
);


CREATE TABLE IF NOT EXISTS repositories (
    github_repo_id text,
    user_id int,
    name text,
    tags set<frozen<tuple<text, text>>>, -- FIXME: type?
    grades map<text, double>,
    -- TODO: state? error text?
    -- TODO: updated_by_analyzer?
    PRIMARY KEY ((github_repo_id, user_id))
);
