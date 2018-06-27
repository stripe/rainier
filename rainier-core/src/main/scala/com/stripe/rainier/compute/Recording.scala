package com.stripe.rainier.compute

import java.io._

trait Recording {
  def save(path: String): Unit
  def samples: List[Array[Double]]
}

case class RecordedSample(params: List[List[Double]]) extends Recording {

  def save(path: String): Unit = {
    val file = new File(path)

    /** Make directory and an empty file if they don't exist **/
    (new File(file.getCanonicalFile().getParentFile().toString)).mkdirs()
    file.createNewFile()

    val outputStream = new ObjectOutputStream(new FileOutputStream(file))
    outputStream.writeObject(params)
    outputStream.close()
  }

  def samples: List[Array[Double]] = params.map(_.toArray)
}

object RecordedSample {

  def load(path: String): RecordedSample = {
    val inputStream = new ObjectInputStream(new FileInputStream(path))
    val samples = inputStream.readObject.asInstanceOf[List[List[Double]]]
    inputStream.close
    RecordedSample(samples)
  }
}
