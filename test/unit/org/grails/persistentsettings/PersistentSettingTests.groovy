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
        
        assert PersistentSetting.getSettingValue("foo") == 1
        assert PersistentSetting.getSettingValue("bar") == "blablabla"
        assert PersistentSetting.getSettingValue("trueSetting") == true
        assert PersistentSetting.getSettingValue("falseSetting") == false
    }
    
    void testInvalidSetting() {
        mockDomain(PersistentSetting)
        def s = new PersistentSetting(
            name: "someInvalidName",
            value: "somevalue",
            defaultValue: "somevalue",
            type: String.class
        )
        assert s.save(flush: true) == null
        
        assert (new PersistentSetting(
            name: "foo",
            value: "somevalue",
            defaultValue: "somevalue",
            type: Integer.class
                )).save(flush: true) == null
    }
}
