package BIDMach.viz;
import BIDMat.{BMat,Mat,SBMat,CMat,DMat,FMat,FFilter,IMat,HMat,GDMat,GFilter,GLMat,GMat,GIMat,GSDMat,GSMat,LMat,SMat,SDMat,TMat}
import BIDMat.MatFunctions._
import BIDMat.SciFunctions._
import BIDMach.models.Model;
import BIDMach.networks.Net;
import BIDMach.networks.layers._;

/***
    Visualizing the filters within a ConvLayer
**/
class FilterViz(val layerId:Int, val bw:Int = 1, val name: String = "") extends Visualization{
    val _name = if (name.length > 0) name else "Conv@" + layerId
    val plot = new ImageArray(_name);
    var bestImg: Mat = null;
    var bestImgOri: Mat = null;
    var filter_scale = 1f        
    var _filter_scale: Mat = null;
    var modelAveragingTime = 0f;
    var _averagingWeight: Mat = null;
    var _averagingModelmats: Array[Mat] = null;
    val ind = irow(0)
    plot.add_slider("filter_scale",(x:Int)=>{filter_scale=x/5f;filter_scale},5,2);
    plot.add_slider("modelAveragingTime",(x:Int)=>{modelAveragingTime=math.exp(x/10).toFloat;modelAveragingTime},0,2);
        
    override def check(model:Model, mats:Array[Mat]) = {
        model match {
            case net:Net => {
                if (layerId < net.layers.length){
                    net.layers(layerId) match {
                        case _:ConvLayer => {
                            _averagingWeight = net.modelmats(0).zeros(1,1);
                            _averagingModelmats = new Array[Mat](net.modelmats.length);
                            for(i<-0 until net.modelmats.length) {
                                _averagingModelmats(i) = net.modelmats(i).zeros(net.modelmats(i).dims);
                                _averagingModelmats(i) <-- net.modelmats(i)
                            }
                            0
                        }
                        case _=>{
                            println("The %d layer of the network is not a ConvLayer" format layerId);
                            1                               
                        }
                    }
                }
                else {
                    println("The network only has %d layers while you're accessing %d" format (net.layers.length, layerId))
                    1
                }                    
            }
            case _=>{
                println("The model is not a Net");
                1
            } 
        }                
    }
    
    def reshapeNCHW(a:Mat) = {
        a.reshapeView(a.dims(1),a.dims(2),a.dims(0),a.dims(3))
    }
       
    def getBestActivation(layer: ConvLayer, tensorFormat: Int) = {
        val dims = layer.output.dims;
        val data = if (tensorFormat == Net.TensorNHWC) layer.output;
        else layer.output.asInstanceOf[FMat].fromNCHWtoNHWC;
        val (d,i) = maxi2(data.reshapeView(dims(0),dims(1)*dims(2)*dims(3)),2);
        val id = IMat(i);
        val w = irow(id.data.map(_%dims(1)));  
        val h = irow(id.data.map(_%(dims(1)*dims(2))/dims(1)));  
        val n = irow(id.data.map(_/(dims(1)*dims(2))));
        //val i0 = i.asInstanceOf[IMat]
        val ffilter = layer.ffilter;
//        val ww = i0+0;ww<--( -ffilter.pad(1) + (w+ffilter.outPad(1)) * ffilter.stride(1) );
        val ww = ( -ffilter.pad(1) + (w+ffilter.outPad(1)) * ffilter.stride(1) );
        max(ww, 0, ww)
        min(ww, layer.inputData.dims(1) - ffilter.inDims(1), ww)
        val hh = ( -ffilter.pad(2) + (h+ffilter.outPad(2)) * ffilter.stride(2) );
        max(hh, 0, hh)
        min(hh, layer.inputData.dims(2) - ffilter.inDims(2), hh)
        (d,ww,hh,n)
    }
    
