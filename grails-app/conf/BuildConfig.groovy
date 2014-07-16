grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"

grails.project.dependency.resolution = {
  inherits("global") {
    // uncomment to disable ehcache
    // excludes 'ehcache'
  }
  log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
  repositories {

    grailsRepo "http://grails.org/plugins"
    grailsHome()
    grailsCentral()

  }

  plugins {
    runtime ":hibernate:$grailsVersion"
    runtime ":maven-publisher:0.8.1"

    build ":tomcat:$grailsVersion"
  }

  dependencies {

    // runtime 'mysql:mysql-connector-java:5.1.13'
  }
}
