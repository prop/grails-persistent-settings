package org.grails.persistentsettings
import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH

class PersistentSetting {
    String name
    Object value
    
    static transients = ['propertyName']
    
    String propertyName

    String getPropertyName() {
        return "persistentSettings." + name
    }
    
    
    static constraints = {

        name unique: true, validator: { val, obj ->
            if (!CH.config.persistentSettings.containsKey(val)) {
                return 'setting.invalid.name'
            }
            return true
        }
        value nullable: true, validator: { val, obj ->
            def s = CH.config.persistentSettings[obj.name]
            if (val.getClass() != s.type) {
                return "setting.invalid.type"
            }
            if (s.validator != [:]) {
                return s.validator.call(val)
            }
            return true
        }
    }

    static void bootstrap () {
        if (!CH.config.persistentSettings) return
        
        (CH.config.persistentSettings.collect{k,v -> k} -
         PersistentSetting.list().collect{it.name}).each{
            def s = CH.config.persistentSettings[it]
            new PersistentSetting(name: it,
                                  value: s.defaultValue,
                                 ).save(failOnError: true, flush: true)
        }
    }

    static Object getValue (String n) {
        try {
            return findByName(n).value
        } catch (NullPointerException e) {
            throw new Exception ('Invalid settings key')
        }
    }

}
