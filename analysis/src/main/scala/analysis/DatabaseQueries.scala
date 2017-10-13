package analysis

import github.GithubUser
import utils.{ResourceUtils, StringUtils}

object DatabaseQueries extends ResourceUtils {

  def buildAnalysisResultQuery(users: Iterable[GithubUser], gradedRepositories: Iterable[GradedRepository]): String = {
    import StringUtils._

    val repositories: Map[String, GradedRepository] = gradedRepositories.map(repo => repo.idBase64 -> repo).toMap

    val queries: Iterable[String] = users.flatMap { user =>
      val githubUserId = user.id.toString

      val userValues: Map[String, String] = Map(
        "github_user_id" -> githubUserId,
        "github_login" -> user.login,
        "full_name" -> user.fullName.getOrElse(""),
        "job_seeker" -> user.jobSeeker.getOrElse(false).toString,
        "description" -> user.description.getOrElse("")
      )
      val saveUserSQL: String = saveUserTemplate.formatTemplate(userValues)

      val contacts: Map[String, String] = Seq("Email" -> user.email, "Website" -> user.blog).flatMap {
        case (category: String, Some(contact: String)) =>
          Seq("github_user_id" -> githubUserId, "category" -> category, "contact" -> contact)
      }.toMap
      val saveContactsSQL: String = saveContactTemplate.formatTemplate(contacts)

      val repositoriesOfUser: Seq[GradedRepository] = user.repositories.flatMap(repo => repositories.get(repo.idBase64))

      def tagsSQL(category: String, tags: Seq[String]): Seq[String] = tags.map { tag =>
        saveTagTemplate.formatTemplate(
          Map(
            "tag_category_rest_id" -> category,
            "tag" -> tag,
            "keywords" -> tag.toLowerCase,
            "github_user_id" -> githubUserId
          ))
      }

      val languages: Seq[String] = repositoriesOfUser.flatMap(repo => repo.languages)
      val technologies: Seq[String] = repositoriesOfUser.flatMap(repo => repo.technologies)
      val saveTagsSQL: Seq[String] = tagsSQL("languages", languages) ++ tagsSQL("technologies", technologies)

      val saveRepositoriesSQL: Seq[String] = repositoriesOfUser.map { repo =>
        saveRepositoryTemplate.formatTemplate(
          Map("raw_repository_id" -> repo.idBase64,
              "github_user_id" -> githubUserId,
              "repository_name" -> repo.name,
              "lines_of_code" -> repo.linesOfCode.toString)
        )
      }

      val saveGradesSQL: Seq[String] = repositoriesOfUser.flatMap { repo =>
        repo.grades.map(
          grade =>
            saveGradeTemplate.formatTemplate(
              Map("raw_repository_id" -> repo.idBase64,
                  "grade_category" -> grade.gradeCategory,
                  "value" -> grade.value.toString)
          ))
      }

      Seq(saveUserSQL, saveContactsSQL) ++ saveTagsSQL ++ saveRepositoriesSQL ++ saveGradesSQL
    }

    if (queries.isEmpty) ""
    else (Seq("BEGIN") ++ queries ++ Seq("COMMIT")).mkString(";")
  }

  val loadQueries: String = resourceToString("/db/loadQueries.sql")
  val loadWarningsToGradeCategory: String = resourceToString("/db/loadWarningsToGradeCategory.sql")
  val loadWeightedTechnologies: String = resourceToString("/db/loadWeightedTechnologies.sql")

  private val saveContactTemplate = resourceToString("/db/saveContact.sql")
  private val saveGradeTemplate = resourceToString("/db/saveGrade.sql")
  private val saveRepositoryTemplate = resourceToString("/db/saveRepository.sql")
  private val saveTagTemplate = resourceToString("/db/saveTag.sql")
  private val saveUserTemplate = resourceToString("/db/saveUser.sql")

}
