import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.Swabra
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.swabra
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.MavenBuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2021.1"

project {
    vcsRoot(VerademoVcs)
    buildType(Build)
}

object Build : BuildType({
    name = "Build"
    artifactRules = """app\target\verademo.war => verademo.war"""

    vcs {
        root(VerademoVcs)
    }
    steps {
        maven {
            goals = "clean test package verify"
            pomLocation = "app/pom.xml"
            runnerArgs = "-Dmaven.test.failure.ignore=true"
            localRepoScope = MavenBuildStep.RepositoryScope.MAVEN_DEFAULT
        }
        script {
            scriptContent =  """
                curl  https://downloads.veracode.com/securityscan/pipeline-scan-LATEST.zip -o pipeline-scan.zip
                Expand-Archive -Path pipeline-scan.zip -DestinationPath veracode_scanner
                java -jar veracode_scanner\\pipeline-scan.jar --veracode_api_id '%env.VERACODE_API_ID' \
                    --veracode_api_key '%env.VERACODE_API_KEY' \
                    --file target/verademo.war --issue_details true
            """
        }
        step {
            type = "teamcity-veracode-plugin"
            param("uploadIncludePattern", "**/**.war")
            param("appName", "%env.TEAMCITY_PROJECT_NAME%")
            param("createProfile", "true")
            param("criticality", "VeryHigh")
            param("waitForScan", "false")
            param("sandboxName", "TeamCity")
            param("useGlobalCredentials", "true")
            param("createSandbox", "true")
            param("version", "%env.BUILD_NUMBER%")
        }
    }
    triggers {
        vcs {
            groupCheckinsByCommitter = true
        }
    }
})

object VerademoVcs : GitVcsRoot({
    name = "VerademoVcs"
    url = "https://github.com/tfahey/Verademo.git"
    branch = "refs/heads/master"
    branchSpec = "refs/heads/*"
})


fun wrapWithFeature(buildType: BuildType, featureBlock: BuildFeatures.() -> Unit): BuildType {
    buildType.features {
        featureBlock()
    }
    return buildType
}