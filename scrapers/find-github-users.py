#!/usr/bin/env python3

query = "in:README.md sort:updated mirror:false fork:false '](https://travis-ci.org/'"

args = {
    'query': query,
    'results_limit': 20,
    'commits_limit': 2,
    'topics_limit': 20,
    'repositories_limit': 20,
    'pinned_repositories_limit': 6,
}

graphql_query = open('find-github-users.graphql').read() % args

print(graphql_query)
