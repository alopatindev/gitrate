// Copyright 2017 Alexander Lopatin
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package controllers

import analysis.github.{GithubRepository, GithubUser}
import analysis.{Grade, GradedRepository}
import controllers.UserController.AnalysisResult
import common.LocationParser.Location
import testing.PostgresTestUtils

import java.net.URL
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._

class UserControllerSpec extends PostgresTestUtils {

  "saveAnalysisResult" should {

    "process empty input" in { _ =>
      val analysisResult = AnalysisResult(Iterable.empty, Iterable.empty, Map.empty)
      val saveResult = UserController.saveAnalysisResult(analysisResult)
      val _ = Await.result(saveResult, Duration.Inf)

      type T = String
      type Container = Vector[T]
      val query: UserController.SQLQuery[Container, T] = sql"""SELECT github_login FROM users""".as[T]
      val users: Future[Container] = UserController.runQuery(query)
      val result: Container = Await.result(users, Duration.Inf)

      assert(result.isEmpty)
    }

    "process single user" in { _ =>
      val analysisResult = AnalysisResult(fakeSingleUser, fakeGradedRepositoryOfSingleUser, Map.empty)
      val saveResult = UserController.saveAnalysisResult(analysisResult)
      val _ = Await.result(saveResult, Duration.Inf)

      type T = String
      type Container = Vector[T]
      val query: UserController.SQLQuery[Container, T] = sql"""SELECT github_login FROM users""".as[T]
      val users: Future[Container] = UserController.runQuery(query)
      val result: Container = Await.result(users, Duration.Inf)

      assert(result.length === 1)
      assert(result.head === fakeSingleUser.head.login)
    }

    "process multiple users" in { _ =>
      val analysisResult = AnalysisResult(fakeTwoUsers, fakeGradedRepositoriesOfTwoUsers, Map.empty)
      val saveResult = UserController.saveAnalysisResult(analysisResult)
      val _ = Await.result(saveResult, Duration.Inf)

      type T = String
      type Container = Vector[T]
      val query: UserController.SQLQuery[Container, T] = sql"""SELECT github_login FROM users""".as[T]
      val users: Future[Container] = UserController.runQuery(query)
      val result: Container = Await.result(users, Duration.Inf)

      assert(result.length === 2)
      assert(result === fakeTwoUsers.map(_.login))
    }

    "save contacts" in { _ =>
      val analysisResult = AnalysisResult(fakeTwoUsers, fakeGradedRepositoriesOfTwoUsers, Map.empty)
      val saveResult = UserController.saveAnalysisResult(analysisResult)
      val _ = Await.result(saveResult, Duration.Inf)

      type T = (String, String, String)
      type Container = Vector[T]
      val query: UserController.SQLQuery[Vector[T], T] = sql"""
        SELECT users.github_login, contact_categories.category, contacts.contact
        FROM users
        INNER JOIN contacts ON contacts.user_id = users.id
        INNER JOIN contact_categories ON contact_categories.id = contacts.category_id""".as[T]
      val data: Future[Container] = UserController.runQuery(query)
      val result: Container = Await.result(data, Duration.Inf)

      assert(result.contains((fakeUserA.login, "Email", fakeUserA.email.get)))
      assert(result.contains((fakeUserA.login, "Website", fakeUserA.blog.get)))
      assert(result.contains((fakeUserB.login, "Website", fakeUserB.blog.get)))
    }

    "save languages and technologies" in { _ =>
      val analysisResult = AnalysisResult(fakeTwoUsers, fakeGradedRepositoriesOfTwoUsers, Map.empty)
      val saveResult = UserController.saveAnalysisResult(analysisResult)
      val _ = Await.result(saveResult, Duration.Inf)

      type T = (String, String, String, Boolean)
      type Container = Vector[T]
      val query: UserController.SQLQuery[Vector[T], T] = sql"""
        SELECT users.github_login, languages.language, technologies.technology, technologies_users_settings.verified
        FROM users
        INNER JOIN technologies_users ON technologies_users.user_id = users.id
        INNER JOIN technologies ON technologies.id = technologies_users.technology_id
        INNER JOIN technologies_users_settings ON technologies_users_settings.id = technologies_users.id
        INNER JOIN languages ON languages.id = technologies.language_id""".as[T]
      val data: Future[Container] = UserController.runQuery(query)
      val result: Container = Await.result(data, Duration.Inf)

      val user = fakeUserB
      val repo = user.repositories.head

      val languagesToTechnologies: Map[String, Seq[String]] =
        fakeGradedRepositoriesOfTwoUsers.filter(_.idBase64 == repo.idBase64).head.languageToTechnologies
      assert(languagesToTechnologies.nonEmpty)

      languagesToTechnologies.foreach {
        case (language, technologies) =>
          technologies.foreach { technology =>
            val row = (user.login, language, technology, true)
            assert(result contains row)
          }
      }
    }

    "save not graded languages" in { _ =>
      val analysisResult = AnalysisResult(fakeSingleUser, fakeGradedRepositoryOfSingleUser, Map.empty)
      val saveResult = UserController.saveAnalysisResult(analysisResult)
      val _ = Await.result(saveResult, Duration.Inf)

      val language = "Perl"
      assert(!(fakeGradedRepoA.languageToTechnologies.keySet contains language))

      type T = String
      type Container = Vector[T]
      val query: UserController.SQLQuery[Vector[T], T] = sql"""
        SELECT language
        FROM languages
        WHERE language = $language""".as[T]
      val users: Future[Container] = UserController.runQuery(query)
      val result: Container = Await.result(users, Duration.Inf)

      assert(result.nonEmpty)
    }

    "save technology synonyms" in { _ =>
      val analysisResult = AnalysisResult(fakeTwoUsers, fakeGradedRepositoriesOfTwoUsers, Map.empty)
      val saveResult = UserController.saveAnalysisResult(analysisResult)
      val _ = Await.result(saveResult, Duration.Inf)

      type T = (String, Boolean)
      type Container = Vector[T]
      val query: UserController.SQLQuery[Vector[T], T] = sql"""
        SELECT technologies.technology, technologies.synonym
        FROM technologies""".as[T]
      val data: Future[Container] = UserController.runQuery(query)
      val synonyms = Await.result(data, Duration.Inf).toMap

      assert(synonyms("eslint") === false)
      assert(synonyms("eslint-plugin-better") === true)
    }

    "downgrade technologies to synonyms" in { _ =>
      type T = (String, Boolean)
      type Container = Vector[T]
      val query: UserController.SQLQuery[Vector[T], T] = sql"""
        SELECT technologies.technology, technologies.synonym
        FROM technologies""".as[T]

      {
        val analysisResult = AnalysisResult(fakeSingleUser, fakeGradedRepositoryOfSingleUser, Map.empty)
        val saveResult = UserController.saveAnalysisResult(analysisResult)
        val _ = Await.result(saveResult, Duration.Inf)

        val data: Future[Container] = UserController.runQuery(query)
        val synonyms = Await.result(data, Duration.Inf).toMap

        assert(synonyms("eslint-plugin-better") === false)
      }

      {
        val analysisResult = AnalysisResult(fakeTwoUsers, fakeGradedRepositoriesOfTwoUsers, Map.empty)
        val saveResult = UserController.saveAnalysisResult(analysisResult)
        val _ = Await.result(saveResult, Duration.Inf)

        val data: Future[Container] = UserController.runQuery(query)
        val synonyms = Await.result(data, Duration.Inf).toMap

        assert(synonyms("eslint-plugin-better") === true)
        assert(synonyms("eslint") === false)
      }
    }

    "not upgrade synonyms to technologies" in { _ =>
      type T = (String, Boolean)
      type Container = Vector[T]
      val query: UserController.SQLQuery[Vector[T], T] = sql"""
        SELECT technologies.technology, technologies.synonym
        FROM technologies""".as[T]

      {
        val analysisResult = AnalysisResult(fakeTwoUsers, fakeGradedRepositoriesOfTwoUsers, Map.empty)
        val saveResult = UserController.saveAnalysisResult(analysisResult)
        val _ = Await.result(saveResult, Duration.Inf)

        val data: Future[Container] = UserController.runQuery(query)
        val synonyms = Await.result(data, Duration.Inf).toMap

        assert(synonyms("eslint-plugin-better") === true)
        assert(synonyms("eslint") === false)
      }

      {
        val analysisResult = AnalysisResult(fakeSingleUser, fakeGradedRepositoryOfSingleUser, Map.empty)
        val saveResult = UserController.saveAnalysisResult(analysisResult)
        val _ = Await.result(saveResult, Duration.Inf)

        val data: Future[Container] = UserController.runQuery(query)
        val synonyms = Await.result(data, Duration.Inf).toMap

        assert(synonyms("eslint-plugin-better") === true)
        assert(synonyms("eslint") === false)
      }
    }

    "save locations" in { _ =>
      val analysisResult =
        AnalysisResult(fakeTwoUsers, fakeGradedRepositoriesOfTwoUsers, fakeUserToLocation)
      val saveResult = UserController.saveAnalysisResult(analysisResult)
      val _ = Await.result(saveResult, Duration.Inf)

      type T = (Option[String], Option[String])
      type Container = Vector[T]
      val query: UserController.SQLQuery[Vector[T], T] = sql"""
        SELECT countries.country, cities.city
        FROM developers
        LEFT JOIN countries ON countries.id = developers.country_id
        LEFT JOIN cities ON cities.id = developers.city_id""".as[T]
      val data: Future[Container] = UserController.runQuery(query)
      val result: Container = Await.result(data, Duration.Inf)

      val expected = Seq(
        Some("Russian Federation") -> Some("Saint Petersburg"),
        None -> Some("Saint Petersburg")
      )
      assert(result === expected)
    }

    "save repositories" in { _ =>
      val analysisResult = AnalysisResult(fakeTwoUsers, fakeGradedRepositoriesOfTwoUsers, Map.empty)
      val saveResult = UserController.saveAnalysisResult(analysisResult)
      val _ = Await.result(saveResult, Duration.Inf)

      type T = (String, String, String, String)
      type Container = Vector[T]
      val query: UserController.SQLQuery[Vector[T], T] = sql"""
        SELECT
          users.github_login,
          repositories.raw_id,
          repositories.name,
          repositories.lines_of_code
        FROM users
        INNER JOIN repositories ON repositories.user_id = users.id""".as[T]
      val data: Future[Container] = UserController.runQuery(query)
      val result: Container = Await.result(data, Duration.Inf)

      val repo = fakeUserB.repositories.head
      val gradedRepo = fakeGradedRepositoriesOfTwoUsers.filter(_.idBase64 == repo.idBase64).head
      assert(result.contains((fakeUserB.login, repo.idBase64, repo.name, gradedRepo.linesOfCode.toString)))
    }

    "save grades" in { _ =>
      val analysisResult = AnalysisResult(fakeTwoUsers, fakeGradedRepositoriesOfTwoUsers, Map.empty)
      val saveResult = UserController.saveAnalysisResult(analysisResult)
      val _ = Await.result(saveResult, Duration.Inf)

      type T = (String, String, String)
      type Container = Vector[T]
      val query: UserController.SQLQuery[Vector[T], T] = sql"""
        SELECT
          users.github_login,
          grade_categories.category,
          ROUND(CAST(grades.value AS NUMERIC), 2)
        FROM users
        INNER JOIN repositories ON repositories.user_id = users.id
        INNER JOIN grades ON grades.repository_id = repositories.id
        INNER JOIN grade_categories ON grade_categories.id = grades.category_id""".as[T]
      val data: Future[Container] = UserController.runQuery(query)
      val result: Container = Await.result(data, Duration.Inf)

      val repo = fakeUserB.repositories.head
      val gradedRepo = fakeGradedRepositoriesOfTwoUsers.filter(_.idBase64 == repo.idBase64).head
      val grade = gradedRepo.grades.head
      val value = BigDecimal(grade.value).setScale(2).toString
      assert(result.contains((fakeUserB.login, grade.gradeCategory, value)))
    }

  }

