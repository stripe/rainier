package com.stripe.rainier.compute

import org.benf.cfr.reader.entities.ClassFile
import org.benf.cfr.reader.util.bytestream.BaseByteData
import org.benf.cfr.reader.state.{DCCommonState, ClassFileSourceImpl}
import org.benf.cfr.reader.util.getopt.OptionsImpl
import org.benf.cfr.reader.util.output.ToStringDumper

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
