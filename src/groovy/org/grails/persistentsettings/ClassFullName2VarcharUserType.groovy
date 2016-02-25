package org.grails.persistentsettings
import org.hibernate.HibernateException
import org.hibernate.usertype.UserType

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
/**
 * Created by d.ponomarev on 20.02.2016.
 */
class ClassFullName2VarcharUserType implements UserType {
  @Override
  int[] sqlTypes() {
    return [ Types.VARCHAR ];
  }

  @Override
  Class returnedClass() {
    return Class.class
  }

  @Override
  boolean equals(Object x, Object y) throws HibernateException {
    if (x == null) {
      return y == null
    } else {
      return x.equals(y)
    }
  }

  @Override
  int hashCode(Object x) throws HibernateException {
    if (x) {
      return x.hashCode()
    } else {
      return 0
    }
  }

  @Override
  Object nullSafeGet(ResultSet rs, String[] names, Object owner) throws HibernateException, SQLException {
    def colName = names[0]
    final String classFullName = rs.getString(colName);
    if (classFullName) {
      try {
        return Class.forName(classFullName, true,
            Thread.currentThread().contextClassLoader)
      } catch (ClassNotFoundException cnfe) {
        String idValue = owner.hasProperty("id") ? owner.id as String : "null"
        throw new ClassNotFoundException("Can not convert property of instaince of '${owner.class.name}' " +
            "with id='$idValue' which corresponds column '$colName'", cnfe)
      }
    } else {
      null
    }
  }

  @Override
  void nullSafeSet(PreparedStatement st, Object value, int index) throws HibernateException, SQLException {
    if (value) {
      st.setString(index, ((Class) value).name)
    } else {
      st.setString(index, null)
    }
  }

  @Override
  Object deepCopy(Object value) throws HibernateException {
    return value
  }

  @Override
  boolean isMutable() {
    return true
  }

  @Override
  Serializable disassemble(Object value) throws HibernateException {
    return value as Serializable
  }

  @Override
  Object assemble(Serializable cached, Object owner) throws HibernateException {
     return cached
  }

  @Override
  Object replace(Object original, Object target, Object owner) throws HibernateException {
    return original
  }
}
