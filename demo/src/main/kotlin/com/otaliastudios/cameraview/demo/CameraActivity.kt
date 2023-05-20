package com.otaliastudios.cameraview.demo

//import com.otaliastudios.cameraview.filter.Filters

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.utils.ImageUtils
import com.example.utils.PathUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.liushengqi.utils.ActivityManager
import com.liushengqi.utils.FileUtils
//import com.luck.picture.lib.basic.PictureSelector
//import com.luck.picture.lib.config.SelectMimeType
//import com.luck.picture.lib.entity.LocalMedia
//import com.luck.picture.lib.interfaces.OnResultCallbackListener
import com.otaliastudios.cameraview.*
import com.otaliastudios.cameraview.controls.Facing
import com.otaliastudios.cameraview.controls.Mode
import com.otaliastudios.cameraview.controls.Preview
import com.otaliastudios.cameraview.filter.Filters
import com.otaliastudios.cameraview.frame.Frame
import com.otaliastudios.cameraview.frame.FrameProcessor
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import java.io.ByteArrayOutputStream
import java.util.*



class CameraActivity : AppCompatActivity(), View.OnClickListener, OptionView.Callback {

    companion object {
        private val LOG = CameraLogger.create("DemoApp")
        private const val USE_FRAME_PROCESSOR = false
        private const val DECODE_BITMAP = false
        public lateinit var module:Module
        public var mode=0//0表示不使用增亮
        public var SAVE_IMG=false
    }



    /*相机预览界面*/
    private val camera: CameraView by lazy { findViewById(R.id.camera) }



    /*
    * 拍摄选项界面
    * 默认情况下该界面隐藏在相机的最底端
    * */
    private val controlPanel: ViewGroup by lazy { findViewById(R.id.controls) }


    private var captureTime: Long = 0
    private var currentFilter = 0
    private val allFilters = Filters.values()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        CameraLogger.setLogLevel(CameraLogger.LEVEL_VERBOSE)
        //使用android.util.Log实现,方便了打印日志信息
        var path = PathUtils.assetFilePath(this,"model.pt")
        module = LiteModuleLoader.load(path)

        ActivityManager.setCurrentActivity(this);
        camera.setLifecycleOwner(this)
        //进行此设置之后，this的生命周期函数会同步调用camera的声明周期函数。假如当前Activity(即CameraActivity)执行onStop(),则会同步执行camera.onStop().
        camera.playSounds=false

        camera.addCameraListener(Listener())
        //添加一系列的监听器，监听事件包括：拍照、相机打开、相机出错等等

        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
                // 在此处设置当前的Activity
                ActivityManager.setCurrentActivity(activity)
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }
        })

        /*是否使用帧处理器
        不过不太懂帧处理器是在干什么
        */

        if (USE_FRAME_PROCESSOR) {
            camera.addFrameProcessor(object : FrameProcessor {
                private var lastTime = System.currentTimeMillis()
                override fun process(frame: Frame) {
                    val newTime = frame.time
                    val delay = newTime - lastTime
                    lastTime = newTime
                    LOG.v("Frame delayMillis:", delay, "FPS:", 1000 / delay)
                    if (DECODE_BITMAP) {
                        if (frame.format == ImageFormat.NV21
                                && frame.dataClass == ByteArray::class.java) {
                            val data = frame.getData<ByteArray>()
                            val yuvImage = YuvImage(data,
                                    frame.format,
                                    frame.size.width,
                                    frame.size.height,
                                    null)
                            val jpegStream = ByteArrayOutputStream()
                            yuvImage.compressToJpeg(Rect(0, 0,
                                    frame.size.width,
                                    frame.size.height), 100, jpegStream)
                            val jpegByteArray = jpegStream.toByteArray()
                            val bitmap = BitmapFactory.decodeByteArray(jpegByteArray,
                                    0, jpegByteArray.size)
                            bitmap.toString()
                        }
                    }
                }
            })
        }


        //各种组件会监听当前活动中的事件
        findViewById<View>(R.id.edit).setOnClickListener(this)
        findViewById<View>(R.id.capturePicture).setOnClickListener(this)
        findViewById<View>(R.id.capturePictureSnapshot).setOnClickListener(this)
