package controllers

import analysis.github.GithubUser
import analysis.GradedRepository
import analysis.TextAnalyzer.StemToSynonyms
import common.LocationParser.Location
import utils.CollectionUtils._
import utils.{LogUtils, SlickUtils}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._

object UserController extends SlickUtils with LogUtils {

  case class AnalysisResult(users: Iterable[GithubUser],
                            gradedRepositories: Iterable[GradedRepository],
                            languageToTechnologyToSynonyms: Iterable[(String, StemToSynonyms)],
                            userToLocation: Map[Int, Location])

  def saveAnalysisResult(result: AnalysisResult): Future[Unit] = {
    val query = buildSaveAnalysisResultQuery(result)
    val future = runQuery(query).map(_ => ())
    future.foreach(_ => logInfo("finished saving analysis result"))
    future
  }

  private def buildSaveAnalysisResultQuery(result: AnalysisResult) =
    DBIO
      .seq(buildSaveAllUsersQuery(result), buildSaveTechnologySynonyms(result.languageToTechnologyToSynonyms))
      .transactionally

  private def buildSaveAllUsersQuery(result: AnalysisResult) = {
    val users = result.users.toSeq
    logInfo(s"saving ${users.length} users")
    val repositories: Map[String, GradedRepository] = result.gradedRepositories.map(repo => repo.idBase64 -> repo).toMap
    DBIO
      .sequence(
        for {
          user <- users
          repositoriesOfUser: Seq[GradedRepository] = user.repositories.flatMap(repo => repositories.get(repo.idBase64))
          languageToTechnologiesSeq: Seq[MapOfSeq[String, String]] = repositoriesOfUser.map(_.languageToTechnologies)
          languageToTechnologies: MapOfSeq[String, String] = seqOfMapsToMap(languageToTechnologiesSeq)
            .mapValues(_.toSet.toSeq)
          location: Location = result.userToLocation.getOrElse(user.id, Location(None, None))
        } yield
          DBIO.seq(
            buildSaveLocationQuery(location),
            buildSaveUserQuery(user),
            buildSaveDeveloperQuery(user, location),
            buildSaveContactsQuery(user),
            buildSaveLanguagesAndTechnologiesQuery(languageToTechnologies, user.id),
            buildSaveRepositoriesQuery(repositoriesOfUser, user.id),
            buildSaveGradesQuery(repositoriesOfUser)
          ))
  }

  private def buildSaveLocationQuery(location: Location) = {
    val countryQuery = location.country.map { country =>
      sqlu"""
      INSERT INTO countries (id, country)
      VALUES (DEFAULT, $country)
      ON CONFLICT (country) DO NOTHING;"""
    }

    val cityQuery = location.city.map { city =>
      sqlu"""
      INSERT INTO cities (id, city)
      VALUES (DEFAULT, $city)
      ON CONFLICT (city) DO NOTHING;"""
    }

    val queries = Seq(countryQuery, cityQuery).flatten
    DBIO.sequence(queries)
  }

  private def buildSaveUserQuery(user: GithubUser) = sqlu"""
    INSERT INTO users (
      id,
      github_user_id,
      github_login,
      full_name,
      updated_by_user
    ) VALUES (
      DEFAULT,
      ${user.id},
      ${user.login},
      ${user.fullName.getOrElse("")},
      DEFAULT
    ) ON CONFLICT (github_user_id) DO UPDATE
    SET
      github_login = ${user.login},
      full_name = ${user.fullName.getOrElse("")}"""

  private def buildSaveDeveloperQuery(user: GithubUser, location: Location) = sqlu"""
    INSERT INTO developers (
      id,
      user_id,
      show_email,
      job_seeker,
      available_for_relocation,
      programming_experience_months,
      work_experience_months,
      description,
      raw_location,
      country_id,
      city_id,
      viewed
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
      ${user.description.getOrElse("")},
      ${user.location.getOrElse("")},
      (
        SELECT id
        FROM countries
        WHERE country = ${location.country}
      ),
      (
        SELECT id
        FROM cities
        WHERE city = ${location.city}
      ),
      DEFAULT
    ) ON CONFLICT (user_id) DO NOTHING"""

  private def buildSaveLanguagesAndTechnologiesQuery(languageToTechnologies: Map[String, Seq[String]],
                                                     githubUserId: Int) =
    DBIO.sequence(for {
      (language: String, technologies: Seq[String]) <- languageToTechnologies
      technology: String <- technologies
    } yield sqlu"""
      INSERT INTO languages (id, language) VALUES (DEFAULT, $language)
      ON CONFLICT (language) DO NOTHING;

      INSERT INTO technologies (
        id,
        language_id,
        technology
      ) VALUES (
        DEFAULT,
        (SELECT id FROM languages WHERE language = $language),
        $technology
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

  private def buildSaveTechnologySynonyms(languageToTechnologyToSynonyms: Iterable[(String, StemToSynonyms)]) =
    DBIO.sequence(for {
      (language, technologiesToSynonyms) <- languageToTechnologyToSynonyms
      (technology, synonyms) <- technologiesToSynonyms
      synonym <- synonyms
    } yield sqlu"""
      INSERT INTO technology_synonyms (id, technology_id, synonym)
      VALUES (
        DEFAULT,
        (
          SELECT technologies.id
          FROM technologies
          JOIN languages ON languages.id = technologies.language_id AND languages.language = $language
          WHERE technologies.technology = $technology
        ),
        $synonym
      ) ON CONFLICT (technology_id, synonym) DO NOTHING""")

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
