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
    build(":release:2.2.1") {
      export = false
    }
    runtime ":hibernate:$grailsVersion"
  }

  dependencies {

    // runtime 'mysql:mysql-connector-java:5.1.13'
  }
}
