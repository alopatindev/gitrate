package analysis

import java.net.URL

import analysis.github.{GithubRepository, GithubUser}
import org.scalatest.{Outcome, fixture}

import scala.concurrent.Future

class UserControllerSuite extends fixture.WordSpec {

  import scala.concurrent.Await
  import scala.concurrent.duration.Duration
  import slick.jdbc.PostgresProfile.api._

  "UserControllerSuite" can {

    "saveAnalysisResult" should {

      "process empty input" in { _ =>
        val saveResult = UserController.saveAnalysisResult(Iterable(), Iterable())
        val _ = Await.result(saveResult, Duration.Inf)

        type T = String
        type Container = Vector[T]
        val query: UserController.SQLQuery[Container, T] = sql"""SELECT github_login FROM users""".as[T]
        val users: Future[Container] = UserController.runQuery(query)
        val result: Container = Await.result(users, Duration.Inf)

        assert(result.isEmpty)
      }

      "process single user" in { _ =>
        val saveResult = UserController.saveAnalysisResult(fakeSingleUser, fakeGradedRepositories)
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
        val saveResult = UserController.saveAnalysisResult(fakeTwoUsers, fakeGradedRepositories)
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
        val saveResult = UserController.saveAnalysisResult(fakeTwoUsers, fakeGradedRepositories)
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
        val saveResult = UserController.saveAnalysisResult(fakeTwoUsers, fakeGradedRepositories)
        val _ = Await.result(saveResult, Duration.Inf)

        type T = (String, String, String, String)
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

        val trueValue = "t"
        val user = fakeUserA
        val repo = user.repositories.head

        val languagesToTechnologies: Map[String, Set[String]] =
          fakeGradedRepositories.filter(_.idBase64 == repo.idBase64).head.languageToTechnologies
        assert(languagesToTechnologies.nonEmpty)

        languagesToTechnologies.foreach {
          case (language, technologies) =>
            technologies.foreach { technology =>
              val row = (user.login, language, technology, trueValue)
              assert(result contains row)
            }
        }
      }

      "save repositories" in { _ =>
        val saveResult = UserController.saveAnalysisResult(fakeTwoUsers, fakeGradedRepositories)
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
        val gradedRepo = fakeGradedRepositories.filter(_.idBase64 == repo.idBase64).head
        assert(result.contains((fakeUserB.login, repo.idBase64, repo.name, gradedRepo.linesOfCode.toString)))
      }

      "save grades" in { _ =>
        val saveResult = UserController.saveAnalysisResult(fakeTwoUsers, fakeGradedRepositories)
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
        val gradedRepo = fakeGradedRepositories.filter(_.idBase64 == repo.idBase64).head
        val grade = gradedRepo.grades.head
        val value = BigDecimal(grade.value).setScale(2).toString
        assert(result.contains((fakeUserB.login, grade.gradeCategory, value)))
      }

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
    languages = Seq("Python", "C++"),
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
      languageToTechnologies = Map("JavaScript" -> Set("MongoDB"), "Perl" -> Set(), "C++" -> Set("Boost")),
      grades = Seq(Grade("Maintainable", 0.8)),
      linesOfCode = 100
    )

  private val fakeGradedRepoB: GradedRepository =
    GradedRepository(
      idBase64 = "repoB",
      name = "nameB",
      languageToTechnologies = Map("C" -> Set("PostgreSQL"), "Python" -> Set("Django"), "C++" -> Set()),
      grades = Seq(Grade("Maintainable", 0.9), Grade("Performant", 0.8)),
      linesOfCode = 200
    )

  private val fakeGradedRepoC: GradedRepository =
    GradedRepository(
      idBase64 = "repoC",
      name = "nameC",
      languageToTechnologies = Map("Bash" -> Set(), "Java" -> Set("Spring")),
      grades = Seq(Grade("Maintainable", 0.95), Grade("Performant", 0.77)),
      linesOfCode = 300
    )

  private val fakeUserA = GithubUser(
    id = 1,
    login = "userA",
    repositories = Seq(fakeRepoA),
    fullName = Some("Full Name A"),
    description = Some("description A"),
    location = Some("location A"),
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
    location = Some("location B"),
    company = Some("company B"),
    email = None,
    blog = Some("https://b.domain"),
    jobSeeker = Some(true)
  )

  private val fakeSingleUser: Seq[GithubUser] = Seq(fakeUserA)
  private val fakeTwoUsers: Seq[GithubUser] = Seq(fakeUserA, fakeUserB)
  private val fakeGradedRepositories: Seq[GradedRepository] = Seq(fakeGradedRepoA, fakeGradedRepoB, fakeGradedRepoC)

  case class FixtureParam()

  override def withFixture(test: OneArgTest): Outcome = { // scalastyle:ignore
    val username = "gitrate_test"
    val database = username
    val db: Database =
      Database.forURL(url = s"jdbc:postgresql:$database", user = username, driver = "org.postgresql.Driver")

    val schema = sqlu"""
CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  github_user_id INTEGER UNIQUE NOT NULL,
  github_login TEXT NOT NULL,
  full_name TEXT NOT NULL,
  updated_by_user TIMESTAMP,
  viewed INTEGER DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS developers (
  id SERIAL PRIMARY KEY,
  user_id INTEGER UNIQUE REFERENCES users NOT NULL,
  show_email BOOLEAN,
  job_seeker BOOLEAN NOT NULL,
  available_for_relocation BOOLEAN,
  programming_experience_months SMALLINT,
  work_experience_months SMALLINT,
  description TEXT DEFAULT '' NOT NULL
);

CREATE TABLE IF NOT EXISTS contact_categories (
  id SERIAL PRIMARY KEY,
  category TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS contacts (
  id SERIAL PRIMARY KEY,
  category_id INTEGER REFERENCES contact_categories NOT NULL,
  contact TEXT,
  user_id INTEGER REFERENCES users NOT NULL,
  UNIQUE (category_id, contact)
);

CREATE TABLE IF NOT EXISTS languages (
  id SERIAL PRIMARY KEY,
  language TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS technologies (
  id SERIAL PRIMARY KEY,
  language_id INTEGER REFERENCES languages NOT NULL,
  technology TEXT NOT NULL,
  technology_human_readable TEXT,
  UNIQUE (language_id, technology)
);

CREATE TABLE IF NOT EXISTS technology_synonyms (
  id SERIAL PRIMARY KEY,
  technology_id INTEGER REFERENCES technologies NOT NULL,
  synonym TEXT NOT NULL,
  UNIQUE (technology_id, synonym)
);

CREATE TABLE IF NOT EXISTS technologies_users (
  id SERIAL PRIMARY KEY,
  technology_id INTEGER REFERENCES technologies NOT NULL,
  user_id INTEGER REFERENCES users NOT NULL,
  UNIQUE (technology_id, user_id)
);

CREATE TABLE IF NOT EXISTS technologies_users_settings (
  id SERIAL PRIMARY KEY,
  technologies_users_id INTEGER UNIQUE REFERENCES technologies_users NOT NULL,
  verified BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS repositories (
  id SERIAL PRIMARY KEY,
  raw_id TEXT UNIQUE NOT NULL,
  user_id INTEGER REFERENCES users NOT NULL,
  name TEXT NOT NULL,
  lines_of_code INTEGER NOT NULL,
  updated_by_analyzer TIMESTAMP DEFAULT NOW() NOT NULL
);

CREATE TABLE IF NOT EXISTS grade_categories (
  id SERIAL PRIMARY KEY,
  category TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS grades (
  id SERIAL PRIMARY KEY,
  category_id INTEGER REFERENCES grade_categories NOT NULL,
  value FLOAT NOT NULL,
  repository_id INTEGER REFERENCES repositories NOT NULL,
  UNIQUE (category_id, repository_id)
)"""

    val initialData = sqlu"""
INSERT INTO grade_categories (id, category) VALUES
  (DEFAULT, 'Maintainable'),
  (DEFAULT, 'Testable'),
  (DEFAULT, 'Robust'),
  (DEFAULT, 'Secure'),
  (DEFAULT, 'Automated'),
  (DEFAULT, 'Performant');

INSERT INTO contact_categories (id, category) VALUES
  (DEFAULT, 'Email'),
  (DEFAULT, 'Website');

INSERT INTO languages (id, language) VALUES
  (DEFAULT, 'JavaScript'),
  (DEFAULT, 'Python'),
  (DEFAULT, 'Java'),
  (DEFAULT, 'Bash'),
  (DEFAULT, 'C++'),
  (DEFAULT, 'C');
"""

    val dropData = sqlu"DROP OWNED BY gitrate_test" // FIXME: is there a correct way to unhardcode username here?
    val result = db.run(DBIO.seq(dropData, schema, initialData).transactionally)
    Await.result(result, Duration.Inf)

    val theFixture = FixtureParam()
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {
      db.close()
    }
  }

}
