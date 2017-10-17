package analysis

import github.GithubUser
import utils.LogUtils
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import slick.jdbc.PostgresProfile.api._

object UserController extends LogUtils {

  type SQLTransaction[T] = DBIOAction[T, NoStream, Effect with Effect.Transactional]
  type SQLQuery[Container, T] = slick.sql.SqlStreamingAction[Container, T, Effect]

  def saveAnalysisResult(users: Iterable[GithubUser], gradedRepositories: Iterable[GradedRepository]): Future[Unit] = {
    val query = buildAnalysisResultQuery(users, gradedRepositories)
    runQuery(query).map(_ => ())
  }

  def runQuery[T](query: SQLTransaction[T]): Future[T] = {
    val result = db.run(query).logErrors()
    result.onComplete(_ => db.close())
    result
  }

  def runQuery[Container, T](query: SQLQuery[Container, T]): Future[Container] = {
    val result = db.run(query).logErrors()
    result.onComplete(_ => db.close())
    result
  }

  private def db: Database = Database.forConfig("db.postgresql")

  private def buildAnalysisResultQuery(users: Iterable[GithubUser], gradedRepositories: Iterable[GradedRepository]) = {
    val repositories: Map[String, GradedRepository] = gradedRepositories.map(repo => repo.idBase64 -> repo).toMap
    DBIO
      .sequence(
        for {
          user <- users.toSeq
          repositoriesOfUser: Seq[GradedRepository] = user.repositories.flatMap(repo => repositories.get(repo.idBase64))
          languages: Seq[String] = repositoriesOfUser.flatMap(repo => repo.languages)
          technologies: Seq[String] = repositoriesOfUser.flatMap(repo => repo.technologies)
        } yield
          DBIO.seq(
            buildSaveUserQuery(user),
            buildSaveContactsQuery(user),
            buildSaveTagsQuery("languages", languages, user.id),
            buildSaveTagsQuery("technologies", technologies, user.id),
            buildSaveRepositoriesQuery(repositoriesOfUser, user.id),
            buildSaveGradesQuery(repositoriesOfUser)
          ))
      .transactionally
  }

  private def buildSaveUserQuery(user: GithubUser) = sqlu"""
    INSERT INTO users (
      id,
      github_user_id,
      github_login,
      full_name,
      developer,
      updated_by_user,
      viewed
    ) VALUES (
      DEFAULT,
      ${user.id},
      ${user.login},
      ${user.fullName.getOrElse("")},
      TRUE,
      DEFAULT,
      DEFAULT
    ) ON CONFLICT (github_user_id) DO UPDATE
    SET
      github_login = ${user.login},
      full_name = ${user.fullName.getOrElse("")},
      developer = TRUE;

    INSERT INTO developers (
      id,
      user_id,
      show_email,
      job_seeker,
      available_for_relocation,
      programming_experience_months,
      work_experience_months,
      description
    ) VALUES (
      DEFAULT,
      (
        SELECT id
        FROM users
        WHERE github_user_id = ${user.id}
      ),
      DEFAULT,
      ${user.jobSeeker.getOrElse(false)},
      DEFAULT,
      DEFAULT,
      DEFAULT,
      ${user.description.getOrElse("")}
    ) ON CONFLICT (user_id) DO NOTHING"""

  private def buildSaveTagsQuery(category: String, tags: Seq[String], githubUserId: Int) = // scalastyle:ignore
    DBIO.sequence(for {
      tag <- tags
      keywords = tag.toLowerCase
    } yield sqlu"""
      INSERT INTO tags (
        id,
        category_id,
        tag,
        keywords,
        weight,
        clicked
      ) VALUES (
        DEFAULT,
        (SELECT id FROM tag_categories WHERE category_rest_id = $category),
        $tag,
        $keywords,
        DEFAULT,
        DEFAULT
      ) ON CONFLICT (category_id, tag) DO NOTHING;

      INSERT INTO tags_users (
        id,
        tag_id,
        user_id
      ) VALUES (
        DEFAULT,
        (
          SELECT tags.id
          FROM tags
          INNER JOIN tag_categories ON tag_categories.id = tags.category_id
          WHERE tag = $tag AND tag_categories.category_rest_id = $category
        ),
        (SELECT id FROM users WHERE github_user_id = $githubUserId)
      ) ON CONFLICT (tag_id, user_id) DO NOTHING;

      INSERT INTO tags_users_settings (
        id,
        tags_users_id,
        verified
      ) VALUES (
        DEFAULT,
        (
          SELECT tags_users.id
          FROM tags_users
          INNER JOIN tags ON tags.id = tags_users.tag_id
          INNER JOIN tag_categories ON tag_categories.id = tags.category_id
          INNER JOIN users ON users.id = tags_users.user_id
          WHERE
            tags.tag = $tag
            AND tag_categories.category_rest_id = $category
            AND users.github_user_id = $githubUserId
        ),
        TRUE
      ) ON CONFLICT (tags_users_id) DO UPDATE
      SET verified = TRUE""")

  private def buildSaveGradesQuery(repositoriesOfUser: Seq[GradedRepository]) =
    DBIO.sequence(for {
      repo <- repositoriesOfUser
      grade <- repo.grades
    } yield sqlu"""
      INSERT INTO grades (
        id,
        category_id,
        value,
        repository_id
      ) VALUES (
        DEFAULT,
        (SELECT id FROM grade_categories WHERE category = ${grade.gradeCategory}),
        ${grade.value},
        (SELECT id FROM repositories WHERE raw_id = ${repo.idBase64})
      ) ON CONFLICT (category_id, repository_id) DO UPDATE
      SET value = ${grade.value}""")

  private def buildSaveRepositoriesQuery(repositoriesOfUser: Seq[GradedRepository], githubUserId: Int) =
    DBIO.sequence(for {
      repo <- repositoriesOfUser
    } yield sqlu"""
      INSERT INTO repositories (
        id,
        raw_id,
        user_id,
        name,
        lines_of_code,
        updated_by_analyzer
      ) VALUES (
        DEFAULT,
        ${repo.idBase64},
        (SELECT id FROM users WHERE github_user_id = $githubUserId),
        ${repo.name},
        ${repo.linesOfCode},
        DEFAULT
      ) ON CONFLICT (raw_id) DO UPDATE
      SET
        updated_by_analyzer = DEFAULT,
        lines_of_code = ${repo.linesOfCode}""")

  private def buildSaveContactsQuery(user: GithubUser) =
    DBIO.sequence(for {
      (category, Some(contact)) <- Seq("Email" -> user.email, "Website" -> user.blog)
    } yield sqlu"""
      INSERT INTO contacts (
        id,
        category_id,
        contact,
        user_id
      ) VALUES (
        DEFAULT,
        (SELECT id FROM contact_categories WHERE category = $category),
        $contact,
        (SELECT id FROM users WHERE github_user_id = ${user.id})
      ) ON CONFLICT (category_id, contact) DO NOTHING""")

}
