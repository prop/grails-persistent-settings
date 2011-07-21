package org.grails.persistentsettings
import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH

class PersistentSetting {
    String name
    Class type
    Object value
    
    static transients = ['customValidator', 'propertyName']
    
    Closure customValidator = {return true}
    
    String propertyName

    String getPropertyName() {
        return "persistentSettings." + name
    }
    
    
    static constraints = {
        customValidator nullable: true
        name unique: true, validator: { val, obj ->
            if (!CH.config.persistentSettings.containsKey(val)) {
                return 'setting.invalid.name'
            }
            return true
        }
        value nullable: true, validator: { val, obj ->
            if (val.getClass() != obj.type) {
                return "setting.invalid.type"
            }
            if (obj.customValidator != null) {
                return obj.customValidator.call(val)
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
                                  type: s.type,
                                  value: s.defaultValue,
                                  customValidator: s.validator
                                 ).save(failOnError: true, flush: true)
        }
    }

    static Object getValue (String n) {
        return findByName(n).value
    }

}
