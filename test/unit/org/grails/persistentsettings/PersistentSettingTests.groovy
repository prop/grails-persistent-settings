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
        
        assert PersistentSetting.list().size() == 4
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
    }

    void testConstraints() {
        def testInstances = []
        mockForConstraintsTests(PersistentSetting, testInstances)
        def item = new PersistentSetting(
            name: "someInvalidName",
            value: "somevalue"
        )
        assertFalse item.validate()
        assertEquals item.errors['name'], "persistedsetting.name.invalid"
        item = new PersistentSetting(
            name: "foo",
            value: "somevalue"
        )
        assertFalse item.validate()
        assertEquals item.errors['value'], "persistedsetting.type.invalid"

        item = new PersistentSetting(
            name: "foo",
            value: 2
        )
        assertTrue item.validate()

        
    }
    
    void testInvalidSetting() {
        
        mockDomain(PersistentSetting)
        
    }
}
