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

      "save tags" in { _ =>
        val saveResult = UserController.saveAnalysisResult(fakeTwoUsers, fakeGradedRepositories)
        val _ = Await.result(saveResult, Duration.Inf)

        type T = (String, String, String, String)
        type Container = Vector[T]
        val query: UserController.SQLQuery[Vector[T], T] = sql"""
          SELECT users.github_login, tag_categories.category, tags.tag, tags_users_settings.verified
          FROM users
          INNER JOIN tags_users ON tags_users.user_id = users.id
          INNER JOIN tags ON tags.id = tags_users.tag_id
          INNER JOIN tags_users_settings ON tags_users_settings.id = tags_users.id
          INNER JOIN tag_categories ON tag_categories.id = tags.category_id""".as[T]
        val data: Future[Container] = UserController.runQuery(query)
        val result: Container = Await.result(data, Duration.Inf)

        val trueValue = "t"
        val repo = fakeUserA.repositories.head
        val technologies = fakeGradedRepositories.filter(_.idBase64 == repo.idBase64).head.technologies
        assert(result.contains((fakeUserA.login, "Programming Language", repo.primaryLanguage, trueValue)))
        assert(result.contains((fakeUserA.login, "Technology", technologies.head, trueValue)))
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
    "developer",
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
      languages = Set("JavaScript", "Perl", "C++"),
      technologies = Set("Boost", "MongoDB"),
      grades = Seq(Grade("Maintainable", 0.8)),
      linesOfCode = 100
    )

  private val fakeGradedRepoB: GradedRepository =
    GradedRepository(
      idBase64 = "repoB",
      name = "nameB",
      languages = Set("C", "Python", "C++"),
      technologies = Set("PostgreSQL", "Django"),
      grades = Seq(Grade("Maintainable", 0.9), Grade("Performant", 0.8)),
      linesOfCode = 200
    )

  private val fakeGradedRepoC: GradedRepository =
    GradedRepository(
      idBase64 = "repoC",
      name = "nameC",
      languages = Set("Bash", "Java"),
      technologies = Set("Spring"),
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
  developer BOOLEAN DEFAULT TRUE NOT NULL,
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
  description TEXT NOT NULL
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

CREATE TABLE IF NOT EXISTS tag_categories (
  id SERIAL PRIMARY KEY,
  category_rest_id TEXT UNIQUE NOT NULL,
  category TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS tags (
  id SERIAL PRIMARY KEY,
  category_id INTEGER REFERENCES tag_categories NOT NULL,
  tag TEXT NOT NULL,
  clicked INTEGER DEFAULT 0 NOT NULL,
  UNIQUE (category_id, tag)
);

CREATE TABLE IF NOT EXISTS tags_users (
  id SERIAL PRIMARY KEY,
  tag_id INTEGER REFERENCES tags NOT NULL,
  user_id INTEGER REFERENCES users NOT NULL,
  UNIQUE (tag_id, user_id)
);

CREATE TABLE IF NOT EXISTS tags_users_settings (
  id SERIAL PRIMARY KEY,
  tags_users_id INTEGER UNIQUE REFERENCES tags_users NOT NULL,
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

INSERT INTO tag_categories (
  id,
  category_rest_id,
  category
) VALUES (
  DEFAULT,
  'languages',
  'Programming Language'
);

INSERT INTO tag_categories (
  id,
  category_rest_id,
  category
) VALUES (
  DEFAULT,
  'technologies',
  'Technology'
);

INSERT INTO tags (
  id,
  category_id,
  tag,
  clicked
) VALUES (
  DEFAULT,
  (SELECT id FROM tag_categories WHERE category_rest_id = 'languages'),
  'JavaScript',
  DEFAULT
);

INSERT INTO tags (
  id,
  category_id,
  tag,
  clicked
) VALUES (
  DEFAULT,
  (SELECT id FROM tag_categories WHERE category_rest_id = 'languages'),
  'C++',
  DEFAULT
);

INSERT INTO tags (
  id,
  category_id,
  tag,
  clicked
) VALUES (
  DEFAULT,
  (SELECT id FROM tag_categories WHERE category_rest_id = 'languages'),
  'C',
  DEFAULT
);

INSERT INTO contact_categories (
  id,
  category
) VALUES (
  DEFAULT,
  'Email'
);

INSERT INTO contact_categories (
  id,
  category
) VALUES (
  DEFAULT,
  'Website'
);
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
