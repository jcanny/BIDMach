package BIDMach.networks.layers

import BIDMat.{Mat,SBMat,CMat,DMat,FMat,IMat,LMat,HMat,GMat,GDMat,GIMat,GLMat,GSMat,GSDMat,SMat,SDMat}
import BIDMat.MatFunctions._
import BIDMat.SciFunctions._
import BIDMach.datasources._
import BIDMach.updaters._
import BIDMach.mixins._
import BIDMach.models._
import BIDMach._
import edu.berkeley.bid.CPUMACH
import edu.berkeley.bid.CUMACH
import scala.util.hashing.MurmurHash3;
import java.util.HashMap;
import BIDMach.networks._


/**
 * Computes the element-wise sum of input layers. 
 */

@SerialVersionUID(100L)
class AddLayer(override val net:Net, override val opts:AddNodeOpts = new AddNode) extends Layer(net, opts) { 
  
  override val _inputs = new Array[LayerTerm](opts.ninputs);

	override def forward = {
      val start = toc;
      inplaceNoConnectGetOutput();
      
	  output ~ inputData + inputDatas(1);
	  (2 until inputlength).map((i:Int) => output ~ output + inputDatas(i));

	  forwardtime += toc - start;
	}

	override def backward = {
      val start = toc;
      inplaceNoConnectGetInputDerivs();
      
	  (0 until inputlength).map((i:Int) => {
		if (inputDerivs(i).asInstanceOf[AnyRef] != null) inputDerivs(i) ~ inputDerivs(i) + squash(deriv, inputDerivs(i));
	  });
	  
	  inplaceNoConnectReleaseDeriv()
	  backwardtime += toc - start;
	}
  
  override def toString = {
    "add@"+("%04x" format (hashCode % 0x10000));
  }
}

trait AddNodeOpts extends NodeOpts {
  var ninputs = 2;
  
  def copyOpts(opts:AddNodeOpts):AddNodeOpts = {
    super.copyOpts(opts);
    opts.ninputs = ninputs;
    opts;
  }
}

@SerialVersionUID(100L)
class AddNode extends Node with AddNodeOpts {
  override val inputs:Array[NodeTerm] = new Array[NodeTerm](ninputs);
  
  def copyTo(opts:AddNode):AddNode = {
    super.copyTo(opts);
    copyOpts(opts);
    opts;
  }


  override def clone:AddNode = {copyTo(new AddNode).asInstanceOf[AddNode];}

  override def create(net:Net):AddLayer = {AddLayer(net, this);}
  
  override def toString = {
   "add@"+("%04x" format (hashCode % 0x10000));
  }
}

@SerialVersionUID(100L)
object AddLayer { 
  
  def apply(net:Net) = new AddLayer(net, new AddNode);
  
  def apply(net:Net, opts:AddNodeOpts) = new AddLayer(net, opts); 
}
