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
    
    static transients = ['propertyName',
                         'description',
                         'value',
                         'oValue',
                         'type',
                         'advanced'
                        ]
    
                        /**
                         * Name of the property for i18n
                         */
                        String propertyName

                        /**
                         * A copy of the original value
                         */
                        Object oValue = null
    
                        Object value

                        public Object getValue() {
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

                        public void setValue(Object v) {
                            oValue = v
                            if (v == null) sValue = null
                            else sValue = v.toString()
                        }

    static mapping = {
        sValue  type: 'text'
    }
    
    String getPropertyName() {
        return "org.grails.persistentsettings." + name + ".name"
    }
    
    String getDescription() {
        return "org.grails.persistentsettings." + name + ".description"
    }

    ConfigObject getAdvanced() {
        return configObject[name].advanced
    }
    
    Class getType() {
        def type = configObject[name].type
        if (type.getClass() == Class.class) return type
        return null
    }
    private static final ConfigObject configObject = CH.config.persistentSettings
    
    static constraints = {

        name nullable: false, unique: true, validator: { val, obj ->
            // name is invalid if configObject does not contain it
            if (!configObject.containsKey(val)) {
                return 'persistedsetting.name.invalid'
            }
            return true
        }
        
        value nullable: true, bindable: true, validator: { val, obj ->
            if (!configObject.containsKey(obj.name)) {
                return 'persistedsetting.name.invalid'
            }
            
            def s = configObject[obj.name]
            if (obj.oValue != null && obj.oValue.getClass() != s.type) {
                return "persistedsetting.type.invalid"
            }
            
            def list = obj.getAdvanced().list
            if (list && list.size() > 0 && obj.value != null &&
                !list.contains(obj.oValue)) {
                return "persistedsetting.value.invalid"
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
        def originalMapConstructor = PersistentSetting.metaClass.retrieveConstructor(Map);

        PersistentSetting.metaClass.constructor = { Map map ->
            if (map.size() > 0) {
                throw new RuntimeException("Passing params to constructor not supported");
            }
            instance;
        }
        
        if (!configObject) return
        (configObject.collect{k,v -> k} -
         PersistentSetting.list().collect{it.name}).each{
            def s = configObject[it]
            def ps = new PersistentSetting();
            ps.name = it;
            ps.value = s.defaultValue;
            ps.save(failOnError: true, flush: true);
        }
    }

    static Object getValue (String name) {
        try {
            return findByName(name).value
        } catch (NullPointerException e) {
            throw new RuntimeException ('Invalid settings key')
        }
    }

    static PersistentSetting setValue (String name, Object value) {
        def setting = findByName(name)
        if (!setting) {
            setting = new PersistentSetting();
            setting.name = name;
            setting.value = value;
        }
        else setting.value = value
        setting.save()
        // if (!setting.save(flush: true)) {
        //     println "Erors: ${setting.errors}"
        // throw new RuntimeException ('Could not save PersistentSetting')
        // }
        return setting
    }

    static PersistentSetting setValue (String name, String value) {
        def setting = new PersistentSetting()
        def oValue
        if (!configObject.containsKey(name)) {
            setting.errors.reject('persistedsetting.name.invalid')
            return setting
        }
        try {
            def type = configObject[name].type
            oValue = value.asType(type)
        } catch (Exception e) {
            setting.errors.reject('persistedsetting.type.invalid')
            return setting
        }
        return setValue(name, (Object) oValue)
    }
}

