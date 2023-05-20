package com.otaliastudios.cameraview.demo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.utils.ImageUtils
import com.example.utils.PathUtils
import com.otaliastudios.cameraview.CameraUtils
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.controls.PictureFormat
import com.otaliastudios.cameraview.size.AspectRatio
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import java.io.File

class PicturePreviewActivity : AppCompatActivity() {

    companion object {
        var pictureResult: PictureResult? = null
    }
    var imageRatio = 0.0f

    lateinit var inputArray: FloatArray
    lateinit var outputArray: FloatArray
    var imageHeight = 0
    var imageWidth = 0
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picture_preview)
        val result = pictureResult ?: run {
            finish()
            return
        }
        val imageView = findViewById<ImageView>(R.id.image)
        val captureResolution = findViewById<MessageView>(R.id.nativeCaptureResolution)
        val captureLatency = findViewById<MessageView>(R.id.captureLatency)
        val exifRotation = findViewById<MessageView>(R.id.exifRotation)
        val seekBar = findViewById<SeekBar>(R.id.seek_bar)
        val delay = intent.getLongExtra("delay", 0)
        val ratio = AspectRatio.of(result.size)
        captureLatency.setTitleAndMessage("Approx. latency", "$delay milliseconds")
        captureResolution.setTitleAndMessage("Resolution", "${result.size} ($ratio)")
        exifRotation.setTitleAndMessage("EXIF rotation", result.rotation.toString())
        if(CameraActivity.mode==1) {
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    imageRatio = progress.toFloat() / 255
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {

                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    if (CameraActivity.mode == 0)
                        return
                    val bitmap = ImageUtils.floatArray2Bitmap(
                        ImageUtils.mixImage(
                            inputArray,
                            outputArray,
                            imageRatio
                        ), imageWidth, imageHeight
                    )
                    imageView.setImageBitmap(bitmap)
                }
            })
        }else{
            seekBar.visibility= View.GONE
        }
        try {
            result.toBitmap(1000, 1000) { bitmap ->
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    loadResultImage(bitmap)
                }
            }
        } catch (e: UnsupportedOperationException) {
            imageView.setImageDrawable(ColorDrawable(Color.GREEN))
            Toast.makeText(this, "Can't preview this format: " + result.format, Toast.LENGTH_LONG).show()
        }
        if (result.isSnapshot) {
            // Log the real size for debugging reason.
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(result.data, 0, result.data.size, options)
            if (result.rotation % 180 != 0) {
                Log.e("PicturePreview", "The picture full size is ${result.size.height}x${result.size.width}")
            } else {
                Log.e("PicturePreview", "The picture full size is ${result.size.width}x${result.size.height}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            pictureResult = null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.share, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.share) {
            Toast.makeText(this, "Sharing...", Toast.LENGTH_SHORT).show()
            val extension = when (requireNotNull(pictureResult).format) {
                PictureFormat.JPEG -> "jpg"
                PictureFormat.DNG -> "dng"
                else -> throw RuntimeException("Unknown format.")
            }
            val destFile = File(filesDir, "picture.$extension")
            CameraUtils.writeToFile(requireNotNull(pictureResult?.data), destFile) { file ->
                if (file != null) {
                    val context = this@PicturePreviewActivity
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "image/*"
                    val uri = FileProvider.getUriForFile(context,
                            context.packageName + ".provider", file)
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(intent)
                } else {
                    Toast.makeText(this@PicturePreviewActivity,
                            "Error while writing file.",
                            Toast.LENGTH_SHORT).show()
                }
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun loadResultImage(inputBitmap:Bitmap){
        val bitmap2 = ImageUtils.setBitmapSize(inputBitmap,1000,1000)
        if(CameraActivity.mode==0){
            return
        }
        imageHeight = bitmap2.height
        imageWidth = bitmap2.width
        val inputTensor = ImageUtils.bitmap2RGBData(bitmap2)
        val outputTensor = CameraActivity.module.forward(IValue.from(inputTensor)).toTuple()[1].toTensor()
        inputArray = inputTensor.dataAsFloatArray
        outputArray = outputTensor.dataAsFloatArray
    }
}