//        findViewById<View>(R.id.captureVideo).setOnClickListener(this)
//        findViewById<View>(R.id.captureVideoSnapshot).setOnClickListener(this)
        findViewById<View>(R.id.toggleCamera).setOnClickListener(this)
        findViewById<View>(R.id.changeFilter).setOnClickListener(this)
        findViewById<View>(R.id.galary).setOnClickListener(this)

        //group应该是UI模块的容器（可能是一个Linear_layout），在其中可以添加各种UI组件
        //向group中就可以添加所有的选项按钮
        val group = controlPanel.getChildAt(0) as ViewGroup


        val watermark = findViewById<View>(R.id.watermark)




        //选项菜单，在相机界面点击选项会弹出一个菜单界面
        //该菜单中列出图像的各种设置，比如宽、高..
        val options: List<Option<*>> = listOf(
                // Layout
                Option.Width(), Option.Height(),
                // Engine and preview
                Option.Mode(), Option.Engine(), Option.Preview(),
                // Some controls
                Option.Flash(), Option.WhiteBalance(), Option.Hdr(),
                Option.PictureMetering(), Option.PictureSnapshotMetering(),
                Option.PictureFormat(),
                // Video recording
                Option.PreviewFrameRate(), Option.VideoCodec(), Option.Audio(), Option.AudioCodec(),
                // Gestures
                Option.Pinch(), Option.HorizontalScroll(), Option.VerticalScroll(),
                Option.Tap(), Option.LongTap(),
                // Watermarks
                Option.OverlayInPreview(watermark),
                Option.OverlayInPictureSnapshot(watermark),
                Option.OverlayInVideoSnapshot(watermark),
                // Frame Processing
                Option.FrameProcessingFormat(),
                // Other
                Option.Grid(), Option.GridColor(), Option.UseDeviceOrientation()
        )

        // 选项列表的分界线（UI）
        //true表示该选项后面有一个横向
        val dividers = listOf(
                // Layout
                false, true,
                // Engine and preview
                false, false, true,
                // Some controls
                false, false, false, false, false, true,
                // Video recording
                false, false, false, true,
                // Gestures
                false, false, false, false, true,
                // Watermarks
                false, false, true,
                // Frame Processing
                true,
                // Other
                false, false, true
        )
        for (i in options.indices) {
            val view = OptionView<Any>(this)
            view.setOption(options[i] as Option<Any>, this)
            view.setHasDivider(dividers[i])
            group.addView(view, MATCH_PARENT, WRAP_CONTENT)
        }
        controlPanel.viewTreeObserver.addOnGlobalLayoutListener {
            BottomSheetBehavior.from(controlPanel).state = BottomSheetBehavior.STATE_HIDDEN
        }

        // Animate the watermark just to show we record the animation in video snapshots
        val animator = ValueAnimator.ofFloat(1f, 0.8f)
        animator.duration = 300
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            watermark.scaleX = scale
            watermark.scaleY = scale
            watermark.rotation = watermark.rotation + 2
        }
        animator.start()
    }


    private fun message(content: String, important: Boolean) {
        if (important) {
            LOG.w(content)
            Toast.makeText(this, content, Toast.LENGTH_LONG).show()
        } else {
            LOG.i(content)
            Toast.makeText(this, content, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class Listener : CameraListener() {
        override fun onCameraOpened(options: CameraOptions) {
            val group = controlPanel.getChildAt(0) as ViewGroup
            for (i in 0 until group.childCount) {
                val view = group.getChildAt(i) as OptionView<*>
                view.onCameraOpened(camera, options)
            }
        }

        override fun onCameraError(exception: CameraException) {
            super.onCameraError(exception)
            message("Got CameraException #" + exception.reason, true)
        }

        @RequiresApi(Build.VERSION_CODES.N)
        override fun onPictureTaken(result: PictureResult) {
            super.onPictureTaken(result)
            if (camera.isTakingVideo) {
                message("Captured while taking video. Size=" + result.size, false)
                return
            }
            if(SAVE_IMG==true){
                val context = ActivityManager.getCurrentActivity()
                if (context != null) {
                    result.toBitmap {
                            t->
                        var bitmap = t?.let { ImageUtils.setBitmapSize(it,500,500) }
                        Log.d("saveInfo","1"+bitmap.toString())

                        if (context != null && bitmap!=null) {
                            FileUtils.saveImages(context, bitmap!!)
                            Log.d("saveInfo","okk")
                        }
                    };


                }

                return
            }


            // This can happen if picture was taken with a gesture.
            val callbackTime = System.currentTimeMillis()
            if (captureTime == 0L) captureTime = callbackTime - 300
            LOG.w("onPictureTaken called! Launching activity. Delay:", callbackTime - captureTime)
            PicturePreviewActivity.pictureResult = result
            val intent = Intent(this@CameraActivity, PicturePreviewActivity::class.java)
            intent.putExtra("delay", callbackTime - captureTime)
            startActivity(intent)
            captureTime = 0
            LOG.w("onPictureTaken called! Launched activity.")
        }

        override fun onVideoTaken(result: VideoResult) {
            super.onVideoTaken(result)
            LOG.w("onVideoTaken called! Launching activity.")
            VideoPreviewActivity.videoResult = result
            val intent = Intent(this@CameraActivity, VideoPreviewActivity::class.java)
            startActivity(intent)
            LOG.w("onVideoTaken called! Launched activity.")
        }

        override fun onVideoRecordingStart() {
            super.onVideoRecordingStart()
            LOG.w("onVideoRecordingStart!")
        }

        override fun onVideoRecordingEnd() {
            super.onVideoRecordingEnd()
            message("Video taken. Processing...", false)
            LOG.w("onVideoRecordingEnd!")
        }

        override fun onExposureCorrectionChanged(newValue: Float, bounds: FloatArray, fingers: Array<PointF>?) {
            super.onExposureCorrectionChanged(newValue, bounds, fingers)
            message("Exposure correction:$newValue", false)
        }

        override fun onZoomChanged(newValue: Float, bounds: FloatArray, fingers: Array<PointF>?) {
            super.onZoomChanged(newValue, bounds, fingers)
            message("Zoom:$newValue", false)
        }
    }
    fun getPictureFromAlbum(){

//        PictureSelector.create(this)
//            .openGallery(SelectMimeType.ofImage())
//            .setImageEngine(GlideEngine.createGlideEngine())
//            .forResult(object : OnResultCallbackListener<LocalMedia?> {
//                override fun onResult(result: ArrayList<LocalMedia?>?) {}
//                override fun onCancel() {}
//            })
    }
    override fun onClick(view: View) {
        when (view.id) {
            R.id.edit -> edit()
            R.id.capturePicture -> capturePicture()
            R.id.capturePictureSnapshot ->fastSavePicture()
//            R.id.captureVideo -> captureVideo()
//            R.id.captureVideoSnapshot -> captureVideoSnapshot()
            R.id.toggleCamera -> toggleCamera()
            R.id.changeFilter -> changeCurrentFilter()
        }
    }


    override fun onBackPressed() {
        val b = BottomSheetBehavior.from(controlPanel)
        if (b.state != BottomSheetBehavior.STATE_HIDDEN) {
            b.state = BottomSheetBehavior.STATE_HIDDEN
            return
        }
        super.onBackPressed()
    }

    private fun edit() {
        BottomSheetBehavior.from(controlPanel).state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun capturePicture() {
        SAVE_IMG = false
        if (camera.mode == Mode.VIDEO) return run {
            message("Can't take HQ pictures while in VIDEO mode.", false)
        }
        if (camera.isTakingPicture) return
        captureTime = System.currentTimeMillis()
        message("拍摄中...", false)
        camera.takePicture()
    }


    private fun fastSavePicture(){
        SAVE_IMG = true
        if (camera.mode == Mode.VIDEO) return run {
            message("Can't take HQ pictures while in VIDEO mode.", false)
        }
        if (camera.isTakingPicture) return
        captureTime = System.currentTimeMillis()
        message("保存中...", false)
        camera.takePicture()
    }
//    private fun capturePictureSnapshot() {



//        if (camera.isTakingPicture) return
//        if (camera.preview != Preview.GL_SURFACE) return run {
//            message("Picture snapshots are only allowed with the GL_SURFACE preview.", true)
//        }
//        captureTime = System.currentTimeMillis()
//        message("正在保存...", false)
//        camera.takePictureSnapshot()
//    }

//    private fun captureVideo() {
//        if (camera.mode == Mode.PICTURE) return run {
//            message("Can't record HQ videos while in PICTURE mode.", false)
//        }
//        if (camera.isTakingPicture || camera.isTakingVideo) return
//        message("Recording for 5 seconds...", true)
//        camera.takeVideo(File(filesDir, "video.mp4"), 5000)
//    }

//    private fun captureVideoSnapshot() {
//        if (camera.isTakingVideo) return run {
//            message("Already taking video.", false)
//        }
//        if (camera.preview != Preview.GL_SURFACE) return run {
//            message("Video snapshots are only allowed with the GL_SURFACE preview.", true)
//        }
//        message("Recording snapshot for 5 seconds...", true)
//        camera.takeVideoSnapshot(File(filesDir, "video.mp4"), 5000)
//    }

    private fun toggleCamera() {
        if (camera.isTakingPicture || camera.isTakingVideo) return
        when (camera.toggleFacing()) {
            Facing.BACK -> message("Switched to back camera!", false)
            Facing.FRONT -> message("Switched to front camera!", false)
        }
    }
    private fun changeMode(){


    }
    private fun changeCurrentFilter() {
        if (camera.preview != Preview.GL_SURFACE) return run {
            message("Filters are supported only when preview is Preview.GL_SURFACE.", true)
        }
        if (currentFilter < allFilters.size - 1) {
            currentFilter++
        } else {
            currentFilter = 0
        }
        val filter = allFilters[currentFilter]
        message(filter.toString(), false)

        // Normal behavior:
        camera.filter = filter.newInstance()
//以下为自己添加的内容
        mode = 1-mode



        // To test MultiFilter:
        // DuotoneFilter duotone = new DuotoneFilter();
        // duotone.setFirstColor(Color.RED);
        // duotone.setSecondColor(Color.GREEN);
        // camera.setFilter(new MultiFilter(duotone, filter.newInstance()));
    }

    override fun <T : Any> onValueChanged(option: Option<T>, value: T, name: String): Boolean {
        if (option is Option.Width || option is Option.Height) {
            val preview = camera.preview
            val wrapContent = value as Int == WRAP_CONTENT
            if (preview == Preview.SURFACE && !wrapContent) {
                message("The SurfaceView preview does not support width or height changes. " +
                        "The view will act as WRAP_CONTENT by default.", true)
                return false
            }
        }
        option.set(camera, value)
        BottomSheetBehavior.from(controlPanel).state = BottomSheetBehavior.STATE_HIDDEN
        message("Changed " + option.name + " to " + name, false)
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val valid = grantResults.all { it == PERMISSION_GRANTED }
        if (valid && !camera.isOpened) {
            camera.open()
        }
    }

}
