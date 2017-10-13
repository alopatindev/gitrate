package analysis

import java.net.URL

import analysis.github.{GithubRepo, GithubUser}
import org.scalatest.WordSpec

class DatabaseQueriesSuite extends WordSpec {

  "DatabaseQueries" can {

    "buildAnalysisResultQuery" should {

      "process empty input" in {
        val result = DatabaseQueries.buildAnalysisResultQuery(Iterable(), Iterable())
        assert(result.isEmpty)
      }

      "process single user" in {
        val result = DatabaseQueries.buildAnalysisResultQuery(fakeSingleUser, fakeGradedRepositories)
        val expected = ???
        assert(strip(result) === strip(expected))
      }

      "process multiple users" ignore {
        val result = DatabaseQueries.buildAnalysisResultQuery(fakeSingleUser, fakeGradedRepositories)
        val expected = ???
        assert(strip(result) === strip(expected))
      }

    }

  }

  private def strip(text: String): String = text.filterNot(_ == " ")

  private val fakeRepoA: GithubRepo = GithubRepo(
    idBase64 = "repoA",
    name = "nameA",
    primaryLanguage = "JavaScript",
    languages = Seq("Perl", "C++"),
    "developer",
    new URL("http://path.to/developer/archive.tar.gz"),
    1.0
  )

  private val fakeRepoB: GithubRepo = GithubRepo(
    idBase64 = "repoB",
    name = "nameB",
    primaryLanguage = "C",
    languages = Seq("Python", "C++"),
    defaultBranch = "master",
    archiveURL = new URL("http://path.to/master/archive.tar.gz"),
    ownerToAllCommitsRatio = 0.8
  )

  private val fakeRepoC: GithubRepo = GithubRepo(
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
      grades = Seq(Grade("Maintaiable", 0.8)),
      linesOfCode = 100
    )

  private val fakeGradedRepoB: GradedRepository =
    GradedRepository(
      idBase64 = "repoB",
      name = "nameB",
      languages = Set("C", "Python", "C++"),
      technologies = Set("PostgreSQL", "Django"),
      grades = Seq(Grade("Maintaiable", 0.9), Grade("Performant", 0.8)),
      linesOfCode = 200
    )

  private val fakeGradedRepoC: GradedRepository =
    GradedRepository(
      idBase64 = "repoC",
      name = "nameC",
      languages = Set("Bash", "Java"),
      technologies = Set("Spring"),
      grades = Seq(Grade("Maintaiable", 0.95), Grade("Performant", 0.77)),
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
    email = Some("b@domain.com"),
    blog = Some("https://b.domain"),
    jobSeeker = Some(true)
  )

  private val fakeSingleUser: Seq[GithubUser] = Seq(fakeUserA)
  private val fakeTwoUsers: Seq[GithubUser] = Seq(fakeUserA, fakeUserB)
  private val fakeGradedRepositories: Seq[GradedRepository] = Seq(fakeGradedRepoA, fakeGradedRepoB, fakeGradedRepoC)

}