    def getBestImg(net:Net) = {
        val layer = net.layers(layerId).asInstanceOf[ConvLayer];
        val (d,ww,hh,n) = getBestActivation(layer, net.opts.tensorFormat)
        val nFilters = layer.output.dims(0);
        val ffilter = layer.ffilter;
        val cropLayerId = net.layers.indexWhere(_.toString.startsWith("crop"))
        val src = reshapeNCHW(net.layers(0).output);           
        val idataOriginal = cpu(
            if (cropLayerId < 0 ) src
            else {       
                val bl = net.layers(cropLayerId) match {
                    case l:CropLayer=>l.blockInds;
                    case l:CropMirrorLayer=>l.blockInds
                }
                src(bl(1),bl(2),?,?)
            })
        val idata = cpu(reshapeNCHW(layer.inputData))
        val res = idata.zeros(irow(ffilter.inDims(1),ffilter.inDims(2),ffilter.inDims(0),nFilters));
        val resOri = idata.zeros(irow(ffilter.inDims(1),ffilter.inDims(2),ffilter.inDims(0),nFilters));
        for(k<-0 until nFilters){
            ind(0)=k
            res(?,?,?,ind) = idata(ww(k)->(ww(k)+ffilter.inDims(1)),hh(k)->(hh(k)+ffilter.inDims(2)),?,n(k))                 
            resOri(?,?,?,ind) = idataOriginal(ww(k)->(ww(k)+ffilter.inDims(1)),hh(k)->(hh(k)+ffilter.inDims(2)),?,n(k))    
        }
        (res.reshapeView(ffilter.inDims(0),ffilter.inDims(1),ffilter.inDims(2),nFilters),
        resOri.reshapeView(ffilter.inDims(0),ffilter.inDims(1),ffilter.inDims(2),nFilters))
    }
    
    def getBestImgBatch(net:Net) = {
        val (_,res) = getBestImg(net)
        res
        //utils.filter2img((res/256f-0.5f),net.opts.tensorFormat,bw);
    }
    
    def merge(a:Mat, b:Mat, comp:Mat) {
        val aa = a.reshapeView(a.nrows,a.ncols);
        val bb = b.reshapeView(b.nrows,b.ncols);
        aa~aa*@(1-comp);
        aa~aa +(bb*@comp)
    }
    
    def getBestImgAll(net: Net) = {
        val (res,resOri) = getBestImg(net);   
        if (bestImg == null){
            bestImg = res.zeros(res.dims);
            bestImgOri = resOri.zeros(resOri.dims)
        }
        val layer = net.layers(layerId).asInstanceOf[ConvLayer];
        val filter = cpu(net.modelmats(layer.imodel))
        val comp = (sum(bestImg*@filter)<=sum(res*@filter));
        merge(bestImg,res,comp);
        merge(bestImgOri,resOri,comp);
        bestImgOri
        //utils.filter2img((bestImgOri/256f-0.5f),net.opts.tensorFormat,bw)
    }
       
    override def doUpdate(model:Model, mats:Array[Mat], ipass:Int, pos:Long) = {
        val net = model.asInstanceOf[Net];
        val layer = net.layers(layerId).asInstanceOf[ConvLayer];
        for(i<-0 until model.modelmats.length){
            _averagingWeight(0,0) = 1-1f/modelAveragingTime
            _averagingModelmats(i) ~ _averagingModelmats(i) *@ _averagingWeight;
            _averagingModelmats(i) ~ _averagingModelmats(i) + (model.modelmats(i) *@ (1f - _averagingWeight))
        }
        if (_filter_scale == null) _filter_scale = net.modelmats(layer.imodel).zeros(1,1);
         _filter_scale(0,0) = filter_scale;            
//        val img = utils.filter2img(net.modelmats(layer.imodel)*@_filter_scale,net.opts.tensorFormat,bw);
        val img = FMat(cpu((_averagingModelmats(layer.imodel)*@_filter_scale+0.5f) * 256f))
        if (layer.imodel == 0){
//            val input = getBestImgBatch(net)
            val input = getBestImgAll(net)
            plot.plot_image(img \ input,net.opts.tensorFormat)
        }
        else
            plot.plot_image(img.reshapeView(img.dims(1),img.dims(2),img.dims(0),img.dims(3))(?,?,0->3,?).reshapeView(3,img.dims(1),img.dims(2),img.dims(3)),net.opts.tensorFormat)
    }    
}

