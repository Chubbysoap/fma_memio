package chipyard.example

import chisel3._
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import hardfloat._

class FMAWithMemIO(val w: Int) extends Bundle {
  val input_valid = Input(Bool())
  val input_ready = Output(Bool())
  val x = Input(UInt(w.W)) // a
  val y = Input(UInt(w.W)) // b
  val z = Input(UInt(w.W)) // c
  val output_valid = Output(Bool())
  val output_ready = Input(Bool())
  val result = Output(UInt(w.W))
  val busy = Output(Bool())
}

class FMAWithMem(val w: Int = 32) extends Module {
  val io = IO(new FMAWithMemIO(w))

  // FSM states
  val s_idle :: s_compute :: s_done :: Nil = Enum(3)
  val state = RegInit(s_idle)

  // Memory: 4-entry register file
  val mem = Mem(4, UInt(w.W))
  val addr = RegInit(0.U(2.W))
  val mem_data = RegInit(0.U(w.W))

  // FMA unit (using HardFloat)
  val fma = Module(new MulAddRecFN(8, 24)) // 32-bit float (8 exp, 24 sig)
  fma.io.a := io.x
  fma.io.b := io.y
  fma.io.c := io.z
  fma.io.op := 0.U // FMA operation
  fma.io.roundingMode := 0.U // Round to nearest, ties to even
  fma.io.detectTininess := 1.U // Tininess after rounding
  val result = recFNToFN(fma.io.out, 8, 24)

  // Default outputs
  io.input_ready := state === s_idle
  io.output_valid := state === s_done
  io.busy := state =/= s_idle
  io.result := mem_data

  // FSM logic
  switch(state) {
    is(s_idle) {
      when(io.input_valid) {
        mem(addr) := io.x // Store input a
        mem(addr + 1.U) := io.y // Store input b
        mem(addr + 2.U) := io.z // Store input c
        state := s_compute
      }
    }
    is(s_compute) {
      mem(addr + 3.U) := result // Store result
      state := s_done
    }
    is(s_done) {
      when(io.output_ready) {
        mem_data := mem(addr + 3.U) // Read result
        addr := addr + 1.U // Increment for next operation
        state := s_idle
      }
    }
  }
}

class FMAWithMemTL(params: FMAParams, beatBytes: Int)(implicit p: Parameters)
  extends ClockSinkDomain(ClockSinkParameters())(p) {
  val device = new SimpleDevice("fma-mem", Seq("ucbbar,fma-mem"))
  val node = TLRegisterNode(
    Seq(AddressSet(params.address, 4096-1)),
    device,
    "reg/control",
    beatBytes = beatBytes
  )

  override lazy val module = new FMAWithMemTLImpl

  class FMAWithMemTLImpl extends Impl with HasFMAWithMemTopIO {
    val io = IO(new FMAWithMemTopIO)
    val fma = Module(new FMAWithMem(32))

    // Memory-mapped registers
    val regmap = RegMapper(
      4096,
      0,
      RegFieldGroup("control", Some("Control registers"), Seq(
        RegField.w(32, RegWriteFn((valid, data) => {
          fma.io.x := data
          fma.io.input_valid := valid
          fma.io.input_ready
        }), RegReadFn(fma.io.input_ready)),
        RegField.w(32, RegWriteFn((valid, data) => {
          fma.io.y := data
          fma.io.input_valid := valid
          fma.io.input_ready
        })),
        RegField.w(32, RegWriteFn((valid, data) => {
          fma.io.z := data
          fma.io.input_valid := valid
          fma.io.input_ready
        })),
        RegField.r(32, RegReadFn((ready, data) => {
          fma.io.output_ready := ready
          (fma.io.output_valid, fma.io.result)
        })),
        RegField.r(1, fma.io.busy)
      ))
    )

    node.regmap(regmap: _*)
  }
}

trait HasFMAWithMemTopIO extends RawModule {
  val io = IO(new Bundle {})
}

case class FMAParams(address: BigInt = 0x10000)

object FMAWithMem {
  def apply(params: FMAParams, beatBytes: Int)(implicit p: Parameters): TLRegisterNode = {
    val fma = LazyModule(new FMAWithMemTL(params, beatBytes))
    fma.node
  }
}