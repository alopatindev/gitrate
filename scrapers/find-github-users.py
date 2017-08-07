#!/usr/bin/env python3

QUERY = '''
query {
  search(
    first:3,
    type:REPOSITORY,
    query:"in:README.md sort:updated mirror:false fork:false '](https://travis-ci.org/'"
  ) {
    nodes {
      ... on Repository {
        id
        name
        createdAt
        pushedAt
        defaultBranchRef {
          target {
            ... on Commit {
              id
              history(first:3) {
                nodes {
                  author {
                    user {
                      id
                      #login
                    }
                  }
                }
              }
            }
          }
        }
        owner {
          id
          login
          pinnedRepositories(first:6, orderBy: { field:UPDATED_AT, direction:DESC }) {
            nodes {
              id
              nameWithOwner
              isFork
              isMirror
              createdAt
              pushedAt
              defaultBranchRef {
                target {
                  ... on Commit {
                    history(first:3) {
                      nodes {
                        author {
                          user {
                            id
                            #login
                          }
                        }
                      }
                    }
                  }
                }
              }
              repositoryTopics(first:20) {
                nodes {
                  topic {
                    #id
                    name
                  }
                }
              }
            }
          }
          repositories(first:10, orderBy: { field:UPDATED_AT, direction:DESC }) {
            nodes {
              id
              nameWithOwner
              isFork
              isMirror
              createdAt
              pushedAt
              defaultBranchRef {
                target {
                  ... on Commit {
                    history(first:3) {
                      nodes {
                        author {
                          user {
                            id
                            #login
                          }
                        }
                      }
                    }
                  }
                }
              }
              repositoryTopics(first:20) {
                nodes {
                  topic {
                    #id
                    name
                  }
                }
              }
            }
          }
        }
        repositoryTopics(first:20) {
          nodes {
            topic {
              #id
              name
            }
          }
        }
      }
    }
  }
}
'''

print(QUERY)
