package chipyard.example

import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

class WithFMA extends ConfigFragment {
  def apply(implicit p: Parameters): Unit = {
    val fma = LazyModule(new FMAWithMemTL(FMAParams(), 8))
    p(ExtBus).foreach { bus =>
      bus.coupleTo("fma") { fma.node := TLFragmenter(8, 64) := _ }
    }
  }
}

class FMAConfig extends Config(
  new WithFMA ++
    new freechips.rocketchip.subsystem.WithNBigCores(1) ++
    new chipyard.config.AbstractConfig
)