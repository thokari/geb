/* Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package geb.spock

import geb.report.ReportState
import geb.report.Reporter
import geb.report.ReportingListener
import geb.test.CallbackHttpServer
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.Result
import org.spockframework.runtime.ConditionNotSatisfiedError
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll
import spock.util.EmbeddedSpecRunner

class GebReportingSpecSpec extends Specification {

    final static String REPORTING_SPEC_NAME = "GebReportingExtendingSpec"

    EmbeddedSpecRunner specRunner = new EmbeddedSpecRunner(throwFailure: false)

    @Rule
    TemporaryFolder tempDir

    @Shared
    @AutoCleanup("stop")
    def server = new CallbackHttpServer()

    def setupSpec() {
        server.start()
        server.html {
            body {
                div "Test page"
            }
        }
    }

    def setup() {
        specRunner.addClassImport(GebReportingSpec)
        specRunner.addClassImport(Unroll)
        specRunner.addClassImport(Retry)
        specRunner.addClassImport(Reporter)
        specRunner.addClassImport(ReportingListener)
        specRunner.addClassImport(ReportState)
    }

    File getReportDir() {
        new File(tempDir.root, "reports")
    }

    File getReportGroupDir() {
        new File(reportDir, REPORTING_SPEC_NAME)
    }

    File reportFile(String name) {
        new File(reportGroupDir, name)
    }

    def "report named after the test and which contains response text is made at the end of a test"() {
        when:
        runReportingSpec """
            def "a request is made"() {
                given:
                go "/"
            }
        """

        then:
        reportFile("001-001-a request is made-end.html").text.startsWith("<?xml")
    }

    def "report is written after each test"() {
        when:
        runReportingSpec """
            def "first test"() {
                given:
                go "/"
            }

            def "second test"() {
                given:
                go "/"
            }
        """

        then:
        reportFile("001-001-first test-end.html").exists()
        reportFile("002-001-second test-end.html").exists()
    }

    def "report is not written after a successful test when reporting on failure only is enabled"() {
        when:
        runReportingSpec """
            def "passing test"() {
                given:
                config.reportOnTestFailureOnly = true
                go "/"

                expect:
                true
            }
        """

        then:
        !reportGroupDir.listFiles()
    }

    def "report is written after a failing test when reporting on failure only is enabled"() {
        when:
        runReportingSpec """
            def "failing test"() {
                given:
                config.reportOnTestFailureOnly = true
                go "/"

                expect:
                false
            }
        """

        then:
        reportFile("001-001-failing test-failure.html").exists()
    }

    def "report is written after a failing unrolled test when reporting on failure only is enabled"() {
        when:
        runReportingSpec """
            @Unroll
            def "failing test"() {
                given:
                config.reportOnTestFailureOnly = true
                go "/"

                expect:
                false

                where:
                parameter << [0]
            }
        """

        then:
        reportFile("001-001-failing test_0_-failure.html").exists()
    }

    @Unroll
    def "report is written after every failure when using @Retry with #mode"() {
        when:
        runReportingSpec """
            @spock.lang.Shared currentTry = 1

            @Retry(count = 1, mode = $mode)
            def "failing test"() {
                given:
                println "current try: " + currentTry++
                config.reportOnTestFailureOnly = true
                go "/"
                
                expect:
                false
            }
        """

        then:
        println reportGroupDir.list()
        reportFileNumbers.every {
            reportFile("${it}-failing test-failure.html").exists()
        }

        where:
        mode                               | reportFileNumbers
        'Retry.Mode.ITERATION'             | ['001-001', '001-002']
        'Retry.Mode.FEATURE'               | ['001-001', '001-002']
        'Retry.Mode.SETUP_FEATURE_CLEANUP' | ['001-001', '001-002']
    }

    @Unroll
    def "report is written after every failure in a data driven test when using @Retry with #mode"() {
        when:
        runReportingSpec """
            @Retry(count = 1, mode = $mode)
            def "failing data driven test"() {
                given:
                config.reportOnTestFailureOnly = true
                go "/"

                expect:
                fail == false

                where:
                fail << [true, true]
            }
        """

        then:
        reportFileNumbers.every {
            reportFile("${it}-failing data driven test-failure.html").exists()
        }

        where:
        mode                               | reportFileNumbers
        'Retry.Mode.ITERATION'             | ['001-001', '001-002', '002-001', '002-002']
        'Retry.Mode.FEATURE'               | ['001-001', '001-002', '002-001', '002-002']
        'Retry.Mode.SETUP_FEATURE_CLEANUP' | ['001-001', '001-002', '002-001', '002-002']
    }

    @Unroll
    def "report is written after every failure when using @Stepwise and @Retry with #mode"() {
        when:
        runStepwiseReportingSpec """
            @Retry(count = 1, mode = $mode)
            def "failing stepwise test"() {
                given:
                config.reportOnTestFailureOnly = true
                go "/"

                expect:
                false
            }
        """

        then:
        reportFileNumbers.every {
            reportFile("${it}-failing stepwise test-failure.html").exists()
        }

        where:
        mode                               | reportFileNumbers
        'Retry.Mode.ITERATION'             | ['001-001', '001-002']
        'Retry.Mode.FEATURE'               | ['001-001', '001-002']
        'Retry.Mode.SETUP_FEATURE_CLEANUP' | ['001-001', '001-002']
    }

    @Unroll
    def "report is written after ever failure in a data driven test when using @Stepwise and @Retry with #mode"() {
        when:
        runStepwiseReportingSpec """
            @Retry(count = 1, mode = $mode)
            def "failing stepwise data driven test"() {
                given:
                config.reportOnTestFailureOnly = true
                go "/"

                expect:
                fail == false

                where:
                fail << [true, true]
            }
        """

        then:
        reportFileNumbers.every {
            reportFile("${it}-failing stepwise data driven test-failure.html").exists()
        }

        where:
        mode                               | reportFileNumbers
        'Retry.Mode.ITERATION'             | ['001-001', '001-002', '002-001', '002-002']
        'Retry.Mode.FEATURE'               | ['001-001', '001-002', '002-001', '002-002']
        'Retry.Mode.SETUP_FEATURE_CLEANUP' | ['001-001', '001-002', '002-001', '002-002']
    }

    def "failure when writing a report does not overwrite the original test failure"() {
        when:
        def result = runReportingSpec """
            def "failing test"() {
                given:
                config.reportOnTestFailureOnly = true
                config.reporter = new Reporter() {
                    void writeReport(ReportState reportState) {
                        throw new Exception()
                    }

                    void addListener(ReportingListener listener) {
                    }
                }

                go "/"

                expect:
                false
            }
        """

        then:
        result.failures.first().exception in ConditionNotSatisfiedError
    }

    def "report called from fixture method should create report with default name"() {
        when:
        specRunner.run """
        class $REPORTING_SPEC_NAME extends GebReportingSpec {

            def setupSpec() {
                ${configuration}

                go "/"
                report('Report in setupSpec')
            }

            def "passing test"() {
                expect:
                true
            }
        }
        """

        then:
        reportFile("001-000-fixture-Report in setupSpec.html").text.startsWith("<?xml")
        reportFile("001-001-passing test-end.html").text.startsWith("<?xml")
    }

    Result runReportingSpec(String body) {
        specRunner.run """
            class $REPORTING_SPEC_NAME extends GebReportingSpec {

                def setup() {
                    ${configuration}
                }

                $body
            }
        """
    }

    Result runStepwiseReportingSpec(String body) {
        specRunner.addClassImport(Stepwise)
        specRunner.run """
            @Stepwise
            class $REPORTING_SPEC_NAME extends GebReportingSpec {

                def setup() {
                    ${configuration}
                }

                $body
            }
        """
    }

    private String getConfiguration() {
        """
        baseUrl = "${server.baseUrl}"
        config.rawConfig.reportsDir = "${reportDir.absolutePath.replaceAll("\\\\", "\\\\\\\\")}"
        """
    }
}
