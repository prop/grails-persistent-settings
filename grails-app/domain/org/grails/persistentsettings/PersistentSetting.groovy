package org.grails.persistentsettings
import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH
class PersistentSetting {
    /**
     * A name of the setting
     */
    String name
    
    /**
     * Serialized value (Grails does not persist private fields by default)
     */
    String sValue
    
    static transients = ['propertyName', 'description', 'value', 'oValue']
    
    /**
     * Name of the property for i18n
     */
    String propertyName

    /**
     * A copy of the original value
     */
    Object oValue = null
    
    Object value
    
    
    Object getValue() {
        if (name == null || sValue == null) return null
        if (oValue != null) return oValue
        try {
            def type = (Class) configObject[name].type
            if (type == Boolean.class) return sValue == "true"
            def res = sValue.asType(type)
            return res
        } catch(Exception e) {
            return sValue
        }
    }

    void setValue(Object v) {
        oValue = v
        if (v == null) sValue = null
        else sValue = v.toString()
    }
    
    String getPropertyName() {
        return "org.grails.persistentsettings." + name + ".name"
    }
    
    String getDescription() {
        return "org.grails.persistentsettings." + name + ".description"
    }
    
    private static ConfigObject configObject = CH.config.persistentSettings
    
    static constraints = {

        name nullable: false, unique: true, validator: { val, obj ->
            // name is invalid if configObject does not contain it
            if (!configObject.containsKey(val)) {
                return 'persistedsetting.name.invalid'
            }
            return true
        }
        value nullable: true, validator: { val, obj ->
            def s = configObject[obj.name]
            if (obj.oValue != null && obj.oValue.getClass() != s.type) {
                return "persistedsetting.type.invalid"
            }
            // Calling custom validator
            if (s.validator != [:]) {
                return s.validator.call(val)
            }
            return true
        }
        
        sValue nullable: true
    }
    
    static void bootstrap () {
        if (!configObject) return
        
        (configObject.collect{k,v -> k} -
         PersistentSetting.list().collect{it.name}).each{
            def s = configObject[it]
            new PersistentSetting(name: it,
                                  value: s.defaultValue,
                                 ).save(failOnError: true, flush: true)
        }
    }

    static Object getValue (String name) {
        try {
            return findByName(name).value
        } catch (NullPointerException e) {
            throw new RuntimeException ('Invalid settings key')
        }
    }

    static Object setValue (String name, Object value) {
            def setting = findByName(name)
            if (!setting) {
                setting = new PersistentSetting(name: name, value: value)
            }
            else setting.value = value
            if (!setting.save(flush: true)) {
                println "Errors: ${setting.errors}"
                throw new RuntimeException ('Could not save PersistentSetting')
            }
    }

}
