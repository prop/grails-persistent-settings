package org.grails.persistentsettings
import org.hibernate.HibernateException
import org.hibernate.usertype.UserType

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

/**
 * Created by d.ponomarev on 24.02.2016.
 */
class SerializableConfigObjectUserType implements UserType {

  private static final int SQL_TYPE = Types.BLOB;

  @Override
  int[] sqlTypes() {
    return [SQL_TYPE]
  }

  @Override
  Class returnedClass() {
    return ConfigObject.class
  }

  @Override
  boolean equals(Object x, Object y) throws HibernateException {
    if (x != null) {
      return y != null
    } else {
      x.equals(y)
    }
  }

  @Override
  int hashCode(Object x) throws HibernateException {
    return 0
  }

  @Override
  Object nullSafeGet(ResultSet rs, String[] names, Object owner) throws HibernateException, SQLException {
    def stream = rs.getBinaryStream(names[0])
    if (stream) {
      Object result = null;
      stream.withObjectInputStream(Thread.currentThread().contextClassLoader, { is ->
        result = is.readObject()
      })
      return result
    } else {
      return null
    }
  }

  @Override
  void nullSafeSet(PreparedStatement st, Object value, int index) throws HibernateException, SQLException {
    if (value) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      new ObjectOutputStream(bos).writeObject(value)
      st.setBytes(index, bos.toByteArray())
    } else {
      st.setNull(index, SQL_TYPE)
    }
  }

  @Override
  Object deepCopy(Object value) throws HibernateException {
    return value
  }

  @Override
  boolean isMutable() {
    return false
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
