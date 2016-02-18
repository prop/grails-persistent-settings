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
  private static final String MODULE_NAME_SEPARATOR = ':'

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
      def type = (Class) PersistentSetting.getConfig()[getSettingFullName(name, module)].type
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
    def fullName = getSettingFullName(name, module)
    return PersistentSetting.getConfig()[fullName].advanced
  }

  Class getType() {
    def type = PersistentSetting.getConfig()[getSettingFullName(name, module)].type
    if (type.getClass() == Class.class) return type
    return null
  }

  static constraints = {

    name nullable: false, unique: 'module', validator: { val, obj ->
      // name is invalid if getConfig() does not contain it
      if (!PersistentSetting.getConfig().containsKey(getSettingFullName(val, obj.module))) {
        return 'persistentsettings.name.invalid'
      }
      return true
    }

    value nullable: true, bindable: true, validator: { val, obj ->
      def configFullName = getSettingFullName(obj.name, obj.module)
      if (!PersistentSetting.getConfig().containsKey(configFullName)) {
        return 'name.invalid'
      }

      def s = PersistentSetting.getConfig()[configFullName]
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

  static List<PersistentSetting> firstCleanBootstrap(String moduleName = null) {
    PersistentSetting.executeUpdate("delete from PersistentSetting ps")
    return doBootstrap(getConfig(), moduleName)
  }

  private static final Object psStorageSyncObj = new Object()

  static List<PersistentSetting> reCleanBootstrap(ConfigObject config, String moduleName) {
    if (config != null && moduleName != null) {
      synchronized (psStorageSyncObj) {
        addNewConfigs(config, moduleName)
        doBootstrap(config, moduleName)

        List<PersistentSetting> actualPs = PersistentSetting.findAllByModuleAndNameInList(moduleName,
            config.collect { it.key })
        deleteOtherConfigs(actualPs, moduleName)
        return actualPs
      }
    } else {
      return []
    }
  }

  static ConfigObject addNewConfigs(ConfigObject configToLoad, String module) {
    def config = getConfig()
    configToLoad.each { String k, v ->
      def fullName = getSettingFullName(k, module)

      if (!config[fullName]) {
        v.module = module
        config.putAt(fullName, v)
      }
    }

    configToLoad
  }

  private static def deleteOtherConfigs(List<PersistentSetting> actualPs, String moduleName) {
    def fullNamesOfPsToDelete =
        getConfig().collect({ k, v -> k }).findAll({it.toString().endsWith(MODULE_NAME_SEPARATOR + moduleName)}) -
        actualPs.collect({ getSettingFullName(it.name, moduleName) })

    def deletedConfigs = deleteFromLocalConfig(fullNamesOfPsToDelete)
    if (deletedConfigs) {
      PersistentSetting.executeUpdate("delete from PersistentSetting ps " +
          "where ps.name in :deletedConfigNames and ps.module=:module",
          [deletedConfigNames: deletedConfigs.collect {k, v -> getSettingOriginName(k.toString())}, module: moduleName])
    }
    return deletedConfigs
  }

  private static String getSettingOriginName(String fullName){
    if (fullName) {
      int separatorIndex = fullName.indexOf(MODULE_NAME_SEPARATOR)
      if (separatorIndex != -1) {
        return fullName.substring(0, separatorIndex)
      }
    }
    return fullName
  }

  private static Map deleteFromLocalConfig(List fullNamesOfPsToDelete) {
    def iterator = getConfig().entrySet().iterator()

    Map deleted = [:]
    while (iterator.hasNext()) {
      def iter = iterator.next()
      def val = iter.value
      def key = iter.key

      if (key in fullNamesOfPsToDelete) {
        iterator.remove()
        deleted.put(key, val)
      }
    }

    return deleted
  }

  /**
   * Forms compound setting name which consists from property name and module name
   * @param name
   * @param module
   * @return
   */
  private static String getSettingFullName(String name, String module = null){
    if(module && name) {
      if (name.contains(MODULE_NAME_SEPARATOR)) {
        throw new IllegalArgumentException("PersistentSetting's name" +
            " must not contain symbol '$MODULE_NAME_SEPARATOR'");
      }
      return name + MODULE_NAME_SEPARATOR + module
    }

    return name
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

  protected static List<PersistentSetting> doBootstrap(configs, String moduleName = null) {
    if (!configs) return []

    List<PersistentSetting> created = []

    def allPs = PersistentSetting.list().findAll({
      it.module == moduleName
    })

    (configs.collect { k, v -> k } - allPs.collect { it.name }).each {
      try {
        def s = configs[it]

        def name = it
        PersistentSetting ps = new PersistentSetting()
        ps.name = name
        ps.value = s.defaultValue
        ps.module = moduleName ?: ((s.module instanceof String) ? s.module : null)
        ps.isHidden = s.hidden ?: false
        ps.save(failOnError: true, flush: true);
        created.add(ps)
      } catch (Exception e) {
        print "$it, ${configs[it]}: " + e.message
      }
    }

    return created
  }

  static Object getValue(String name, String module = null) {
    try {
      if (!module) {
        return findByName(name, [cache: true]).value
      } else {
        return findByNameAndModule([name, module], [cache: true]).value
      }
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
    synchronized (psStorageSyncObj) {
      def setting = new org.grails.persistentsettings.PersistentSetting()
      def oValue
      if (!org.grails.persistentsettings.PersistentSetting.getConfig().containsKey(name)) {
        setting.errors.reject('persistentsettings.name.invalid')
        return setting
      }
      try {
        def type = org.grails.persistentsettings.PersistentSetting.getConfig()[name].type
        oValue = value.asType(type)
      } catch (Exception e) {
        println "Errors: ${e.message}"
        setting.errors.reject('persistentsettings.type.invalid')
        return setting
      }
      return setValue(name, (Object) oValue)
    }
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

