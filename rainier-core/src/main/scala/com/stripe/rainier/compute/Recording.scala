package com.stripe.rainier.compute

import java.io._

case class Recording(params: List[List[Double]]) {

  def serialize: Array[Byte] = Recording.serialize(this)

  def save(path: String): Unit = {
    val file = new File(path)

    /** Make directory and an empty file if they don't exist **/
    (new File(file.getCanonicalFile().getParentFile().toString)).mkdirs()
    file.createNewFile()

    val fis = new FileOutputStream(file)
    fis.write(this.serialize)
    fis.close
  }

  def samples: List[Array[Double]] = params.map(_.toArray)
}

object Recording {

  def serialize[A](value: A): Array[Byte] = {
    val stream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(stream)
    oos.writeObject(value)
    oos.close
    stream.toByteArray
  }

  def deserialize[A](bytes: Array[Byte]): A = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val value = ois.readObject.asInstanceOf[A]
    ois.close
    value
  }

  def loadBytes(path: String): Array[Byte] = {
    val bis = new BufferedInputStream(new FileInputStream(path))
    val bytes =
      Stream.continually(bis.read).takeWhile(_ != -1).map(_.toByte).toArray
    bis.close
    bytes
  }

  def load(path: String): Recording = {
    val bytes = loadBytes(path)
    deserialize[Recording](bytes)
  }
}
