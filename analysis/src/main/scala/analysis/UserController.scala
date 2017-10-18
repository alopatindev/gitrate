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
    val future = runQuery(query).map(_ => ())
    future.foreach(_ => logInfo("finished saving analysis result"))
    future
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

  // TODO: move to utility module?
  type MapOfSet[K, V] = Map[K, Set[V]]
  private def seqOfMapsToMap[K, V](xs: Seq[MapOfSet[K, V]], out: MapOfSet[K, V] = Map[K, Set[V]]()): MapOfSet[K, V] =
    xs match { // TODO: rewrite with foldLeft?
      case (x: MapOfSet[K, V]) :: tail =>
        val newOut = x.foldLeft(out) {
          case (outA, (key, value)) =>
            val oldValue: Set[V] = outA.getOrElse(key, Set.empty)
            val newValue: Set[V] = oldValue ++ value
            outA + (key -> newValue)
        }
        seqOfMapsToMap(tail, newOut)
      case _ => out
    }

  private def buildAnalysisResultQuery(users: Iterable[GithubUser], gradedRepositories: Iterable[GradedRepository]) = {
    val repositories: Map[String, GradedRepository] = gradedRepositories.map(repo => repo.idBase64 -> repo).toMap
    DBIO
      .sequence(
        for {
          user <- users.toSeq
          repositoriesOfUser: Seq[GradedRepository] = user.repositories.flatMap(repo => repositories.get(repo.idBase64))
          languageToTechnologiesSeq: Seq[MapOfSet[String, String]] = repositoriesOfUser.map(_.languageToTechnologies)
          languageToTechnologies: MapOfSet[String, String] = seqOfMapsToMap(languageToTechnologiesSeq)
        } yield
          DBIO.seq(
            buildSaveUserQuery(user),
            buildSaveContactsQuery(user),
            buildSaveTechnologiesQuery(languageToTechnologies, user.id),
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
      updated_by_user,
      viewed
    ) VALUES (
      DEFAULT,
      ${user.id},
      ${user.login},
      ${user.fullName.getOrElse("")},
      DEFAULT,
      DEFAULT
    ) ON CONFLICT (github_user_id) DO UPDATE
    SET
      github_login = ${user.login},
      full_name = ${user.fullName.getOrElse("")};

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

  private def buildSaveTechnologiesQuery(languageToTechnologies: Map[String, Set[String]], githubUserId: Int) =
    DBIO.sequence(for {
      (language: String, technologies: Set[String]) <- languageToTechnologies
      technology: String <- technologies
    } yield sqlu"""
      INSERT INTO technologies (
        id,
        language_id,
        technology,
        technology_human_readable
      ) VALUES (
        DEFAULT,
        (SELECT id FROM languages WHERE language = $language),
        $technology,
        DEFAULT
      ) ON CONFLICT (language_id, technology) DO NOTHING;

      INSERT INTO technologies_users (
        id,
        technology_id,
        user_id
      ) VALUES (
        DEFAULT,
        (
          SELECT technologies.id
          FROM technologies
          INNER JOIN languages ON languages.id = technologies.language_id
          WHERE technologies.technology = $technology AND languages.language = $language
        ),
        (SELECT id FROM users WHERE github_user_id = $githubUserId)
      ) ON CONFLICT (technology_id, user_id) DO NOTHING;

      INSERT INTO technologies_users_settings (
        id,
        technologies_users_id,
        verified
      ) VALUES (
        DEFAULT,
        (
          SELECT technologies_users.id
          FROM technologies_users
          INNER JOIN technologies ON technologies.id = technologies_users.technology_id
          INNER JOIN users ON users.id = technologies_users.user_id
          WHERE technologies.technology = $technology AND users.github_user_id = $githubUserId
        ),
        TRUE
      ) ON CONFLICT (technologies_users_id) DO UPDATE
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
