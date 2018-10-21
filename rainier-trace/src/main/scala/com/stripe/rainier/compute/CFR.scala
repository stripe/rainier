package com.stripe.rainier.compute

import org.benf.cfr.reader
import reader.entities.ClassFile
import reader.state.{DCCommonState, ClassFileSourceImpl}
import reader.util.bytestream.BaseByteData
import reader.util.getopt.OptionsImpl
import reader.util.output.ToStringDumper

object CFR {
  val optionMap = new java.util.HashMap[String, String]
  val options = new OptionsImpl(optionMap)

  def decompile(seq: Seq[Array[Byte]]): Seq[String] = {
    val state = new DCCommonState(options, new ClassFileSourceImpl(options))
    seq.map { bytes =>
      val cf = new ClassFile(new BaseByteData(bytes), "", state)
      cf.analyseTop(state);
      ToStringDumper
        .toString(cf)
        .split("\n")
        .filterNot { x =>
          x.startsWith("/*") ||
          x.startsWith(" */") ||
          x.startsWith(" * Decompiled") ||
          x.startsWith(" * Exception")
        }
        .mkString("\n")
    }
  }
}
