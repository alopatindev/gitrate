package gitrate.analysis.github

import play.api.libs.json.{JsArray, JsDefined, JsValue}

class GithubParser(reposParser: GithubReposParser) {

  def parseUsersAndRepos(searchResult: JsValue): Seq[GithubUser] = {
    val nodes: Seq[JsValue] = (searchResult \ "nodes") match {
      case JsDefined(JsArray(nodes)) => nodes
      case _                         => Seq()
    }

    for {
      node <- nodes
      (repo, Some(owner)) <- reposParser.parseRepoAndOwner(node)
      targetRepos = (repo :: (owner.pinnedRepos ++ owner.repos).toList).filter(_.isTarget)
      if targetRepos.length >= reposParser.minTargetRepos
    } yield GithubUser(owner.userId, owner.login, targetRepos, reposParser)
  }

}