  private val fakeRepoA: GithubRepository = GithubRepository(
    idBase64 = "repoA",
    name = "nameA",
    primaryLanguage = "JavaScript",
    languages = Seq("Perl", "C++"),
    defaultBranch = "developer",
    new URL("http://path.to/developer/archive.tar.gz"),
    1.0
  )

  private val fakeRepoB: GithubRepository = GithubRepository(
    idBase64 = "repoB",
    name = "nameB",
    primaryLanguage = "C",
    languages = Seq("Python", "C++", "JavaScript"),
    defaultBranch = "master",
    archiveURL = new URL("http://path.to/master/archive.tar.gz"),
    ownerToAllCommitsRatio = 0.8
  )

  private val fakeRepoC: GithubRepository = GithubRepository(
    idBase64 = "repoC",
    name = "nameB",
    primaryLanguage = "Bash",
    languages = Seq("Java"),
    defaultBranch = "master",
    archiveURL = new URL("http://path.to/master/archive.tar.gz"),
    ownerToAllCommitsRatio = 0.9
  )

  private val fakeGradedRepoA: GradedRepository =
    GradedRepository(
      idBase64 = "repoA",
      name = "nameA",
      languageToTechnologies = Map("JavaScript" -> Seq("MongoDB", "eslint-plugin-better"), "C++" -> Seq("Boost")),
      grades = Seq(Grade("Maintainable", 0.8)),
      linesOfCode = 100
    )

