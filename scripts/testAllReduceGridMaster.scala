import BIDMach.allreduce._

import scala.concurrent.duration._

// Override the configuration of the port when specified as program argument
val port = "2551"
val nodeNum = 4;
val masterConfig = MasterConfig(nodeNum = nodeNum, discoveryTimeout = 5.seconds)

AllreduceGridMaster.startUp(port, masterConfig)