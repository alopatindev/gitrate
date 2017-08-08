#!/usr/bin/env python3
# coding=utf8

import json
import requests
import sys


GITHUB_TOKEN = sys.argv[1]


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


def search_users():
    pattern = '](https://travis-ci.org/'
    query = "in:README.md sort:updated mirror:false fork:false '%s'" % pattern
    query_args = {
        'search_query': query,
        'results_limit': 20,
        'commits_limit': 2,
        'topics_limit': 20,
        'repositories_limit': 20,
        'pinned_repositories_limit': 6,
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

        yield result


for result in search_users():
    print(result)
    break
