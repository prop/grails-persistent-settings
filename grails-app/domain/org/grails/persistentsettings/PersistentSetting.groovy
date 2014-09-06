package org.grails.persistentsettings

import grails.util.Holders

class PersistentSetting {

  /**
   * A name of the setting
   */
  String name

  /**
   * Serialized value (Grails does not persist private fields by default)
   */
  String sValue

  Boolean isHidden

  static transients = [
    'propertyName',
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
      def type = (Class) PersistentSetting.getConfig()[name].type
      if (type == Boolean.class) return sValue == "true"
      def res = sValue.asType(type)
      return res
    } catch (Exception e) {
      return sValue
    }
  }

  public void setValue(Object v) {
    oValue = v
    if (v == null) sValue = null
    else sValue = v.toString()
  }

  static mapping = {
    datasource 'ALL'
    sValue type: 'text'
    sort 'name'
    cache true
  }

  String getPropertyName() {
    return "org.grails.persistentsettings." + name + ".name"
  }

  String getDescription() {
    return "org.grails.persistentsettings." + name + ".description"
  }

  ConfigObject getAdvanced() {
    return PersistentSetting.getConfig()[name].advanced
  }

  Class getType() {
    def type = PersistentSetting.getConfig()[name].type
    if (type.getClass() == Class.class) return type
    return null
  }

  static constraints = {

    name nullable: false, unique: true, validator: { val, obj ->
      // name is invalid if getConfig() does not contain it
      if (!PersistentSetting.getConfig().containsKey(val)) {
        return 'persistentsettings.name.invalid'
      }
      return true
    }

    value nullable: true, bindable: true, validator: { val, obj ->
      if (!PersistentSetting.getConfig().containsKey(obj.name)) {
        return 'name.invalid'
      }

      def s = PersistentSetting.getConfig()[obj.name]
      if (obj.oValue != null && obj.oValue.getClass() != s.type) {
        return "persistentsettings.type.invalid"
      }

      def list = obj.getAdvanced().list
      if (list && list.size() > 0 && obj.value != null &&
        !list.contains(obj.oValue)) {
        return "persistentsettings.value.invalid"
      }

      // Calling custom validator
      if (s.validator != [:]) {
        return s.validator.call(val)
      }
      return true
    }

    sValue nullable: true

    isHidden nullable: true
  }

  static void bootstrap() {
    def originalMapConstructor = PersistentSetting.metaClass.retrieveConstructor(Map);

    PersistentSetting.metaClass.constructor = { Map map ->
      if (map.size() > 0) {
        throw new RuntimeException("Passing params to constructor not supported");
      }
      instance;
    }

    if (!PersistentSetting.getConfig()) return

    (PersistentSetting.getConfig().collect { k, v -> k } - PersistentSetting.list().collect { it.name }).each {
      try {
        def s = PersistentSetting.getConfig()[it]
        def ps = new PersistentSetting();
        ps.name = it;
        ps.value = s.defaultValue;
        ps.isHidden = s.hidden ?: false
        ps.save(failOnError: true, flush: true);
      } catch (Exception e) {
        print "$it, ${PersistentSetting.getConfig()[it]}: " + e.message
      }
    }
  }

  static Object getValue(String name) {
    try {
      return findByName(name, [cache: true]).value
    } catch (NullPointerException e) {
      throw new RuntimeException("Invalid settings key: '$name'")
    }
  }

  static PersistentSetting setValue(String name, Object value) {
    def setting = findByName(name)
    if (!setting) {
      setting = new PersistentSetting();
      setting.name = name;
    }
    setting.value = value
    //setting.save()
    try {
      if (!setting.save(flush: true)) {
        println "Errors: ${setting.errors}"
        //throw new RuntimeException ('Could not save PersistentSetting')
      }
    } catch (Exception e) {
      println "Errors: ${e.message}"
      throw e
    }
    return setting
  }

  static PersistentSetting setValue(String name, String value) {
    def setting = new PersistentSetting()
    def oValue
    if (!PersistentSetting.getConfig().containsKey(name)) {
      setting.errors.reject('persistentsettings.name.invalid')
      return setting
    }
    try {
      def type = PersistentSetting.getConfig()[name].type
      oValue = value.asType(type)
    } catch (Exception e) {
      println "Errors: ${e.message}"
      setting.errors.reject('persistentsettings.type.invalid')
      return setting
    }
    return setValue(name, (Object) oValue)
  }

  static namedQueries = {
    existingOnly {
      'in'("name", PersistentSetting.getConfig().collect { it.key }.toArray())
    }
    visibleOnly {
      'in'("name", PersistentSetting.getConfig().collect { it.key }.toArray())
      eq 'isHidden', false
    }
  }

  private static def getConfig() {
    return Holders.config.persistentSettings;
  }
}

