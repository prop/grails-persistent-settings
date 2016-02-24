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

  private Class type

  String module
  private static final String MODULE_NAME_SEPARATOR = ':'

  static transients = [
      'propertyName',
      'description',
      'value',
      'oValue',
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
      def type = (Class) getPropertyWithoutSideEffect(getSettingFullName(name, module), "type")
      if (!type) {
        type = this.type
      }
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
    type type: ClassFullName2VarcharUserType, class: String, nullable: true
  }

  String getPropertyName() {
    if (!module) {
      return "org.grails.persistentsettings." + name + ".name"
    } else {
      return "js." + module + ".settings." + name + ".name"
    }
  }

  String getDescription() {
    if (!module) {
      return "org.grails.persistentsettings." + name + ".description"
    } else {
      return "js." + module + ".settings." + name + ".description"
    }
  }

  ConfigObject getAdvanced() {
    def fullName = getSettingFullName(name, module)
    return PersistentSetting.getConfig()[fullName].advanced
  }

  Class getType() {
    def settingFullName = getSettingFullName(name, module)
    def type = getPropertyWithoutSideEffect(settingFullName, "type")
    if (!type) {
      type = this.type
    }
    if (type.getClass() == Class.class) return type
    return null
  }

  private static def getPropertyWithoutSideEffect(String settingFullName, String property){
    if (!getConfig().containsKey(settingFullName)) {
      return null
    }
    def setting = getConfig()[settingFullName]

    if (!setting.containsKey(property)) {
      return null
    }

    return setting[property]
  }

  void setType(Class type) {
    this.type = type
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
          !list.contains(obj.value)) {
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

  static List<PersistentSetting> firstCleanBootstrap() {
    List<PersistentSetting> bootstrapped = doBootstrap(getConfig())

    def psNamesToDelete = PersistentSetting.findAllByModuleIsNull().collect({ it.name }) -
        getConfig().collect({ k, v -> k })
    dbDeleteConfigs(psNamesToDelete)

    return bootstrapped
  }

  private static final Object psStorageSyncObj = new Object()

  static List<PersistentSetting> reCleanBootstrap(ConfigObject config, String moduleName) {
    if (config != null && moduleName != null) {
      synchronized (psStorageSyncObj) {
        config = addNewConfigs(config, moduleName)

        updateValuesFromNullModulePs(doBootstrap(config, moduleName))

        List<PersistentSetting> actualPs = PersistentSetting.findAllByModuleAndNameInList(moduleName,
            config.collect { it.key })
        deleteOtherConfigs(actualPs, moduleName)
        return actualPs
      }
    } else {
      return []
    }
  }

  static void updateValuesFromNullModulePs(List<PersistentSetting> persistentSettings) {
    for (PersistentSetting ps : persistentSettings) {
      PersistentSetting nullModuleSetting = PersistentSetting.findByNameAndModuleIsNull(ps.name)
      if (nullModuleSetting) {
        ps.setsValue(nullModuleSetting.getsValue())
        ps.setIsHidden(nullModuleSetting.getIsHidden())
        def type = nullModuleSetting.getType()
        if (type) {
          ps.setType(type)
        }
        ps.save(failOnError: true, flush: true)
      }
    }
  }

  static ConfigObject addNewConfigs(ConfigObject configsToLoad, String module) {
    def config = getConfig()

    ConfigObject added = new ConfigObject()
    configsToLoad.each { String k, v ->
      def fullName = getSettingFullName(k, module)

      def configToSave = config[k]
      if (configToSave) {
        configToSave = configToSave.clone()
      } else {
        configToSave = v.clone()
      }

      configToSave.module = module
      config.putAt(fullName, configToSave)
      added.put(k, configToSave)
    }

    return added
  }

  private static void deleteOtherConfigs(List<PersistentSetting> actualPs, String moduleName) {
    def fullNamesOfPsToDelete =
        getFullNamesOfConfigsForModule(moduleName) -
            actualPs.collect({ getSettingFullName(it.name, moduleName) })

    deleteFromLocalConfig(fullNamesOfPsToDelete)
    deleteFromDomainConfig(actualPs, moduleName)
  }

  private static void deleteFromDomainConfig(List<PersistentSetting> actualPs, String moduleName) {
    def configNamesForDelete = PersistentSetting.findAllByModule(moduleName).collect { it.name } -
        actualPs.collect { it.name }
    if (configNamesForDelete) {
      dbDeleteConfigs(configNamesForDelete, moduleName)
    }
  }

  private static void dbDeleteConfigs(List<String> configNamesForDelete, String moduleName = null) {
    String hql = "delete from PersistentSetting ps where ps.name in :deletedConfigNames"
    Map params = [deletedConfigNames: configNamesForDelete] as Map

    if (moduleName != null) {
      hql += " and ps.module=:module"
      params << [module: moduleName]
    } else {
      hql += " and ps.module is null"
    }

    PersistentSetting.executeUpdate(hql, params)
  }

  private static ArrayList<Object> getFullNamesOfConfigsForModule(String moduleName) {
    getConfig().collect({ k, v -> k }).findAll({ it.toString().endsWith(MODULE_NAME_SEPARATOR + moduleName) })
  }

  private static void deleteFromLocalConfig(List fullNamesOfPsToDelete) {
    if (fullNamesOfPsToDelete) {
      def iterator = getConfig().entrySet().iterator()

      while (iterator.hasNext()) {
        def iter = iterator.next()
        def key = iter.key

        if (key in fullNamesOfPsToDelete) {
          iterator.remove()
        }
      }
    }
  }

  /**
   * Forms compound setting name which consists from property name and module name
   * @param name pure setting name. Must not contain {@code MODULE_NAME_SEPARATOR}. Nullable
   * @param module module name. Nullable
   * @return Compound setting name. Nullable
   *
   */
  private static String getSettingFullName(String name, String module = null) {
    if (module && name) {
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
        ps.type = s.type
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
        return findByNameAndModuleIsNull(name, [cache: true]).value
      } else {
        return findByNameAndModule(name, module, [cache: true]).value
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

