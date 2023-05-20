package com.example.utils


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import com.otaliastudios.cameraview.CameraUtils
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.controls.PictureFormat
import com.otaliastudios.cameraview.demo.PicturePreviewActivity
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Float.min
import java.text.SimpleDateFormat
import java.util.*


object ImageUtils {
    const val SD_PATH = "/sdcard/lightencamera/"
    const val IN_PATH = "/lightencamera/"
      fun floatArray2Bitmap(floatArray: FloatArray,width:Int,height:Int):Bitmap{
        var bmp = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888);
        var pixels = IntArray(width * height * 4);
        for (i in 0 until width * height) {
            val r: Byte = float2byte(floatArray[i])
            val g: Byte = float2byte(floatArray[i + width * height])
            val b = float2byte(floatArray[i + 2 * width * height])
            pixels[i] = rgb(r, g, b)
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp

    }
    private fun rgb(r: Byte, g: Byte, b: Byte): Int {
        var res = -0x1000000
        res += b.toInt()
        res += g * 256
        res += r * 256 * 256
        return res
    }

//Todo 有可能没有获取到存取文件的权限
    fun saveAsPNG(context: Context, fileName:String, bitmap:Bitmap){
        val savePath: String
        var filePic: File
        savePath = if (Environment.getExternalStorageState() ==
            Environment.MEDIA_MOUNTED
        ) {
            SD_PATH
        } else {
            (context.getApplicationContext().getFilesDir()
                .getAbsolutePath()
                    + IN_PATH)
        }
        try {
            filePic = File(savePath + fileName + ".png")
            if (!filePic.exists()) {
                val rev = filePic.parentFile.mkdirs()
                    Log.d("fileMessage","创建文件夹情况:"+rev)
                    Log.d("fileMessage","创建文件情况:"+filePic.createNewFile())
            }
            val fos = FileOutputStream(filePic)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
        } catch (e: IOException) {
            Log.d("fileMessage","错误"+e)
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

    }
    fun saveToFile(context: Context, result: PictureResult){
        val extension = when (requireNotNull(result).format) {
            PictureFormat.JPEG -> "jpg"
            PictureFormat.DNG -> "dng"
            else -> throw RuntimeException("Unknown format.")
        }
        val destFile = File(context.getApplicationContext().getFilesDir(), "picture.$extension")
        val rev = CameraUtils.writeToFile(requireNotNull(result?.data), destFile)
        Log.d("saveFileResult",rev.toString())
    }


    @RequiresApi(Build.VERSION_CODES.N)
    fun setBitmapSize(bm:Bitmap, maxHeight:Int, maxWidth:Int):Bitmap{
        Log.d("saveInfo",bm.toString())
        val  k = min(maxHeight.toFloat()/bm.height,maxWidth.toFloat()/bm.width)
        val matrix =  Matrix()
        matrix.postScale(k,k)
        val newbm = Bitmap.createBitmap(bm,0,0,bm.width,bm.height,matrix,true)
        return newbm
    }
    fun tensor2Bitmap(tensor: Tensor): Bitmap {
        val data = tensor.dataAsFloatArray
        val width = tensor.shape()[2].toInt()
        val height = tensor.shape()[3].toInt()
        val pixels = IntArray(width * height)
        for (i in 0 until width * height) {
            val R = float2byte(data[i] * 255).toInt()
            val G = float2byte(data[i + width * height] * 255).toInt()
            val B = float2byte(data[i + 2 * width * height] * 255).toInt()
            pixels[i] = R shl 16 or (G shl 8) or B or -0x1000000
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
    private fun float2byte(v: Float): Byte {
        var intNum = (v * 255.0).toInt()
        if(intNum>255)intNum=255;
        if(intNum<0) intNum = 0;
        return intNum.toByte()
    }
    fun bitmap2RGBData(bitmap: Bitmap): Tensor {
        val width = bitmap.width
        val height = bitmap.height
        val intValues = IntArray(width * height)
        bitmap.getPixels(
            intValues, 0, width, 0, 0, width,
            height
        )
        val rgb = FloatArray(width * height * 3)
        for (i in intValues.indices) {
            val `val` = intValues[i]
            rgb[i] = (`val` shr 16 and 0xFF).toFloat() / 255 //R
            rgb[i + width * height] = (`val` shr 8 and 0xFF).toFloat() / 255 //G
            rgb[i + width * height * 2] = (`val` and 0xFF).toFloat() / 255 //B
            //            Log.d(""+rgb[i]+","+rgb[i+width*height]+","+rgb[i+width*height*2],"rgb "+i);
        }
        val shape = longArrayOf(1, 3, width.toLong(), height.toLong())
        return Tensor.fromBlob(rgb, shape)
    }
    fun mixImage(sourceImage: FloatArray, targetImage:FloatArray, ratio: Float):FloatArray{
        try{
            if(ratio<0||ratio>1){
                throw Exception()
            }
        }catch (e:java.lang.Exception){
            Log.e("numError","数值大小出错")
        }
        var result = FloatArray(sourceImage.size)
        for(i in sourceImage.indices){
            result[i] = sourceImage[i]*(1-ratio)
            result[i]+=targetImage[i]*ratio
        }
        return result
    }
}