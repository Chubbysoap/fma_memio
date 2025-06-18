package fma

import chisel3._

class FMAModule extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(32.W))
    val b = Input(UInt(32.W))
    val c = Input(UInt(32.W))
    val result = Output(UInt(32.W))
  })

  io.result := (io.a * io.b) + io.c
}