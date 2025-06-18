package chipyard.fma

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

class FMAPeripheral(baseAddress: BigInt) extends LazyModule {
  val device = new SimpleDevice("fma", Seq("fma,example"))

  val node = TLRegisterNode(
    address = Seq(AddressSet(baseAddress, 0x1f)),
    device = device,
    beatBytes = 4
  )

  lazy val module = new LazyModuleImp(this) {
    val fma = Module(new FMAModule)

    // Registers for operands and memory
    val regA = RegInit(0.U(32.W))
    val regB = RegInit(0.U(32.W))
    val regC = RegInit(0.U(32.W))
    val mem = RegInit(0.U(32.W))  // Small memory component
    val result = RegInit(0.U(32.W))
    val control = RegInit(0.U(1.W))  // Start computation
    val busy = RegInit(false.B)

    // Counter for simulated delay (4 cycles)
    val counter = RegInit(0.U(3.W))

    // FMA logic
    fma.io.a := regA
    fma.io.b := regB
    fma.io.c := regC

    when (control === 1.U && !busy) {
      busy := true.B
      counter := 0.U
    }

    when (busy) {
      counter := counter + 1.U
      when (counter === 3.U) {
        result := fma.io.result
        mem := fma.io.result  // Store result in memory
        busy := false.B
        control := 0.U
      }
    }

    // Register map
    node.regmap(
      0x00 -> Seq(RegField(32, regA, RegFieldDesc("a", "Operand A"))),
      0x04 -> Seq(RegField(32, regB, RegFieldDesc("b", "Operand B"))),
      0x08 -> Seq(RegField(32, regC, RegFieldDesc("c", "Operand C"))),
      0x0C -> Seq(RegField(32, mem, RegFieldDesc("memory", "Memory storage"))),
      0x10 -> Seq(RegField(1, control, RegFieldDesc("control", "Start computation"))),
      0x14 -> Seq(RegField(1, busy, RegFieldDesc("status", "Busy flag"))),
      0x18 -> Seq(RegField(32, result, RegFieldDesc("result", "FMA Result")))
    )
  }
}

class FMAConfigFragment extends Config((site, here, up) => {
  case TLNetworkTopologyLocated(InSubsystem) => up(TLNetworkTopologyLocated(InSubsystem)).map {
    case tl => tl.copy(
      peripherals = tl.peripherals :+ PeripheralAttachParams(
        device = new FMAPeripheral(0x10000000L),
        controlWhere = TLBusWrapperLocation.PBUS,
        blockerCtrlWhere = TLBusWrapperLocation.PBUS
      )
    )
  }
})

class FMARocketConfig extends Config(
  new FMAConfigFragment ++
    new freechips.rocketchip.subsystem.WithNBigCores(1) ++
    new chipyard.config.AbstractConfig
)