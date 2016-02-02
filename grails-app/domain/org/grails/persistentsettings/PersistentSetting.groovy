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

  String module

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

      def list = obj.getAdvanced()?.list
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
    module nullable: true
  }

  static List<PersistentSetting> firstCleanBootstrap(String moduleName) {
    deleteStalePSFromDomain(moduleName)
    return doBootstrap(getConfig(), moduleName)
  }

  private static final Object psStorageSyncObj = new Object()

  static List<PersistentSetting> reCleanBootstrap(ConfigObject config, String moduleName) {
    if (!moduleName) {
      throw IllegalArgumentException(
          "Module name was not specified while trying to clean-boostrap PersistentSettings")
    }

    if (config) {
      synchronized (psStorageSyncObj) {
        deleteStalePSFromDomain(moduleName)

        addToConfig(config, moduleName)
        List<PersistentSetting> bootstrapped = doBootstrap(config, moduleName, true)

        deleteOtherConfigs(bootstrapped, moduleName)
        return bootstrapped
      }
    } else {
      return []
    }
  }

  private static List<ConfigObject> deleteOtherConfigs(List<PersistentSetting> recentBootstrapped, String moduleName) {
    def namesOfPsToDelete = getConfig().collect({ k, v -> k }) - recentBootstrapped.collect({ it.name })
    return deleteFromLocalConfig(moduleName, namesOfPsToDelete)
  }

  private static List deleteFromLocalConfig(String moduleName, List namesOfPsToDelete) {
    def iterator = getConfig().entrySet().iterator()

    List deleted = []
    while (iterator.hasNext()) {
      def val = iterator.next().value
      if (val.module == moduleName && val.name in namesOfPsToDelete) {
        iterator.remove()
        deleted.add(val)
      }
    }

    return deleted
  }

  static ConfigObject addToConfig(ConfigObject configs, String module) {
    def clonedConfigs = configs.clone()
    clonedConfigs.each { k, v -> v.module = module }
    getConfig() << clonedConfigs
    clonedConfigs
  }

  /**
   * Deletes all PersistentSettings that have been stored in a DB by a module name.
   * @param moduleName Nullable.
   * @return
   * List of deleted entities. Nullable
   */
  private static def deleteStalePSFromDomain(String moduleName = null) {
    if (moduleName) {
      PersistentSetting.executeUpdate("delete from PersistentSetting ps where ps.module=:moduleName",
          [moduleName: moduleName])
    } else {
      PersistentSetting.executeUpdate("delete from PersistentSetting")
    }
  }

  static void bootstrap() {
    def originalMapConstructor = PersistentSetting.metaClass.retrieveConstructor(Map);

    PersistentSetting.metaClass.constructor = { Map map ->
      if (map.size() > 0) {
        throw new RuntimeException("Passing params to constructor not supported");
      }
      instance;
    }

    def configs = getConfig()
    doBootstrap(configs)
  }

  protected static List<PersistentSetting> doBootstrap(configs,
                                                       String moduleName = null, boolean failOnError = false) {
    if (!configs) return

    List<PersistentSetting> created = []

    def allPs = PersistentSetting.list()
    if (moduleName) {
      allPs = allPs.findAll({
        it.module == moduleName
      })
    }
    (configs.collect { k, v -> k } - allPs.collect { it.name }).each {
      try {
        def s = configs[it]

        def name = it
        PersistentSetting ps = toPersistentSetting(name, s, moduleName ?: s.module)
        ps.save(failOnError: true, flush: true);
        created.add(ps)
      } catch (Exception e) {
        print "$it, ${configs[it]}: " + e.message
        if (failOnError) {
          throw e
        }
      }
    }

    return created
  }

  private static PersistentSetting toPersistentSetting(String name, config, String module) {
    def ps = new PersistentSetting();
    ps.name = name;
    ps.value = config.defaultValue;
    ps.module = module
    ps.isHidden = config.hidden ?: false
    ps
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
      'in'("name", getConfig().collect { it.key }.toArray())
      or {
        eq 'isHidden', false
        isNull 'isHidden'
      }
    }
    existingAndSafeOnly {
      'in'("name", PersistentSetting.getConfig().findAll { it.value.safe == true }.collect { it.key }.toArray())
    }
  }

  private static def getConfig() {
    return Holders.config.persistentSettings;
  }
}

