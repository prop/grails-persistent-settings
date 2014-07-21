package org.grails.persistentsettings

import grails.util.Environment
import grails.test.*
import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH


class PersistentSettingTests extends GrailsUnitTestCase {
  def testConfig = new ConfigSlurper().parse(new File('grails-app/conf/TestConfig.groovy').toURL())

  private void loadConfig() {
    GroovyClassLoader classLoader = new GroovyClassLoader(this.class.classLoader)
    ConfigSlurper slurper = new ConfigSlurper(Environment.current.name)
    CH.config = slurper.parse(classLoader.loadClass("TestConfig"))
  }

  protected void setUp() {
    loadConfig();
    super.setUp()
  }

  protected void tearDown() {
    super.tearDown()
  }

  void testParseConfig() {
    def persistentSettings = CH.config.persistentSettings
    assert persistentSettings.foo.defaultValue == 1
    assert persistentSettings.foo.type == Integer.class
    assert persistentSettings.bar.validator(persistentSettings.bar.defaultValue) == true
  }

  void testInit() {
    mockDomain(PersistentSetting)
    PersistentSetting.bootstrap()

    assert PersistentSetting.list().size() == 5
  }

  void testTypes() {
    mockDomain(PersistentSetting)
    PersistentSetting.bootstrap()

    assert PersistentSetting.getValue("foo") == 1
    assert PersistentSetting.getValue("bar") == "blablabla"
    assert PersistentSetting.getValue("trueSetting") == true
    assert PersistentSetting.getValue("falseSetting") == false
    assert PersistentSetting.findByName("foo").propertyName == "org.grails.persistentsettings.foo.name"
    assert PersistentSetting.findByName("foo").description == "org.grails.persistentsettings.foo.description"
    assert PersistentSetting.getValue("listSetting") == "first"
  }

  void testConstraints() {
    def testInstances = []
    mockForConstraintsTests(PersistentSetting, testInstances)
    def item = new PersistentSetting();

    item.name = "someInvalidName";
    item.value = "somevalue";

    assertFalse item.validate()
    assertEquals item.errors['name'], "persistentsettings.name.invalid"

    item = new PersistentSetting();
    item.name = "foo";
    item.value = "somevalue";

    assertFalse item.validate()
    assertEquals item.errors['value'], "persistentsettings.type.invalid"

    item = new PersistentSetting();
    item.name = "trueSetting";
    item.value = 7;

    assertFalse item.validate()
    assertEquals item.errors['value'], "persistentsettings.type.invalid"

    item = new PersistentSetting();
    item.name = "foo";
    item.value = 2;
    assertTrue item.validate();

    item = new PersistentSetting();
    item.name = "foo";
    item.value = null;

    assertTrue item.validate()

    item = new PersistentSetting();
    item.name = "listSetting";
    item.value = "second";
    assertTrue item.validate()

    item = new PersistentSetting();
    item.name = "listSetting";
    item.value = "invalid";
    assertFalse item.validate()
  }

  void testSetting() {
    mockDomain(PersistentSetting)
    PersistentSetting.bootstrap()
    assertFalse PersistentSetting.setValue("foo", 42).hasErrors()
    assertEquals PersistentSetting.getValue("foo"), 42

    assertFalse PersistentSetting.setValue("trueSetting", false).hasErrors()
    assertEquals PersistentSetting.getValue("trueSetting"), false

    def ok = false, value = null

    try {
      value = PersistentSetting.getValue("invalidSetting")
    } catch (RuntimeException e) {
      ok = true
    }
    assertTrue ok

    assertTrue PersistentSetting.setValue("invalidSetting", "somevalue").hasErrors()

    assertTrue PersistentSetting.setValue("foo", "someString").hasErrors()

    def err = PersistentSetting.setValue("listSetting", "third").errors
    println "Error: ${err}"


    assertEquals PersistentSetting.getValue("listSetting"), "third"

    assertTrue PersistentSetting.setValue("listSetting", "fifth").hasErrors()
  }

}