  private val fakeGradedRepoB: GradedRepository =
    GradedRepository(
      idBase64 = "repoB",
      name = "nameB",
      languageToTechnologies =
        Map("C" -> Seq("PostgreSQL"), "Python" -> Seq("Django"), "C++" -> Seq.empty, "JavaScript" -> Seq("eslint")),
      grades = Seq(Grade("Maintainable", 0.9), Grade("Performant", 0.8)),
      linesOfCode = 200
    )

  private val fakeGradedRepoC: GradedRepository =
    GradedRepository(
      idBase64 = "repoC",
      name = "nameC",
      languageToTechnologies = Map("Bash" -> Seq.empty, "Java" -> Seq("Spring")),
      grades = Seq(Grade("Maintainable", 0.95), Grade("Performant", 0.77)),
      linesOfCode = 300
    )

  private val fakeUserA = GithubUser(
    id = 1,
    login = "userA",
    repositories = Seq(fakeRepoA),
    fullName = Some("Full Name A"),
    description = Some("description A"),
    location = Some("Russia, Saint Petersburg"),
    company = Some("company A"),
    email = Some("a@domain.com"),
    blog = Some("https://a.domain"),
    jobSeeker = Some(false)
  )

  private val fakeUserB = GithubUser(
    id = 2,
    login = "userB",
    repositories = Seq(fakeRepoB, fakeRepoC),
    fullName = Some("Full Name B"),
    description = Some("description B"),
    location = Some("Saint Petersburg"),
    company = Some("company B"),
    email = None,
    blog = Some("https://b.domain"),
    jobSeeker = Some(true)
  )

  private val fakeSingleUser: Seq[GithubUser] = Seq(fakeUserA)
  private val fakeTwoUsers: Seq[GithubUser] = Seq(fakeUserA, fakeUserB)

  private val fakeGradedRepositoryOfSingleUser: Seq[GradedRepository] = Seq(fakeGradedRepoA)
  private val fakeGradedRepositoriesOfTwoUsers: Seq[GradedRepository] =
    Seq(fakeGradedRepoA, fakeGradedRepoB, fakeGradedRepoC)

  private val fakeUserToLocation: Map[Int, Location] = Map(
    1 -> Location(country = Some("Russian Federation"), city = Some("Saint Petersburg")),
    2 -> Location(country = None, city = Some("Saint Petersburg"))
  )

}
