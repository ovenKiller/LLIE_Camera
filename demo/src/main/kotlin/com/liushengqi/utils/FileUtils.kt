package com.liushengqi.utils

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils.isEmpty
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.utils.ImageUtils
import org.jetbrains.annotations.NotNull
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


var FILE_SEQ =0//生成文件的序列，防止文件重名
object FileUtils {
    //保存
    fun saveImages(@NotNull activity: Activity, @NotNull bitmap: Bitmap) {
        val permission: String = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            //检查权限
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    permission
                ) !== PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(activity, arrayOf(permission), 10086)
                return
            }
        }
        try {
            saveMedia(
                activity,
                bitmap,
                Environment.DIRECTORY_PICTURES,
                "enlightenCamera",
                generateFileName()+".png",
                "image/png",
                ""
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    fun generateFileName(): String {
        val timesdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        //SimpleDateFormat filesdf = new SimpleDateFormat("yyyy-MM-dd HHmmss"); //文件名不能有：
        val FileTime =timesdf.format(Date()).toString();//获取系统时间
        val filename = FileTime.replace(":", "");
        FILE_SEQ = (FILE_SEQ +1)/100
        return filename+ FILE_SEQ
    }


    //type:Environment.DIRECTORY_PICTURES
    @Throws(IOException::class)
    private fun saveMedia(
        context: Context,
        bitmap: Bitmap,
        dirType: String,
        relativeDir: String,
        filename: String,
        mimeType: String,
        description: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            //首先保存
            var saveDir: File = Environment.getExternalStoragePublicDirectory(dirType)
            saveDir = File(saveDir, relativeDir)
            if (!saveDir.exists() && !saveDir.mkdirs()) {
                try {
                    throw Exception("create directory fail!")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            Log.d("SMG", saveDir.getAbsolutePath())
            val outputFile = File(saveDir, filename)
            var fos: FileOutputStream? = null
            try {
                fos = FileOutputStream(outputFile)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
            val bos = BufferedOutputStream(fos)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.flush()
            bos.close()
            //把文件插入到系统图库(直接插入到Picture文件夹下)
//        MediaStore.Images.Media.insertImage(
//            context.contentResolver, outputFile.absolutePath, outputFile.name, ""
//        )
            //最后通知图库更新
            context.sendBroadcast(
                Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(outputFile)
                )
            )
        } else {
            val path =
                if (!isEmpty(relativeDir)) Environment.DIRECTORY_PICTURES + File.separator.toString() + relativeDir else Environment.DIRECTORY_PICTURES
            val contentValues = ContentValues()
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            contentValues.put(MediaStore.Images.Media.DESCRIPTION, description)
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, path)
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            //contentValues.put(MediaStore.Images.Media.IS_PENDING,1)
            val external: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val insertUri: Uri? = context.getContentResolver().insert(external, contentValues)
            var fos: OutputStream? = null as OutputStream?
            if (insertUri != null) {
                try {
                    fos = context.getContentResolver().openOutputStream(insertUri)
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
            }
            if (fos != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
                fos.close()
            }
        }
    }


    /**
     * Android Q以下版本，删除文件需要申请WRITE_EXTERNAL_STORAGE权限。通过MediaStore的DATA字段获得媒体文件的绝对路径，然后使用File相关API删除
     *
     *
     * Android Q以上版本，应用删除自己创建的媒体文件不需要用户授权。删除其他应用创建的媒体文件需要申请READ_EXTERNAL_STORAGE权限。
     * 删除其他应用创建的媒体文件，还会抛出RecoverableSecurityException异常，在操作或删除公共目录的文件时，需要Catch该异常，由MediaProvider弹出弹框给用户选择是否允许应用修改或删除图片/视频/音频文件
     */
    fun deletePicture(@NotNull activity: Activity, @NotNull imageUri: Uri?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor: Cursor? = imageUri?.let {
                activity.contentResolver.query(
                    it, projection,
                    null, null, null
                )
            }
            if (cursor != null) {
                val columnIndex: Int = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                if (columnIndex > -1) {
                    val file = File(cursor.getString(columnIndex))
                    file.delete()
                }
            }
            if (cursor != null) {
                cursor.close()
            }
        } else {
            try {
                if (imageUri != null) {
                    activity.contentResolver.delete(imageUri, null, null)
                }
            } catch (e1: RecoverableSecurityException) {
                //捕获 RecoverableSecurityException异常，发起请求
                try {
                    ActivityCompat.startIntentSenderForResult(
                        activity, e1.userAction.actionIntent.intentSender,
                        10086, null, 0, 0, 0, null
                    )
                } catch (e: SendIntentException) {
                    e.printStackTrace()
                }
            }
        }
    }

}