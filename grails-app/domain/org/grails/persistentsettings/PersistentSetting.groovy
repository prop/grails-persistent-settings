package org.grails.persistentsettings
import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH

class PersistentSetting {
    String name
    Object value
    
    static transients = ['propertyName', 'description']
    
    String propertyName

    String getPropertyName() {
        return "org.grails.persistentsettings." + name + ".name"
    }
    
    String getDescription() {
        return "org.grails.persistentsettings." + name + ".description"
    }
    
    
    static constraints = {

        name unique: true, validator: { val, obj ->
            if (!CH.config.persistentSettings.containsKey(val)) {
                return 'persistedsetting.name.invalid'
            }
            return true
        }
        value nullable: true, validator: { val, obj ->
            def s = CH.config.persistentSettings[obj.name]
            if (val != null && val.getClass() != s.type) {
                return "persistedsetting.type.invalid"
            }
            if (s.validator != [:]) {
                return s.validator.call(val)
            }
            return true
        }
    }

    static void bootstrap () {
        def ps = CH.config.persistentSettings
        if (!ps) return
        
        (ps.collect{k,v -> k} -
         PersistentSetting.list().collect{it.name}).each{
            def s = ps[it]
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
