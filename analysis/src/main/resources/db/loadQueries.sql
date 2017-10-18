SELECT
  languages.language AS language,
  github_search_queries.filename,
  github_search_queries.min_repo_size_kib AS minRepoSizeKiB,
  github_search_queries.max_repo_size_kib AS maxRepoSizeKiB,
  github_search_queries.min_stars AS minStars,
  github_search_queries.max_stars AS maxStars,
  github_search_queries.pattern
FROM github_search_queries
INNER JOIN languages ON languages.id = github_search_queries.language_id
WHERE enabled = true
