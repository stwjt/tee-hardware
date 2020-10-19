package uec.teehardware.devices.random

import scala.math._
import chisel3._
import chisel3.util._
import freechips.rocketchip.util.ElaborationArtefacts

class RO_single(val impl: String = "Simulation") extends Module{
  val io = IO(new Bundle{
    //Inputs
    val input_1   = Input(Bool())
    val input_2   = Input(Bool())
    //Outputs
    val out       = Output(Bool())
  })

  val w1 = Wire(Bool())
  val w2 = Wire(Bool())
  val w3 = Wire(Bool())

  val nand_1 = Module(new trng_primitive_nand(impl)).io
  val not_1  = Module(new trng_primitive_not(impl)).io
  val not_2  = Module(new trng_primitive_not(impl)).io

  nand_1.input_1 := io.input_1
  nand_1.input_2 := io.input_2
  w1             := nand_1.output_1
  dontTouch(w1)
  not_1.input_1  := w1
  w2             := not_1.output_1
  dontTouch(w2)
  not_2.input_1  := w2
  w3             := not_2.output_1
  dontTouch(w3)
  io.out         := w3
}

case class ROLocHints
(
  slice_x: Int = 45,
  slice_y: Int = 45
)

class RingOscillator_top(val edges: Int, val name_ro: String = "ro", val impl: String = "Simulation", val hints: ROLocHints = ROLocHints()) extends Module {
  val io = IO(new Bundle {
    //Inputs
    val enable   = Input(Bool())
    //val reset_c1  = Input(Bool())
    //Outputs
    val pulse    = Output(Bool())
  })
  val RO_edges = VecInit(Seq.tabulate(edges){ case i =>
    val m = Module(new RO_single(impl))
      m.suggestName(s"RO_single_${i}")
      m.io
  })
  var n = RO_edges.length
  (0 until n).map(i => RO_edges(i).input_1 := io.enable)
  (1 until n).map(i => RO_edges(i).input_2 := RO_edges(i-1).out)
  RO_edges(0).input_2 := RO_edges(n-1).out
  io.pulse := RO_edges(n-1).out


  if(impl == "Xilinx") {
    // TODO: The name of the master path for now is fixed
    val master = "TEEHWSoC/TEEHWPlatform/sys/randomClockDomainWrapper/random_0/TRNG/RO/"+name_ro+"/"
    // NOTE: Name of the modules from here on are:
    // 0: RO_single_0
    // 1: RO_single_1
    ElaborationArtefacts.add(
      name_ro+"vivado.xdc",
      s"""
         |
         |""".stripMargin
    )
  }
}

class trng_primitive_nand(val impl: String = "Simulation") extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle(){
    //Inputs
    val input_1 = Input(Bool())
    val input_2 = Input(Bool())
    //Outputs
    val output_1 = Output(Bool())
  })
  if(impl == "Xilinx") {
    /*
    NAND truth table
    out - in0 in1
    1     0   0
    1     0   1
    1     1   0
    0     1   1
    For all other cases, is 0
    The config for INIT is 0007
    */
    setInline("trng_primitive_nand.v",
      s"""
         |module trng_primitive_nand(
         |  input input_1,
         |  input input_2,
         |  output output_1
         |  );
         |
         |  LUT4 #(
         |      .INIT(16'h0007)  // Specify LUT Contents
         |   ) LUT4 (
         |      .O(output_1), // LUT local output
         |      .I0(input_1), // LUT input
         |      .I1(input_2), // LUT input
         |      .I2(0), // LUT input
         |      .I3(0)  // LUT input
         |   );
         |endmodule
         |
         |""".stripMargin)
  } else {
    setInline("trng_primitive_nand.v",
      s"""
         |module trng_primitive_nand(
         |  input input_1,
         |  input input_2,
         |  output output_1
         |  );
         |  nand
         |`ifndef SYNTHESIS
         |    #2
         |`endif
         |    (output_1,input_1,input_2) ;
         |endmodule
         |
         |""".stripMargin)
  }
}

class trng_primitive_not(val impl: String = "Simulation") extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle(){
    //Inputs
    val input_1 = Input(Bool())
    //Outputs
    val output_1 = Output(Bool())
  })
  if(impl == "Xilinx") {
    /*
    NOT truth table
    out - in3 in2-0
    1     0   X
    0     1   X
    The config for INIT is 00FF, and use input 3
    */
    setInline("trng_primitive_not.v",
      s"""
         |module trng_primitive_not(
         |  input input_1,
         |  output output_1
         |  );
         |
         |  LUT4 #(
         |      .INIT(16'h00FF)  // Specify LUT Contents
         |   ) LUT4_L_inst (
         |      .O(output_1), // LUT local output
         |      .I0(0), // LUT input
         |      .I1(0), // LUT input
         |      .I2(0), // LUT input
         |      .I3(input_1)  // LUT input
         |   );
         |""".stripMargin)
  } else {
    setInline("trng_primitive_not.v",
      s"""
         |module trng_primitive_not(
         |  input input_1,
         |  output output_1
         |  );
         |  not
         |`ifndef SYNTHESIS
         |    #3
         |`endif
         |    (output_1,input_1) ;
         |endmodule
         |
         |""".stripMargin)
  }
}

class clock_diviver_ring(val wrap: Int = 100000000) extends Module{
  val io = IO(new Bundle{
    //Input's
    val reset 	= Input(Bool())
    //Output's
    val out 	= Output(Bool())
  })
  val reg = RegInit(0.U(26.W))
  when(io.reset === true.B){
    reg := 0.U
    io.out := false.B
  }.elsewhen(reg === 100000000.U){
    reg    := 0.U
    io.out := true.B
  }.otherwise{
    reg    := reg + 1.U
    io.out := false.B
  }
}

// TODO: This is fixed anyways
class counter_ring(val n: Int = 10) extends Module {
  val io = IO(new Bundle {
    //Input's
    val reset    	= Input(Bool())
    val enable      = Input(Bool())
    //Output's
    val out    	= Output(UInt(10.W))
  })
  val reg = RegInit(0.U(10.W))
  when(io.reset ===true.B){
    reg := 0.U
  }.elsewhen(io.enable === true.B){
    reg := reg + 1.U
  }.elsewhen(reg === 1023.U){//1023
    reg := 0.U
  }.otherwise{
    reg := reg
  }
  io.out := reg
}

object RingOscillatorMain extends App {
  println("Generating RO")
  chisel3.Driver.execute(Array("--no-check-comb-loops","--target-dir", "generated"), () => new RingOscillator_top(21))
}
