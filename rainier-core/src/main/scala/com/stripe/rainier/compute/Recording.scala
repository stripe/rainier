package com.stripe.rainier.compute

import java.io._
import scala.util.Try

case class Recording(params: List[List[Double]]) {

  def serialize: Array[Byte] = {
    val stream = new ByteArrayOutputStream()
    val dos = new DataOutputStream(stream)

    /** Assumption: all the lists are the same size **/
    dos.writeInt(params.head.size)
    params.flatten.foreach(dos.writeDouble(_))
    dos.close
    stream.toByteArray
  }

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

  def deserialize(bytes: Array[Byte]): Recording = {
    val dis = new DataInputStream(new ByteArrayInputStream(bytes))
    val size = dis.readInt()
    val params = Stream
      .continually(Try(dis.readDouble()))
      .takeWhile(_.isSuccess)
      .map(_.get)
      .toList
      .grouped(size)
      .toList
    Recording(params)
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
    deserialize(bytes)
  }
}
