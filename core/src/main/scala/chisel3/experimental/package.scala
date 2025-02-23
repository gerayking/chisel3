// SPDX-License-Identifier: Apache-2.0

package chisel3

import chisel3.ExplicitCompileOptions.Strict
import chisel3.experimental.DataMirror.internal.chiselTypeClone
import chisel3.internal.sourceinfo.SourceInfo

/** Package for experimental features, which may have their API changed, be removed, etc.
  *
  * Because its contents won't necessarily have the same level of stability and support as
  * non-experimental, you must explicitly import this package to use its contents.
  */
package object experimental {
  import scala.language.implicitConversions
  import chisel3.internal.BaseModule

  // Implicit conversions for BlackBox Parameters
  implicit def fromIntToIntParam(x:       Int):    IntParam = IntParam(BigInt(x))
  implicit def fromLongToIntParam(x:      Long):   IntParam = IntParam(BigInt(x))
  implicit def fromBigIntToIntParam(x:    BigInt): IntParam = IntParam(x)
  implicit def fromDoubleToDoubleParam(x: Double): DoubleParam = DoubleParam(x)
  implicit def fromStringToStringParam(x: String): StringParam = StringParam(x)

  type ChiselEnum = EnumFactory

  // Rocket Chip-style clonemodule

  /** A record containing the results of CloneModuleAsRecord
    * The apply method is retrieves the element with the supplied name.
    */
  type ClonePorts = BaseModule.ClonePorts

  object CloneModuleAsRecord {

    /** Clones an existing module and returns a record of all its top-level ports.
      * Each element of the record is named with a string matching the
      * corresponding port's name and shares the port's type.
      * @example {{{
      * val q1 = Module(new Queue(UInt(32.W), 2))
      * val q2_io = CloneModuleAsRecord(q1)("io").asInstanceOf[q1.io.type]
      * q2_io.enq <> q1.io.deq
      * }}}
      */
    def apply(
      proto: BaseModule
    )(
      implicit sourceInfo: chisel3.internal.sourceinfo.SourceInfo,
      compileOptions:      CompileOptions
    ): ClonePorts = {
      BaseModule.cloneIORecord(proto)
    }
  }

  val requireIsHardware = chisel3.internal.requireIsHardware
  val requireIsChiselType = chisel3.internal.requireIsChiselType
  type Direction = ActualDirection
  val Direction = ActualDirection

  /** The same as [[IO]] except there is no prefix for the name of the val */
  def FlatIO[T <: Record](gen: => T)(implicit sourceInfo: SourceInfo, compileOptions: CompileOptions): T = noPrefix {
    import dataview._
    def coerceDirection(d: Data) = {
      import chisel3.{SpecifiedDirection => SD}
      DataMirror.specifiedDirectionOf(gen) match {
        case SD.Flip   => Flipped(d)
        case SD.Input  => Input(d)
        case SD.Output => Output(d)
        case _         => d
      }
    }
    val ports: Seq[Data] =
      gen.elements.toSeq.reverse.map {
        case (name, data) =>
          val p = IO(coerceDirection(chiselTypeClone(data).asInstanceOf[Data]))
          p.suggestName(name)
          p

      }

    implicit val dv: DataView[Seq[Data], T] = DataView.mapping(
      _ => chiselTypeClone(gen).asInstanceOf[T],
      (seq, rec) => seq.zip(rec.elements.toSeq.reverse).map { case (port, (_, field)) => port -> field }
    )
    ports.viewAs[T]
  }

  implicit class ChiselRange(val sc: StringContext) extends AnyVal {

    import scala.language.experimental.macros

    /** Specifies a range using mathematical range notation. Variables can be interpolated using
      * standard string interpolation syntax.
      * @example {{{
      * UInt(range"[0, 2)")
      * UInt(range"[0, \$myInt)")
      * UInt(range"[0, \${myInt + 2})")
      * }}}
      */
    def range(args: Any*): chisel3.internal.firrtl.IntervalRange = macro chisel3.internal.RangeTransform.apply
  }

  class dump extends chisel3.internal.naming.dump
  class treedump extends chisel3.internal.naming.treedump

  /** Experimental macro for naming Chisel hardware values
    *
    * By default, Chisel uses reflection for naming which only works for public fields of `Bundle`
    * and `Module` classes. Applying this macro annotation to a `class` or `object` enables Chisel
    * to name any hardware values within the annotated `class` or `object.
    *
    * @example {{{
    * import chisel3._
    * import chisel3.experimental.chiselName
    *
    * @chiselName
    * class MyModule extends Module {
    *   val io = IO(new Bundle {
    *     val in = Input(UInt(8.W))
    *     val out = Output(UInt(8.W))
    *   })
    *   def createReg(): Unit = {
    *     // @chiselName allows Chisel to name this Reg
    *     val myReg = RegInit(io.in)
    *     io.out := myReg
    *   }
    *   createReg()
    * }
    * }}}
    */
  class chiselName extends chisel3.internal.naming.chiselName

  /** Do not name instances of this type in [[chiselName]]
    *
    * By default, `chiselName` will include `val` names of instances of annotated classes as a
    * prefix in final naming. Mixing in this trait to a `class`, `object`, or anonymous `class`
    * instances will exclude the `val` name from `chiselName` naming.
    *
    * @example {{{
    * import chisel3._
    * import chisel3.experimental.{chiselName, NoChiselNamePrefix}
    *
    * // Note that this is not a Module
    * @chiselName
    * class Counter(w: Int) {
    *   val myReg = RegInit(0.U(w.W))
    *   myReg := myReg + 1.U
    * }
    *
    * @chiselName
    * class MyModule extends Module {
    *   val io = IO(new Bundle {
    *     val out = Output(UInt(8.W))
    *   })
    *   // Name of myReg will be "counter0_myReg"
    *   val counter0 = new Counter(8)
    *   // Name of myReg will be "myReg"
    *   val counter1 = new Counter(8) with NoChiselNamePrefix
    *   io.out := counter0.myReg + counter1.myReg
    * }
    * }}}
    */
  @deprecated(
    "@chiselName and NoChiselNamePrefix have been replaced by the compiler plugin and AffectsChiselPrefix. Use these instead",
    "Chisel 3.5"
  )
  trait NoChiselNamePrefix

  /** Generate prefixes from values of this type in the Chisel compiler plugin
    *
    * Users can mixin this trait to tell the Chisel compiler plugin to include the names of
    * vals of this type when generating prefixes for naming `Data` and `Mem` instances.
    * This is generally useful whenever creating a `class` that contains `Data`, `Mem`,
    * or `Module` instances but does not itself extend `Data` or `Module`.
    *
    * @see See [[https://www.chisel-lang.org/chisel3/docs/explanations/naming.html the compiler plugin documentation]] for more information on this process.
    *
    * @example {{{
    * import chisel3._
    * import chisel3.experimental.AffectsChiselPrefix
    *
    * class MyModule extends Module {
    *   // Note: This contains a Data but is not a named component itself
    *   class NotAData extends AffectsChiselPrefix {
    *     val value = Wire(Bool())
    *   }
    *
    *   // Name with AffectsChiselPrefix:    "nonData_value"
    *   // Name without AffectsChiselPrefix: "value"
    *   val nonData = new NotAData
    *
    *   // Name with AffectsChiselPrefix:    "nonData2_value"
    *   // Name without AffectsChiselPrefix: "value_1"
    *   val nonData2 = new NotAData
    * }
    */
  trait AffectsChiselPrefix

  object BundleLiterals {
    implicit class AddBundleLiteralConstructor[T <: Record](x: T) {
      def Lit(elems: (T => (Data, Data))*)(implicit sourceInfo: SourceInfo, compileOptions: CompileOptions): T = {
        x._makeLit(elems: _*)
      }
    }
  }

  /** This class provides the `Lit` method needed to define a `Vec` literal
    */
  object VecLiterals {
    implicit class AddVecLiteralConstructor[T <: Data](x: Vec[T]) {

      /** Given a generator of a list tuples of the form [Int, Data]
        * constructs a Vec literal, parallel concept to `BundleLiteral`
        *
        * @param elems tuples of an index and a literal value
        * @return
        */
      def Lit(elems: (Int, T)*)(implicit sourceInfo: SourceInfo, compileOptions: CompileOptions): Vec[T] = {
        x._makeLit(elems: _*)
      }
    }

    implicit class AddObjectLiteralConstructor(x: Vec.type) {

      /** This provides an literal construction method for cases using
        * object `Vec` as in `Vec.Lit(1.U, 2.U)`
        */
      def Lit[T <: Data](elems: T*)(implicit sourceInfo: SourceInfo, compileOptions: CompileOptions): Vec[T] = {
        require(elems.nonEmpty, s"Lit.Vec(...) must have at least one element")
        val indexElements = elems.zipWithIndex.map { case (element, index) => (index, element) }
        val widestElement = elems.maxBy(_.getWidth)
        val vec: Vec[T] = Vec.apply(indexElements.length, chiselTypeOf(widestElement))
        vec.Lit(indexElements: _*)
      }
    }
  }

  /** Use to add a prefix to any components generated in the provided scope.
    *
    * @example {{{
    *
    * val x1 = prefix("first") {
    *   // Anything generated here will be prefixed with "first"
    * }
    *
    * val x2 = prefix(mysignal) {
    *   // Anything generated here will be prefixed with the name of mysignal
    * }
    *
    * }}}
    */
  val prefix = chisel3.internal.prefix

  /** Use to clear existing prefixes so no signals within the scope are prefixed
    * by signals/names outside the scope
    *
    * @example {{{
    *
    * val x1 = prefix("first") {
    *   // Anything generated here will have no prefix.
    *   // The result returned from this would *still* be called `x1` however.
    * }
    * }}}
    */
  val noPrefix = chisel3.internal.noPrefix

  // ****************************** Hardware equivalents of Scala Tuples ******************************
  // These are intended to be used via DataView

  /** [[Data]] equivalent of Scala's [[Tuple2]]
    *
    * Users may not instantiate this class directly. Instead they should use the implicit conversion from `Tuple2` in
    * `chisel3.experimental.conversions`
    */
  final class HWTuple2[+A <: Data, +B <: Data] private[chisel3] (val _1: A, val _2: B) extends Bundle()(Strict) {
    // Because this implementation exists in chisel3.core, it cannot compile with the plugin, so we implement the behavior manually
    override protected def _usingPlugin:   Boolean = true
    override protected def _cloneTypeImpl: Bundle = new HWTuple2(chiselTypeClone(_1), chiselTypeClone(_2))
    override protected def _elementsImpl: Iterable[(String, Any)] = Vector(
      "_1" -> _1,
      "_2" -> _2
    )
  }

  /** [[Data]] equivalent of Scala's [[Tuple3]]
    *
    * Users may not instantiate this class directly. Instead they should use the implicit conversion from `Tuple3` in
    * `chisel3.experimental.conversions`
    */
  final class HWTuple3[+A <: Data, +B <: Data, +C <: Data] private[chisel3] (
    val _1: A,
    val _2: B,
    val _3: C)
      extends Bundle()(Strict) {
    // Because this implementation exists in chisel3.core, it cannot compile with the plugin, so we implement the behavior manually
    override protected def _usingPlugin: Boolean = true
    override protected def _cloneTypeImpl: Bundle = new HWTuple3(
      chiselTypeClone(_1),
      chiselTypeClone(_2),
      chiselTypeClone(_3)
    )
    override protected def _elementsImpl: Iterable[(String, Any)] = Vector(
      "_1" -> _1,
      "_2" -> _2,
      "_3" -> _3
    )
  }

  /** [[Data]] equivalent of Scala's [[Tuple4]]
    *
    * Users may not instantiate this class directly. Instead they should use the implicit conversion from `Tuple4` in
    * `chisel3.experimental.conversions`
    */
  final class HWTuple4[+A <: Data, +B <: Data, +C <: Data, +D <: Data] private[chisel3] (
    val _1: A,
    val _2: B,
    val _3: C,
    val _4: D)
      extends Bundle()(Strict) {
    // Because this implementation exists in chisel3.core, it cannot compile with the plugin, so we implement the behavior manually
    override protected def _usingPlugin: Boolean = true
    override protected def _cloneTypeImpl: Bundle = new HWTuple4(
      chiselTypeClone(_1),
      chiselTypeClone(_2),
      chiselTypeClone(_3),
      chiselTypeClone(_4)
    )
    override protected def _elementsImpl: Iterable[(String, Any)] = Vector(
      "_1" -> _1,
      "_2" -> _2,
      "_3" -> _3,
      "_4" -> _4
    )
  }

  /** [[Data]] equivalent of Scala's [[Tuple5]]
    *
    * Users may not instantiate this class directly. Instead they should use the implicit conversion from `Tuple5` in
    * `chisel3.experimental.conversions`
    */
  final class HWTuple5[+A <: Data, +B <: Data, +C <: Data, +D <: Data, +E <: Data] private[chisel3] (
    val _1: A,
    val _2: B,
    val _3: C,
    val _4: D,
    val _5: E)
      extends Bundle()(Strict) {
    // Because this implementation exists in chisel3.core, it cannot compile with the plugin, so we implement the behavior manually
    override protected def _usingPlugin: Boolean = true
    override protected def _cloneTypeImpl: Bundle = new HWTuple5(
      chiselTypeClone(_1),
      chiselTypeClone(_2),
      chiselTypeClone(_3),
      chiselTypeClone(_4),
      chiselTypeClone(_5)
    )
    override protected def _elementsImpl: Iterable[(String, Any)] = Vector(
      "_1" -> _1,
      "_2" -> _2,
      "_3" -> _3,
      "_4" -> _4,
      "_5" -> _5
    )
  }

  /** [[Data]] equivalent of Scala's [[Tuple6]]
    *
    * Users may not instantiate this class directly. Instead they should use the implicit conversion from `Tuple6` in
    * `chisel3.experimental.conversions`
    */
  final class HWTuple6[+A <: Data, +B <: Data, +C <: Data, +D <: Data, +E <: Data, +F <: Data] private[chisel3] (
    val _1: A,
    val _2: B,
    val _3: C,
    val _4: D,
    val _5: E,
    val _6: F)
      extends Bundle()(Strict) {
    // Because this implementation exists in chisel3.core, it cannot compile with the plugin, so we implement the behavior manually
    override protected def _usingPlugin: Boolean = true
    override protected def _cloneTypeImpl: Bundle = new HWTuple6(
      chiselTypeClone(_1),
      chiselTypeClone(_2),
      chiselTypeClone(_3),
      chiselTypeClone(_4),
      chiselTypeClone(_5),
      chiselTypeClone(_6)
    )
    override protected def _elementsImpl: Iterable[(String, Any)] = Vector(
      "_1" -> _1,
      "_2" -> _2,
      "_3" -> _3,
      "_4" -> _4,
      "_5" -> _5,
      "_6" -> _6
    )
  }

  /** [[Data]] equivalent of Scala's [[Tuple7]]
    *
    * Users may not instantiate this class directly. Instead they should use the implicit conversion from `Tuple7` in
    * `chisel3.experimental.conversions`
    */
  final class HWTuple7[
    +A <: Data,
    +B <: Data,
    +C <: Data,
    +D <: Data,
    +E <: Data,
    +F <: Data,
    +G <: Data
  ] private[chisel3] (
    val _1: A,
    val _2: B,
    val _3: C,
    val _4: D,
    val _5: E,
    val _6: F,
    val _7: G)
      extends Bundle()(Strict) {
    // Because this implementation exists in chisel3.core, it cannot compile with the plugin, so we implement the behavior manually
    override protected def _usingPlugin: Boolean = true
    override protected def _cloneTypeImpl: Bundle = new HWTuple7(
      chiselTypeClone(_1),
      chiselTypeClone(_2),
      chiselTypeClone(_3),
      chiselTypeClone(_4),
      chiselTypeClone(_5),
      chiselTypeClone(_6),
      chiselTypeClone(_7)
    )
    override protected def _elementsImpl: Iterable[(String, Any)] = Vector(
      "_1" -> _1,
      "_2" -> _2,
      "_3" -> _3,
      "_4" -> _4,
      "_5" -> _5,
      "_6" -> _6,
      "_7" -> _7
    )
  }

  /** [[Data]] equivalent of Scala's [[Tuple8]]
    *
    * Users may not instantiate this class directly. Instead they should use the implicit conversion from `Tuple8` in
    * `chisel3.experimental.conversions`
    */
  final class HWTuple8[
    +A <: Data,
    +B <: Data,
    +C <: Data,
    +D <: Data,
    +E <: Data,
    +F <: Data,
    +G <: Data,
    +H <: Data
  ] private[chisel3] (
    val _1: A,
    val _2: B,
    val _3: C,
    val _4: D,
    val _5: E,
    val _6: F,
    val _7: G,
    val _8: H)
      extends Bundle()(Strict) {
    // Because this implementation exists in chisel3.core, it cannot compile with the plugin, so we implement the behavior manually
    override protected def _usingPlugin: Boolean = true
    override protected def _cloneTypeImpl: Bundle = new HWTuple8(
      chiselTypeClone(_1),
      chiselTypeClone(_2),
      chiselTypeClone(_3),
      chiselTypeClone(_4),
      chiselTypeClone(_5),
      chiselTypeClone(_6),
      chiselTypeClone(_7),
      chiselTypeClone(_8)
    )
    override protected def _elementsImpl: Iterable[(String, Any)] = Vector(
      "_1" -> _1,
      "_2" -> _2,
      "_3" -> _3,
      "_4" -> _4,
      "_5" -> _5,
      "_6" -> _6,
      "_7" -> _7,
      "_8" -> _8
    )
  }

  /** [[Data]] equivalent of Scala's [[Tuple9]]
    *
    * Users may not instantiate this class directly. Instead they should use the implicit conversion from `Tuple9` in
    * `chisel3.experimental.conversions`
    */
  final class HWTuple9[
    +A <: Data,
    +B <: Data,
    +C <: Data,
    +D <: Data,
    +E <: Data,
    +F <: Data,
    +G <: Data,
    +H <: Data,
    +I <: Data
  ] private[chisel3] (
    val _1: A,
    val _2: B,
    val _3: C,
    val _4: D,
    val _5: E,
    val _6: F,
    val _7: G,
    val _8: H,
    val _9: I)
      extends Bundle()(Strict) {
    // Because this implementation exists in chisel3.core, it cannot compile with the plugin, so we implement the behavior manually
    override protected def _usingPlugin: Boolean = true
    override protected def _cloneTypeImpl: Bundle = new HWTuple9(
      chiselTypeClone(_1),
      chiselTypeClone(_2),
      chiselTypeClone(_3),
      chiselTypeClone(_4),
      chiselTypeClone(_5),
      chiselTypeClone(_6),
      chiselTypeClone(_7),
      chiselTypeClone(_8),
      chiselTypeClone(_9)
    )
    override protected def _elementsImpl: Iterable[(String, Any)] = Vector(
      "_1" -> _1,
      "_2" -> _2,
      "_3" -> _3,
      "_4" -> _4,
      "_5" -> _5,
      "_6" -> _6,
      "_7" -> _7,
      "_8" -> _8,
      "_9" -> _9
    )
  }

  /** [[Data]] equivalent of Scala's [[Tuple9]]
    *
    * Users may not instantiate this class directly. Instead they should use the implicit conversion from `Tuple9` in
    * `chisel3.experimental.conversions`
    */
  final class HWTuple10[
    +A <: Data,
    +B <: Data,
    +C <: Data,
    +D <: Data,
    +E <: Data,
    +F <: Data,
    +G <: Data,
    +H <: Data,
    +I <: Data,
    +J <: Data
  ] private[chisel3] (
    val _1:  A,
    val _2:  B,
    val _3:  C,
    val _4:  D,
    val _5:  E,
    val _6:  F,
    val _7:  G,
    val _8:  H,
    val _9:  I,
    val _10: J)
      extends Bundle()(Strict) {
    // Because this implementation exists in chisel3.core, it cannot compile with the plugin, so we implement the behavior manually
    override protected def _usingPlugin: Boolean = true
    override protected def _cloneTypeImpl: Bundle = new HWTuple10(
      chiselTypeClone(_1),
      chiselTypeClone(_2),
      chiselTypeClone(_3),
      chiselTypeClone(_4),
      chiselTypeClone(_5),
      chiselTypeClone(_6),
      chiselTypeClone(_7),
      chiselTypeClone(_8),
      chiselTypeClone(_9),
      chiselTypeClone(_10)
    )
    override protected def _elementsImpl: Iterable[(String, Any)] = Vector(
      "_1" -> _1,
      "_2" -> _2,
      "_3" -> _3,
      "_4" -> _4,
      "_5" -> _5,
      "_6" -> _6,
      "_7" -> _7,
      "_8" -> _8,
      "_9" -> _9,
      "_10" -> _10
    )
  }
}
