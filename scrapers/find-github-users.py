#!/usr/bin/env python3
# coding=utf8

import json
import requests
import sys
from dateutil.parser import parse as isodate


GITHUB_TOKEN = sys.argv[1]
MIN_REPO_AGE_DAYS = 60
MAX_REPO_SIZE_KB = 5120


def gql_execute(query):
    headers = {'Authorization': 'bearer %s' % GITHUB_TOKEN}
    data = {'query': query}
    json_data = json.dumps(data)
    response = requests.post(
        url='https://api.github.com/graphql',
        headers=headers,
        data=json_data
    )
    return json.loads(response.text)


def search_repositories_and_users():
    pattern = '](https://travis-ci.org/'
    query = "in:README.md sort:updated mirror:false fork:false size:<=%d '%s'" % (MAX_REPO_SIZE_KB, pattern)
    query_args = {
        'search_query': query,
        'max_results': 20,
        'max_commits': 2,
        'max_repositories': 20,
        'max_pinned_repositories': 6,
        'max_topics': 20,
        'max_languages': 20,
        'results_offset': '',
    }

    end_cursor = None
    while True:
        if end_cursor is not None:
            query_args['results_offset'] = 'after: "%s"' % end_cursor

        graphql_query = open('find-github-users.graphql').read() % query_args
        result = gql_execute(graphql_query)['data']['search']

        page_info = result['pageInfo']
        if page_info['hasNextPage']:
            end_cursor = page_info['endCursor']
        else:
            break

        yield result['nodes']


def extract_users(repositories_and_users):
    for nodes in repositories_and_users:
        for node in nodes:
            pushed_at = isodate(node['pushedAt'])
            created_at = isodate(node['createdAt'])
            age = (pushed_at - created_at).days
            if age >= MIN_REPO_AGE_DAYS:
                yield node


for result in extract_users(search_repositories_and_users()):
    print(result)
    break
